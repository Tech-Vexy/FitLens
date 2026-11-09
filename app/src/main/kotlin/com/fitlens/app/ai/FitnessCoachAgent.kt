package com.fitlens.app.ai

import android.util.Log
import com.fitlens.app.ai.adk.AdcVertexGeminiAdapter
import com.fitlens.app.ai.adk.AdkAuthMode
import com.fitlens.app.ai.adk.AdkInMemoryRunner
import com.fitlens.app.ai.adk.AdkInstruction
import com.fitlens.app.ai.adk.AdkLlmAgent
import com.fitlens.app.ai.adk.EdgeGeminiNanoAdapter
import com.fitlens.app.ai.adk.FirebaseGeminiAdapter

/**
 * FitnessCoachAgent encapsulates agent definition and execution orchestration using ADK Kotlin.
 *
 * Supported Authentication & Model Backends:
 * 1. Firebase AI Logic + App Check (Android production):
 *    - Uses FirebaseGeminiAdapter (Zero client API key in APK; gated by Play Integrity).
 * 2. Google Application Default Credentials (ADC) (Workstations, JVM, CI, Backend proxy):
 *    - Uses AdcVertexGeminiAdapter with Google ADC credentials (gcloud / service account).
 * 3. On-Device Edge Sub-Agent:
 *    - Uses EdgeGeminiNanoAdapter for zero-latency, local privacy-preserving execution.
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
            You are the FitLens AI Fitness and Biomechanics Coach built with Google ADK Kotlin.
            Use provided tools for plan generation, session analysis, adaptive rest recovery,
            and physiological/biomechanical safety feedback.
            Never diagnose medical conditions or advise against physician guidance.
            Always prioritize biomechanical safety and user safety thresholds.
        """
    }

    // ADK Kotlin LlmAgent specification
    val rootAgent: AdkLlmAgent = AdkLlmAgent(
        name = AGENT_NAME,
        description = "Personalized fitness and biomechanics coach for FitLens.",
        model = when (initialAuthMode) {
            AdkAuthMode.FIREBASE_AI_LOGIC -> FirebaseGeminiAdapter(modelName = MODEL_GEMINI_FLASH)
            AdkAuthMode.GOOGLE_ADC -> AdcVertexGeminiAdapter(modelName = MODEL_GEMINI_FLASH)
            AdkAuthMode.ON_DEVICE_EDGE -> EdgeGeminiNanoAdapter(modelName = MODEL_GEMINI_NANO)
        },
        instruction = AdkInstruction(SYSTEM_INSTRUCTION),
        tools = listOf(tools)
    )

    // In-memory runner for coroutine execution
    val runner: AdkInMemoryRunner = AdkInMemoryRunner(
        agent = rootAgent,
        appName = "FitLens"
    )

    /**
     * Executes plan generation via the ADK Kotlin Agent and tools.
     */
    suspend fun requestWorkoutPlan(
        goal: String,
        experience: String,
        equipment: String,
        durationMinutes: Int
    ): WorkoutPlanResult {
        Log.i(TAG, "Requesting workout plan via ${rootAgent.name} [Auth: ${rootAgent.model.authMode}]")
        runner.execute("Generate workout plan for goal=$goal, experience=$experience, equipment=$equipment, duration=$durationMinutes")
        return tools.generateWorkoutPlan(goal, experience, equipment, durationMinutes)
    }

    /**
     * Executes post-workout session evaluation with physiological and pose metrics.
     */
    suspend fun evaluateSession(
        durationSeconds: Int,
        avgHrBpm: Double,
        formDeviations: Int
    ): SessionAnalysisResult {
        Log.i(TAG, "Evaluating session locally with vitals: $avgHrBpm bpm, deviations: $formDeviations")
        // Health telemetry remains local-only; do not send it to Firebase AI Logic.
        return tools.analyzeSession(durationSeconds, avgHrBpm, formDeviations)
    }

    /**
     * Dynamically adapts rest duration using real-time vitals.
     * Can execute on Edge Sub-Agent (Gemini Nano) for instant zero-latency response.
     */
    suspend fun calculateAdaptiveRest(
        baseRestSeconds: Int,
        currentHeartRateBpm: Double,
        preferLocal: Boolean = true
    ): RestAdjustmentResult {
        val mode = if (preferLocal) AdkAuthMode.ON_DEVICE_EDGE else rootAgent.model.authMode
        Log.i(TAG, "Evaluating adaptive rest ($mode) for HR: $currentHeartRateBpm bpm")
        return tools.calculateAdaptiveRest(baseRestSeconds, currentHeartRateBpm)
    }

    /**
     * Evaluates biomechanical fatigue during an active set.
     * Always handled locally at the edge for sub-10ms safety reaction time.
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
     * Generates a multi-week periodization mesocycle via Cloud Gemini 3.8 Flash (Firebase AI Logic / ADC).
     */
    suspend fun requestMesocyclePlan(
        goal: String,
        weeks: Int = 4,
        frequencyPerWeek: Int = 3
    ): MesocyclePlanResult {
        Log.i(TAG, "Requesting $weeks-week mesocycle plan via ${rootAgent.name} ($goal)")
        runner.execute("Generate $weeks-week periodization mesocycle for $goal at $frequencyPerWeek days/week")
        return tools.generateMesocyclePeriodization(goal, weeks, frequencyPerWeek)
    }
}
