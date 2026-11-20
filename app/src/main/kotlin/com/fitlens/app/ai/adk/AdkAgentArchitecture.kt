package com.fitlens.app.ai.adk

import android.util.Log
import com.fitlens.app.ai.firebase.FirebaseAiService

/**
 * Authentication and backend model routing modes for ADK Kotlin.
 */
enum class AdkAuthMode {
    /**
     * Hybrid Inference: Best-in-class orchestration combining Cloud Firebase AI Logic
     * (Gemini 3.8 Flash) for high-level generative reasoning, plan generation, and multimodal
     * media cues, with deterministic sub-10ms On-Device Edge (Gemini Nano / LocalEdgeEngine)
     * for real-time safety, adaptive rest, and offline resilience.
     */
    HYBRID_INFERENCE,

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

/**
 * Hybrid model adapter combining Cloud Firebase AI Logic with On-Device Edge.
 * Dispatches generative reasoning to Firebase AI Logic while retaining instant
 * on-device deterministic fallback and local safety evaluation.
 */
data class HybridGeminiAdapter(
    val cloudAdapter: FirebaseGeminiAdapter = FirebaseGeminiAdapter(),
    val edgeAdapter: EdgeGeminiNanoAdapter = EdgeGeminiNanoAdapter()
) : AdkModelAdapter {
    override val modelName: String = "hybrid(gemini-3.8-flash + gemini-nano-v2)"
    override val authMode: AdkAuthMode = AdkAuthMode.HYBRID_INFERENCE

    suspend fun generate(prompt: String, tools: List<Any> = emptyList()): String {
        return runCatching {
            cloudAdapter.generate(prompt)
        }.getOrElse { e ->
            Log.w("HybridGeminiAdapter", "Cloud Firebase AI Logic unavailable (${e.message}), executing via Edge Nano.")
            edgeAdapter.generate(prompt, tools)
        }
    }
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
            is HybridGeminiAdapter -> {
                currentModel.generate(prompt, agent.tools)
            }
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
