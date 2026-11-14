package com.fitlens.app.ai.adk

import android.util.Log
import com.fitlens.app.ai.firebase.FirebaseAiService

/**
 * Authentication and backend model routing modes for ADK Kotlin.
 */
enum class AdkAuthMode {
    /**
     * Firebase AI Logic + Firebase App Check (Production Android App).
     * Attested by Play Integrity. Zero API keys in APK or client code.
     */
    FIREBASE_AI_LOGIC,

    /**
     * On-device deterministic @Tool routing engine.
     * Offline, zero network latency, 100% local execution.
     */
    ON_DEVICE_EDGE
}

/**
 * Common abstraction representing an ADK model backend adapter.
 */
sealed interface AdkModelAdapter {
    val modelName: String
    val authMode: AdkAuthMode
}

/**
 * Firebase-backed Gemini model adapter (Zero client API key).
 */
data class FirebaseGeminiAdapter(
    override val modelName: String = "gemini-3.8-flash",
    val appCheckEnforced: Boolean = true
) : AdkModelAdapter {
    override val authMode: AdkAuthMode = AdkAuthMode.FIREBASE_AI_LOGIC

    suspend fun generate(prompt: String): String =
        FirebaseAiService(modelName).generateText(prompt)
}

/**
 * On-device local inference adapter backed by [LocalEdgeEngine].
 */
data class EdgeGeminiNanoAdapter(
    override val modelName: String = "gemini-nano-v2"
) : AdkModelAdapter {
    override val authMode: AdkAuthMode = AdkAuthMode.ON_DEVICE_EDGE

    private val engine = LocalEdgeEngine()

    suspend fun generate(prompt: String, tools: List<Any> = emptyList()): String =
        engine.generate(tools, prompt)
}

data class AdkInstruction(val text: String)

/**
 * ADK Kotlin LlmAgent representation combining identity, instruction, model backend, and tools.
 */
data class AdkLlmAgent(
    val name: String,
    val description: String,
    val model: AdkModelAdapter,
    val instruction: AdkInstruction,
    val tools: List<Any>
)

/**
 * In-memory execution runner for ADK agents.
 * Handles coroutine dispatching, session state, and automatic offline fallback to
 * [EdgeGeminiNanoAdapter] if Firebase AI Logic is unavailable or offline.
 */
class AdkInMemoryRunner(
    var agent: AdkLlmAgent,
    val appName: String = "FitLens"
) {
    companion object {
        private const val TAG = "AdkInMemoryRunner"
    }

    private val sessionHistory = mutableListOf<String>()
    private val edgeFallback = EdgeGeminiNanoAdapter()

    init {
        Log.i(
            TAG,
            "Initialized ADK InMemoryRunner for '${agent.name}' [Backend: ${agent.model.authMode}, Model: ${agent.model.modelName}]"
        )
    }

    /**
     * Executes prompt on the active agent. If the cloud backend is offline or
     * unconfigured in a local/demo build, falls back seamlessly to on-device edge routing.
     */
    suspend fun execute(prompt: String): String {
        synchronized(sessionHistory) {
            sessionHistory.add("User: $prompt")
        }
        Log.d(TAG, "Executing agent prompt via ${agent.model.authMode}: $prompt")

        val response = when (val currentModel = agent.model) {
            is FirebaseGeminiAdapter -> {
                runCatching {
                    currentModel.generate(prompt)
                }.getOrElse { e ->
                    Log.w(TAG, "Firebase AI Logic unavailable (${e.message}); falling back to On-Device Edge.")
                    edgeFallback.generate(prompt, agent.tools)
                }
            }
            is EdgeGeminiNanoAdapter -> {
                currentModel.generate(prompt, agent.tools)
            }
        }

        synchronized(sessionHistory) {
            sessionHistory.add("Agent: $response")
        }
        return response
    }

    fun getHistory(): List<String> = synchronized(sessionHistory) { sessionHistory.toList() }
}
