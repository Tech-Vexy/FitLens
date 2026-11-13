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
     * On-device sub-agent using Gemini Nano / LiteRT-LM (ML Kit).
     * Offline, zero network latency, privacy-sensitive local reasoning.
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
 *
 * Deterministic, network-free tool routing: discovers the agent's @Tool
 * functions and dispatches matching intents to FitnessTools locally.
 * No model download, no API key, sub-10ms latency for safety checks.
 */
data class EdgeGeminiNanoAdapter(
    override val modelName: String = "gemini-nano-v2"
) : AdkModelAdapter {
    override val authMode: AdkAuthMode = AdkAuthMode.ON_DEVICE_EDGE

    private val engine = LocalEdgeEngine()

    /**
     * Runs local edge inference over the agent's registered tools.
     */
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
 * Handles coroutine dispatching, session state, and tool routing without persistent overhead.
 */
class AdkInMemoryRunner(
    val agent: AdkLlmAgent,
    val appName: String = "FitLens"
) {
    companion object {
        private const val TAG = "AdkInMemoryRunner"
    }

    private val sessionHistory = mutableListOf<String>()

    init {
        Log.i(
            TAG,
            "Initialized ADK InMemoryRunner for '${agent.name}' [Backend: ${agent.model.authMode}, Model: ${agent.model.modelName}]"
        )
    }

    /**
     * Executes prompt or event stream on the active agent.
     */
    suspend fun execute(prompt: String): String {
        sessionHistory.add("User: $prompt")
        Log.d(TAG, "Executing agent prompt via ${agent.model.authMode}: $prompt")

        val response = when (agent.model.authMode) {
            AdkAuthMode.FIREBASE_AI_LOGIC -> {
                (agent.model as FirebaseGeminiAdapter).generate(prompt)
            }
            AdkAuthMode.ON_DEVICE_EDGE -> {
                (agent.model as EdgeGeminiNanoAdapter).generate(prompt, agent.tools)
            }
        }

        sessionHistory.add("Agent: $response")
        return response
    }

    fun getHistory(): List<String> = sessionHistory.toList()
}
