package com.fitlens.app.ai

import android.util.Log
import com.fitlens.app.ai.adk.AdkAuthMode
import com.fitlens.app.ai.adk.AdkInMemoryRunner
import com.fitlens.app.ai.adk.AdkInstruction
import com.fitlens.app.ai.adk.AdkLlmAgent
import com.fitlens.app.ai.adk.EdgeGeminiNanoAdapter
import com.fitlens.app.ai.adk.FirebaseGeminiAdapter

/**
 * FitnessCoachAgent encapsulates agent definition and execution orchestration using ADK Kotlin.
 *
 * Privacy boundary: Biometric and physiological vitals (pulse, breathing rate, HRV, expressions,
 * skeletal landmarks) NEVER leave the device. Cloud prompts (when Firebase AI Logic is enabled)
 * contain strictly non-biometric workout preferences (goal, experience, equipment, duration).
 */
class FitnessCoachAgent(
    private val tools: FitnessTools = FitnessTools(),
    initialAuthMode: AdkAuthMode = AdkAuthMode.FIREBASE_AI_LOGIC
) {
    companion object {
        private const val TAG = "FitnessCoachAgent"
        const val AGENT_NAME = "fitness_coach"
        const val MODEL_GEMINI_FLASH = "gemini-3.8-flash"
        const val MODEL_GEMINI_NANO = "gemini-nano-v2"

        const val SYSTEM_INSTRUCTION = """
            You are the FitLens Fitness and Wellness Coach built with Google ADK Kotlin.
            Use provided tools for plan generation, session analysis, adaptive rest recovery,
            and physiological/biomechanical safety feedback.
            Never diagnose medical conditions, claim clinical treatment, or advise against physician guidance.
            Always prioritize biomechanical safety and non-medical wellness coaching.
        """
    }

    var rootAgent: AdkLlmAgent = buildAgent(initialAuthMode)
        private set

    val runner: AdkInMemoryRunner = AdkInMemoryRunner(
        agent = rootAgent,
        appName = "FitLens"
    )

    private fun buildAgent(mode: AdkAuthMode): AdkLlmAgent = AdkLlmAgent(
        name = AGENT_NAME,
        description = "Personalized fitness and biomechanics coach for FitLens.",
        model = when (mode) {
            AdkAuthMode.FIREBASE_AI_LOGIC -> FirebaseGeminiAdapter(modelName = MODEL_GEMINI_FLASH)
            AdkAuthMode.ON_DEVICE_EDGE -> EdgeGeminiNanoAdapter(modelName = MODEL_GEMINI_NANO)
        },
        instruction = AdkInstruction(SYSTEM_INSTRUCTION),
        tools = listOf(tools)
    )

    /**
     * Switches the active ADK backend between Firebase AI Logic and 100% On-Device Edge.
     */
    @Synchronized
    fun setAuthMode(mode: AdkAuthMode) {
        if (rootAgent.model.authMode == mode) return
        rootAgent = buildAgent(mode)
        runner.agent = rootAgent
        Log.i(TAG, "Switched FitnessCoachAgent backend to $mode (${rootAgent.model.modelName})")
    }

    /**
     * Executes plan generation via the ADK Kotlin Agent and merges any LLM coaching rationale.
     * Only non-biometric workout preferences are included in the prompt.
     */
    suspend fun requestWorkoutPlan(
        goal: String,
        experience: String,
        equipment: String,
        durationMinutes: Int
    ): WorkoutPlanResult {
        Log.i(TAG, "Requesting workout plan via ${rootAgent.name} [Auth: ${rootAgent.model.authMode}]")
        val prompt =
            "Generate a concise coaching cue (1-2 sentences, wellness-only, no medical claims) for a " +
            "$durationMinutes minute $goal workout for an $experience athlete using $equipment."
        val agentResponse = runCatching { runner.execute(prompt) }.getOrNull().orEmpty().trim()
        val structured = tools.generateWorkoutPlan(goal, experience, equipment, durationMinutes)
        return if (agentResponse.isNotBlank()) {
            structured.copy(aiCoachRationale = agentResponse.take(260))
        } else {
            structured
        }
    }

    /**
     * Executes post-workout session evaluation strictly locally (zero cloud vitals transmission).
     */
    fun evaluateSession(
        durationSeconds: Int,
        avgHrBpm: Double
    ): SessionAnalysisResult {
        Log.i(TAG, "Evaluating session locally with vitals: $avgHrBpm bpm")
        return tools.analyzeSession(durationSeconds, avgHrBpm)
    }

    /**
     * Dynamically adapts rest duration using real-time vitals locally on-device.
     */
    suspend fun calculateAdaptiveRest(
        baseRestSeconds: Int,
        currentHeartRateBpm: Double,
        restingHeartRateBpm: Double = 70.0,
        preferLocal: Boolean = true
    ): RestAdjustmentResult {
        val mode = if (preferLocal) AdkAuthMode.ON_DEVICE_EDGE else rootAgent.model.authMode
        Log.i(TAG, "Evaluating adaptive rest ($mode) for HR: $currentHeartRateBpm bpm")
        return tools.calculateAdaptiveRest(baseRestSeconds, currentHeartRateBpm, restingHeartRateBpm)
    }

    /**
     * Evaluates biomechanical fatigue during an active set locally at the edge (<10ms).
     */
    suspend fun checkBiomechanicalFatigue(
        consecutiveDeviations: Int,
        currentSetReps: Int,
        targetReps: Int
    ): FatigueCheckResult {
        Log.i(TAG, "Checking fatigue: $consecutiveDeviations deviations at rep $currentSetReps")
        return tools.evaluateBiomechanicalFatigue(consecutiveDeviations, currentSetReps, targetReps)
    }

    /**
     * Synthesizes all 4 SmartSpectra live signals (pulse, breathing, HRV, expressions)
     * strictly locally on-device.
     */
    fun evaluateBiofeedback(
        heartRateBpm: Double,
        breathingRateBpm: Double,
        hrvRmssdMs: Double,
        facialExpression: String?
    ): BiofeedbackCoachingResult {
        return tools.evaluateHolisticBiofeedback(
            heartRateBpm = heartRateBpm,
            breathingRateBpm = breathingRateBpm,
            hrvRmssdMs = hrvRmssdMs,
            facialExpression = facialExpression ?: "Neutral"
        )
    }

    /**
     * Generates a multi-week periodization mesocycle via ADK Agent (with offline Edge fallback).
     */
    suspend fun requestMesocyclePlan(
        goal: String,
        weeks: Int = 4,
        frequencyPerWeek: Int = 3
    ): MesocyclePlanResult {
        Log.i(TAG, "Requesting $weeks-week mesocycle plan via ${rootAgent.name} ($goal)")
        val prompt =
            "Generate a $weeks-week periodization mesocycle overview for $goal at $frequencyPerWeek days/week."
        val agentResponse = runCatching { runner.execute(prompt) }.getOrNull().orEmpty().trim()
        val structured = tools.generateMesocyclePeriodization(goal, weeks, frequencyPerWeek)
        return if (agentResponse.isNotBlank()) {
            structured.copy(aiCoachRationale = agentResponse.take(260))
        } else {
            structured
        }
    }
}
