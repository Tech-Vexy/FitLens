package com.fitlens.app.ai.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.fitlens.app.ai.firebase.FirebaseAiService
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.imageConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Cloud media pipeline layered on top of [WorkoutMediaGenerator].
 *
 * Firebase AI Logic currently exposes Nano Banana image generation and Gemini
 * text generation, but not client-side video-file generation. This service
 * therefore generates the actual procedure/cover image with Nano Banana and
 * synthesizes the Gemini Omni walkthrough script/phases with Gemini Flash;
 * the app renders that script as the interactive walkthrough player.
 */
class WorkoutMediaCloudGenerator(
    private val imageGenerator: NanoBananaImageGenerator = NanoBananaImageGenerator(),
    private val walkthroughGenerator: GeminiOmniWalkthroughGenerator = GeminiOmniWalkthroughGenerator()
) {
    companion object {
        private const val TAG = "WorkoutMediaCloud"
        private const val ROUTINE_CHUNK_SIZE = 2
    }

    suspend fun enrichPack(
        pack: ExerciseMediaPack,
        allowCloudMedia: Boolean = true
    ): ExerciseMediaPack {
        if (!allowCloudMedia) return pack

        val needsImage = pack.nanobananaImages.isEmpty()
        val needsWalkthrough = pack.mediaSource != WorkoutMediaSource.HYBRID_FIREBASE_AI_LOGIC &&
            pack.mediaSource != WorkoutMediaSource.GEMINI_OMNI_WALKTHROUGH
        val existingImage = pack.nanobananaImages.values.firstOrNull()

        return coroutineScope {
            val imageDeferred = if (needsImage) {
                async {
                    runCatching { imageGenerator.generate(buildImagePrompt(pack)) }
                        .onFailure { Log.w(TAG, "Nano Banana image failed for ${pack.exerciseName}: ${it.message}") }
                        .getOrNull()
                }
            } else {
                null
            }
            val walkthroughDeferred = if (needsWalkthrough) {
                async {
                    runCatching { walkthroughGenerator.generate(pack) }
                        .onFailure { Log.w(TAG, "Gemini walkthrough synthesis failed for ${pack.exerciseName}: ${it.message}") }
                        .getOrNull()
                }
            } else {
                null
            }

            val generatedImage = imageDeferred?.await()
            val generatedWalkthrough = walkthroughDeferred?.await()
            val image = generatedImage ?: existingImage
            val notes = mutableListOf<String>()

            when {
                !needsImage -> notes += "Reused Nano Banana procedure image."
                generatedImage != null -> notes += "Nano Banana procedure image generated."
                else -> notes += "Nano Banana image unavailable; using local procedure visual."
            }
            when {
                !needsWalkthrough -> notes += "Reused Gemini Omni walkthrough script."
                generatedWalkthrough != null -> notes += "Gemini Omni walkthrough script generated."
                else -> notes += "Gemini walkthrough synthesis unavailable; using local phase plan."
            }

            val hasCloudImage = image != null
            val hasCloudWalkthrough = generatedWalkthrough != null || !needsWalkthrough
            val enrichedWalkthrough = (generatedWalkthrough ?: pack.videoWalkthrough).copy(
                coverImage = image ?: pack.videoWalkthrough.coverImage
            )
            val source = when {
                hasCloudImage && hasCloudWalkthrough -> WorkoutMediaSource.HYBRID_FIREBASE_AI_LOGIC
                hasCloudImage -> WorkoutMediaSource.NANOBANANA_IMAGE
                hasCloudWalkthrough -> WorkoutMediaSource.GEMINI_OMNI_WALKTHROUGH
                else -> WorkoutMediaSource.LOCAL_SYNTHESIS
            }

            pack.copy(
                videoWalkthrough = enrichedWalkthrough,
                nanobananaImages = if (image != null) {
                    pack.procedureSteps.associate { it.stepNumber to image }
                } else {
                    emptyMap()
                },
                mediaSource = source,
                generationNotes = notes
            )
        }
    }

    suspend fun enrichRoutine(
        packs: Map<String, ExerciseMediaPack>,
        allowCloudMedia: Boolean = true
    ): Map<String, ExerciseMediaPack> {
        if (!allowCloudMedia || packs.isEmpty()) return packs

        val enriched = mutableListOf<Pair<String, ExerciseMediaPack>>()
        packs.entries.chunked(ROUTINE_CHUNK_SIZE).forEach { chunk ->
            val chunkResults = coroutineScope {
                chunk.map { (exerciseName, pack) ->
                    async { exerciseName to enrichPack(pack, allowCloudMedia) }
                }.awaitAll()
            }
            enriched += chunkResults
        }
        return enriched.toMap()
    }

    private fun buildImagePrompt(pack: ExerciseMediaPack): String {
        val stepSummary = pack.procedureSteps.joinToString(" ") { step ->
            "Step ${step.stepNumber}: ${step.title} — ${step.instruction.take(120)}"
        }.take(900)
        return buildString {
            append(pack.nanobananaPrompt)
            append(" Generate one polished 16:9 procedure sheet for ")
            append(pack.exerciseName)
            append(" showing a clear numbered ")
            append(pack.procedureSteps.size.coerceAtLeast(3))
            append("-step sequence. Athletic educational fitness visual, consistent character, "
                "clean studio lighting, precise joint alignment, subtle biomechanical highlights. ")
            append(stepSummary)
            append(" No text overlays, no watermark, no logos, safe-for-work content.")
        }
    }
}

/**
 * Nano Banana image generation through Firebase AI Logic.
 */
class NanoBananaImageGenerator(
    private val modelName: String = DEFAULT_MODEL_NAME
) {
    companion object {
        const val DEFAULT_MODEL_NAME = "gemini-nano-banana-2.1"
    }

    private val model by lazy { createModel() }

    suspend fun generate(prompt: String): Bitmap? {
        require(prompt.isNotBlank()) { "Image prompt must not be blank" }
        val response = model.generateContent(prompt)
        return extractBitmap(response)
    }

    private fun createModel(): GenerativeModel = Firebase
        .ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = imageConfig {
                    aspectRatio = AspectRatio.LANDSCAPE_16x9
                    imageSize = ImageSize.SIZE_1K
                }
            }
        )

    private fun extractBitmap(response: GenerateContentResponse): Bitmap? {
        response.candidates.firstOrNull()
            ?.content
            ?.parts
            ?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
            ?.image
            ?.let { return it }

        val bytes = response.inlineDataParts.firstOrNull()?.inlineData ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

/**
 * Gemini text model used to synthesize the Gemini Omni-style walkthrough plan.
 * Firebase AI Logic does not expose client-side Omni/Veo video-file output, so
 * this produces the structured script that the walkthrough player executes.
 */
class GeminiOmniWalkthroughGenerator(
    private val coachService: FirebaseAiService = FirebaseAiService()
) {
    suspend fun generate(pack: ExerciseMediaPack): GeminiOmniVideoWalkthrough? {
        val prompt = buildWalkthroughPrompt(pack)
        val raw = coachService.generateText(prompt)
        val jsonText = raw
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        return parseWalkthrough(json, pack)
    }

    private fun buildWalkthroughPrompt(pack: ExerciseMediaPack): String {
        val steps = pack.procedureSteps.joinToString("\n") { step ->
            "${step.stepNumber}. ${step.title}: ${step.instruction} " +
                "(${step.breathingCue}; ${step.keyJointAngles})"
        }
        return """
            You are a Gemini Omni workout-video director. Return ONLY a valid JSON object with no markdown.
            Create a concise ${pack.videoWalkthrough.totalDurationSeconds}-second vertical workout walkthrough
            for "${pack.exerciseName}" in a ${pack.categoryName} routine.
            Use exactly 4 phases covering setup, descent/contraction, hold/peak, and reset/next rep.
            Each phase needs: phaseIndex, title, startSeconds, durationSeconds, cameraPerspective,
            coachingAudioPrompt, breathingDirective, formInspectionCheck.
            JSON shape:
            {"videoTitle":"...","durationSeconds":12,"videoPrompt":"...","audioNarrationScript":"...","phases":[{"phaseIndex":1,"title":"...","startSeconds":0,"durationSeconds":3,"cameraPerspective":"...","coachingAudioPrompt":"...","breathingDirective":"...","formInspectionCheck":"..."}]}
            Procedure steps:
            $steps
        """.trimIndent()
    }

    private fun parseWalkthrough(
        json: JSONObject,
        fallback: ExerciseMediaPack
    ): GeminiOmniVideoWalkthrough? {
        val phaseArray = json.optJSONArray("phases") ?: return null
        val phases = (0 until phaseArray.length()).mapNotNull { index ->
            val phaseJson = phaseArray.optJSONObject(index) ?: return@mapNotNull null
            GeminiOmniWalkthroughPhase(
                phaseIndex = phaseJson.optInt("phaseIndex", index + 1),
                title = phaseJson.optString("title", "Phase ${index + 1}"),
                startSeconds = phaseJson.optDouble("startSeconds", index * 3.0).toFloat(),
                durationSeconds = phaseJson.optDouble("durationSeconds", 3.0).toFloat().coerceAtLeast(1f),
                cameraPerspective = phaseJson.optString(
                    "cameraPerspective",
                    phaseJson.optString("cameraAngle", "Dynamic Camera")
                ),
                coachingAudioPrompt = phaseJson.optString("coachingAudioPrompt", "Maintain control."),
                breathingDirective = phaseJson.optString("breathingDirective", "Breathe steadily."),
                formInspectionCheck = phaseJson.optString("formInspectionCheck", "Form aligned.")
            )
        }
        if (phases.size < 3) return null

        return GeminiOmniVideoWalkthrough(
            videoTitle = json.optString("videoTitle", "Gemini Omni Walkthrough: ${fallback.exerciseName}"),
            totalDurationSeconds = json.optInt(
                "durationSeconds",
                fallback.videoWalkthrough.totalDurationSeconds
            ).coerceIn(6, 30),
            videoPrompt = json.optString("videoPrompt", fallback.videoWalkthrough.videoPrompt),
            phases = phases.sortedBy { it.phaseIndex },
            audioNarrationScript = json.optString(
                "audioNarrationScript",
                fallback.videoWalkthrough.audioNarrationScript
            )
        )
    }
}
