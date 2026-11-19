package com.fitlens.app.ai.live

import android.annotation.SuppressLint
import android.util.Log
import com.fitlens.app.ai.firebase.FirebaseAiService
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Transcription
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class LiveConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LISTENING,
    ERROR
}

data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val moodTag: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class LiveAssistantState(
    val connectionState: LiveConnectionState = LiveConnectionState.DISCONNECTED,
    val messages: List<AssistantMessage> = listOf(
        AssistantMessage(
            isUser = false,
            text = "Hi! I'm your personal AI trainer & companion. Tap Start Sensing and Talk Live or message me — I'll chat with you and tune into how you might be feeling from your expression, heart rate, and breathing."
        )
    ),
    val streamingUserText: String = "",
    val streamingAssistantText: String = "",
    val isSendingText: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Manages real-time bidirectional voice, video, and text conversations using the
 * Firebase AI Logic Gemini Live API ([LiveSession]).
 */
@OptIn(PublicPreviewAPI::class)
class GeminiLiveAssistant(
    private val textFallbackService: FirebaseAiService = FirebaseAiService()
) {
    companion object {
        private const val TAG = "GeminiLiveAssistant"
        const val LIVE_MODEL_NAME = "gemini-3.8-live"

        const val TRAINER_SYSTEM_INSTRUCTION = """
            You are FitLens, a perceptive, emotionally intelligent Personal AI Trainer & Wellness Companion.
            You can see the user's live facial features and expressions, and you receive real-time physiological context (heart rate, HRV, breathing cadence, recent trends, and time of day).

            How to coach and converse naturally:
            1. Speak naturally and warmly, like an attentive personal trainer — NEVER recite telemetry like a dashboard or list metrics mechanically. Weave cues in subtly (e.g., "Your breathing is steady and calm — ready for another set?" or "You look a little tense right now, let's take an extra 20 seconds of rest").
            2. Live Training & Pacing: When the user is doing a set or asks to train, actively count reps, call out tempo/breathing cues, and guide rest periods out loud, adapting rest duration or intensity to their live heart rate, breathing, and facial strain.
            3. Emotional Congruence: Pay attention to facial-vs-physiological cues — if their face looks neutral or smiling while their heart rate is elevated, gently check in on whether they're energized, pushing through fatigue, or holding stress.
            4. Remember what the user shared earlier in the conversation and connect it to how they seem right now.
            5. Keep responses concise (2-4 sentences) and NEVER use Markdown headers (##), tables (|...|), or asterisks (**).
            6. Never diagnose medical conditions; frame physical cues strictly as gentle emotional, readiness, and training observations.
        """
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Kept outside [scope] so the final suspend close() still runs after shutdown
    // cancels the session scope.
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(LiveAssistantState())
    val state: StateFlow<LiveAssistantState> = _state.asStateFlow()

    @Volatile
    private var liveSession: LiveSession? = null

    private val liveModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = LIVE_MODEL_NAME,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("AOEDE"))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            systemInstruction = content {
                text(TRAINER_SYSTEM_INSTRUCTION.trimIndent())
            }
        )
    }

    val isLiveVoiceActive: Boolean
        get() = _state.value.connectionState == LiveConnectionState.LISTENING

    @SuppressLint("MissingPermission")
    fun startVoiceSession(initialBiometricContext: String) {
        val current = _state.value.connectionState
        if (current == LiveConnectionState.CONNECTING || current == LiveConnectionState.LISTENING) {
            return
        }

        _state.update {
            it.copy(
                connectionState = LiveConnectionState.CONNECTING,
                errorMessage = null
            )
        }

        scope.launch {
            try {
                val session = liveModel.connect()
                liveSession = session

                if (initialBiometricContext.isNotBlank()) {
                    runCatching {
                        session.send(initialBiometricContext, turnComplete = false)
                    }
                }

                _state.update {
                    it.copy(
                        connectionState = LiveConnectionState.LISTENING,
                        errorMessage = null
                    )
                }

                session.startAudioConversation(
                    transcriptHandler = { inputTx: Transcription?, outputTx: Transcription? ->
                        handleTranscriptions(inputTx?.text, outputTx?.text)
                    },
                    goAwayHandler = {
                        Log.w(TAG, "Gemini Live session received GoAway")
                        stopVoiceSession()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Gemini Live voice session", e)
                liveSession = null
                _state.update {
                    it.copy(
                        connectionState = LiveConnectionState.ERROR,
                        errorMessage = e.message ?: "Unable to connect to Gemini Live. You can still chat via text."
                    )
                }
            }
        }
    }

    private fun handleTranscriptions(inputChunk: String?, outputChunk: String?) {
        _state.update { current ->
            var messages = current.messages
            var userStream = current.streamingUserText
            var assistantStream = current.streamingAssistantText

            if (!inputChunk.isNullOrBlank()) {
                if (assistantStream.isNotBlank()) {
                    messages = messages + AssistantMessage(
                        isUser = false,
                        text = MoodSpeculator.cleanAssistantText(assistantStream)
                    )
                    assistantStream = ""
                }
                userStream = appendTranscriptChunk(userStream, inputChunk)
            }

            if (!outputChunk.isNullOrBlank()) {
                if (userStream.isNotBlank()) {
                    messages = messages + AssistantMessage(
                        isUser = true,
                        text = userStream.trim()
                    )
                    userStream = ""
                }
                assistantStream = appendTranscriptChunk(assistantStream, outputChunk)
            }

            current.copy(
                messages = messages,
                streamingUserText = userStream,
                streamingAssistantText = assistantStream
            )
        }
    }

    private fun appendTranscriptChunk(existing: String, chunk: String): String {
        if (existing.isBlank()) return chunk.trimStart()
        val needsSpace = !existing.endsWith(" ") && !chunk.startsWith(" ") &&
            chunk.firstOrNull()?.let { it !in listOf('.', ',', '!', '?', ';', ':', '\'') } == true
        return if (needsSpace) "$existing $chunk" else "$existing$chunk"
    }

    fun stopVoiceSession() {
        val session = liveSession
        liveSession = null

        scope.launch {
            runCatching { session?.stopAudioConversation() }
            runCatching { session?.close() }
        }

        flushStreamedTranscripts()
    }

    private fun flushStreamedTranscripts() {
        _state.update { current ->
            val flushed = buildList {
                addAll(current.messages)
                if (current.streamingUserText.isNotBlank()) {
                    add(AssistantMessage(isUser = true, text = current.streamingUserText.trim()))
                }
                if (current.streamingAssistantText.isNotBlank()) {
                    add(
                        AssistantMessage(
                            isUser = false,
                            text = MoodSpeculator.cleanAssistantText(current.streamingAssistantText)
                        )
                    )
                }
            }
            current.copy(
                connectionState = LiveConnectionState.DISCONNECTED,
                messages = flushed,
                streamingUserText = "",
                streamingAssistantText = ""
            )
        }
    }

    fun sendBiometricContext(context: String) {
        val session = liveSession ?: return
        if (session.isClosed() || context.isBlank()) return
        scope.launch {
            runCatching {
                session.send(context, turnComplete = false)
            }.onFailure { e ->
                Log.v(TAG, "Skipped biometric context sync: ${e.message}")
            }
        }
    }

    fun sendVideoFrame(jpegBytes: ByteArray) {
        val session = liveSession ?: return
        if (session.isClosed() || !isLiveVoiceActive || jpegBytes.isEmpty()) return
        scope.launch {
            runCatching {
                session.sendVideoRealtime(InlineData(jpegBytes, "image/jpeg"))
            }.onFailure { e ->
                Log.v(TAG, "Skipped video frame: ${e.message}")
            }
        }
    }

    fun sendTextMessage(
        userText: String,
        biometricContext: String,
        fallbackSpeculation: MoodSpeculation,
        preferOffline: Boolean = false
    ) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        _state.update { current ->
            current.copy(
                messages = current.messages + AssistantMessage(isUser = true, text = trimmed),
                isSendingText = true,
                errorMessage = null
            )
        }

        val session = liveSession
        if (session != null && !session.isClosed() && isLiveVoiceActive) {
            scope.launch {
                runCatching {
                    session.send("$biometricContext\n\nUser message: $trimmed", turnComplete = true)
                }.onFailure { e ->
                    Log.w(TAG, "LiveSession text send failed: ${e.message}")
                }
                _state.update { it.copy(isSendingText = false) }
            }
            return
        }

        scope.launch {
            val recentHistory = _state.value.messages
                .takeLast(8)
                .joinToString("\n") { msg ->
                    val speaker = if (msg.isUser) "User" else "Trainer"
                    "$speaker: ${msg.text}"
                }

            val reply = if (!preferOffline) {
                runCatching {
                    val prompt = """
                        ${TRAINER_SYSTEM_INSTRUCTION.trimIndent()}

                        $biometricContext

                        Recent conversation:
                        $recentHistory

                        Respond naturally to the user's latest message in 2-4 warm sentences without any Markdown formatting.
                    """.trimIndent()
                    MoodSpeculator.cleanAssistantText(textFallbackService.generateText(prompt))
                }.getOrNull()
            } else {
                null
            }

            val finalReply = reply?.takeIf { it.isNotBlank() }
                ?: buildOfflineEmpatheticReply(trimmed, fallbackSpeculation)

            _state.update { current ->
                current.copy(
                    messages = current.messages + AssistantMessage(
                        isUser = false,
                        text = finalReply,
                        moodTag = "${fallbackSpeculation.emoji} ${fallbackSpeculation.label}"
                    ),
                    isSendingText = false
                )
            }
        }
    }

    private fun buildOfflineEmpatheticReply(
        userText: String,
        speculation: MoodSpeculation
    ): String {
        val lower = userText.lowercase()
        val hasSignals = speculation.label != "Waiting for signals"
        val trendAddon = speculation.trendNote?.let { " ($it)" } ?: ""
        val incongruenceAddon = speculation.incongruenceHint?.let { " $it" } ?: ""

        return when {
            "relax" in lower || "unwind" in lower || "calm" in lower || "breathe" in lower || "anxious" in lower ->
                "Let's take a quick reset together. Try a slow 4-second inhale, 2-second hold, and 6-second exhale while softening your shoulders — I'll watch how your breathing and pulse settle."
            "tired" in lower || "exhausted" in lower || "sleep" in lower || "drain" in lower ->
                if (hasSignals) {
                    "${speculation.emoji} It sounds like your energy is running low. ${speculation.summary}$trendAddon Would a 60-second quiet breather or light mobility work better today?"
                } else {
                    "It sounds like you're carrying some fatigue today. Give yourself permission to slow down — what's been taking the most out of you?"
                }
            "feel" in lower || "mood" in lower || "stress" in lower || "energy" in lower || "how am i" in lower ->
                if (hasSignals) {
                    "${speculation.emoji} ${speculation.summary}$trendAddon$incongruenceAddon How are things feeling from your side right now?"
                } else {
                    "I'm ready to tune in! Tap Start Sensing so I can read your facial expression, pulse, and breathing — or just tell me how your day is going."
                }
            lower.trim() in setOf("hi", "hey", "hello", "yo", "good morning", "good evening") ||
                lower.startsWith("hi ") || lower.startsWith("hey ") || lower.startsWith("hello ") ->
                if (hasSignals) {
                    "Hey! ${speculation.emoji} ${speculation.summary} What's on your mind right now?"
                } else {
                    "Hey! Good to see you. Turn on sensing whenever you'd like me to read your vitals and expression, or we can just chat."
                }
            else ->
                if (hasSignals) {
                    "I hear you. Right now you seem ${speculation.label.lowercase()} — ${speculation.summary}$incongruenceAddon Tell me more about what's on your mind."
                } else {
                    "I hear you — tell me a bit more about what's on your mind right now."
                }
        }
    }

    fun notifyBiometricShift(
        shift: BiometricShiftEvent,
        biometricContext: String,
        moodTag: String
    ) {
        val session = liveSession
        if (session != null && !session.isClosed() && isLiveVoiceActive) {
            scope.launch {
                runCatching {
                    session.send(
                        "$biometricContext\n[LIVE OBSERVATION] ${shift.description}. Notice this naturally if relevant.",
                        turnComplete = false
                    )
                }
            }
            return
        }

        _state.update { current ->
            val lastAssistantMsg = current.messages.lastOrNull { !it.isUser }?.text
            if (lastAssistantMsg == shift.gentlePrompt) {
                current
            } else {
                current.copy(
                    messages = current.messages + AssistantMessage(
                        isUser = false,
                        text = shift.gentlePrompt,
                        moodTag = moodTag
                    )
                )
            }
        }
    }

    fun showErrorMessage(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun clearErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun clearMessages() {
        _state.update {
            it.copy(
                messages = listOf(
                    AssistantMessage(
                        isUser = false,
                        text = "Fresh start! Whenever you're ready, talk to me or ask how you're coming across."
                    )
                ),
                streamingUserText = "",
                streamingAssistantText = "",
                errorMessage = null
            )
        }
    }

    fun shutdown() {
        val session = liveSession
        liveSession = null
        runCatching { session?.stopAudioConversation() }
        teardownScope.launch { runCatching { session?.close() } }
        flushStreamedTranscripts()
        scope.cancel()
    }
}
