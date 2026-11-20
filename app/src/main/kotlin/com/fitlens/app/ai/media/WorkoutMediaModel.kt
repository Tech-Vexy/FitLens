package com.fitlens.app.ai.media

import android.graphics.Bitmap

/**
 * Step in an exercise procedure generated using Nanobanana aesthetic.
 */
data class NanobananaProcedureStep(
    val stepNumber: Int,
    val title: String,
    val instruction: String,
    val breathingCue: String,
    val biomechanicsCue: String,
    val targetMuscles: List<String>,
    val progressFraction: Float,
    val keyJointAngles: String
)

/**
 * Stage/phase in the Gemini Omni video walkthrough.
 */
data class GeminiOmniWalkthroughPhase(
    val phaseIndex: Int,
    val title: String,
    val startSeconds: Float,
    val durationSeconds: Float,
    val cameraPerspective: String,
    val coachingAudioPrompt: String,
    val breathingDirective: String,
    val formInspectionCheck: String
)

/**
 * Video walkthrough synthesized using Gemini Omni.
 */
data class GeminiOmniVideoWalkthrough(
    val videoTitle: String,
    val totalDurationSeconds: Int = 12,
    val videoPrompt: String,
    val phases: List<GeminiOmniWalkthroughPhase>,
    val audioNarrationScript: String,
    val coverImage: Bitmap? = null
)

/**
 * Describes whether procedure media came from the local synthesis fallback or
 * Firebase AI Logic cloud generation.
 */
enum class WorkoutMediaSource {
    LOCAL_SYNTHESIS,
    NANOBANANA_IMAGE,
    GEMINI_OMNI_WALKTHROUGH,
    HYBRID_FIREBASE_AI_LOGIC
}

/**
 * Bundled media artifacts generated during workout generation for an exercise.
 */
data class ExerciseMediaPack(
    val exerciseName: String,
    val categoryName: String,
    val nanobananaPrompt: String,
    val procedureSteps: List<NanobananaProcedureStep>,
    val videoWalkthrough: GeminiOmniVideoWalkthrough,
    val nanobananaImages: Map<Int, Bitmap> = emptyMap(),
    val mediaSource: WorkoutMediaSource = WorkoutMediaSource.LOCAL_SYNTHESIS,
    val generationNotes: List<String> = emptyList()
)
