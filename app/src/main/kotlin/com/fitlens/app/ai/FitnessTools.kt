package com.fitlens.app.ai

import com.fitlens.app.ai.adk.Param
import com.fitlens.app.ai.adk.Tool

/**
 * Data structures for ADK Agent Tool operations.
 */
data class WorkoutPlanResult(
    val goal: String,
    val targetExercises: List<String>,
    val targetDurationMinutes: Int,
    val recommendedRestIntervalSeconds: Int
)

data class SessionAnalysisResult(
    val summary: String,
    val exertionScore: Int,
    val formAccuracyPercentage: Float,
    val recommendation: String
)

data class RestAdjustmentResult(
    val originalRestSeconds: Int,
    val adjustedRestSeconds: Int,
    val reason: String,
    val isExtended: Boolean
)

data class FatigueCheckResult(
    val shouldHaltSet: Boolean,
    val fatigueLevel: String,
    val correctiveAction: String
)

data class MesocycleWeekPhase(
    val weekNumber: Int,
    val phaseName: String,
    val targetVolumeSetsPerMuscle: Int,
    val targetIntensityRpe: Int,
    val focusDirective: String
)

data class MesocyclePlanResult(
    val goal: String,
    val totalWeeks: Int,
    val daysPerWeek: Int,
    val phases: List<MesocycleWeekPhase>
)

/**
 * FitnessTools exposes domain functions for the ADK Kotlin LlmAgent.
 * Annotated with @Tool and @Param to be discoverable and callable by the agent runtime.
 */
class FitnessTools {

    @Tool(description = "Generates a personalized workout plan tailored to target goals, experience level, equipment, and duration.")
    fun generateWorkoutPlan(
        @Param(description = "Primary fitness goal (e.g. Hypertrophy, Endurance, Mobility)") goal: String,
        @Param(description = "Experience level (Beginner, Intermediate, Advanced)") experience: String,
        @Param(description = "Available equipment (e.g. Dumbbells, Barbell, Bodyweight)") equipment: String,
        @Param(description = "Preferred workout duration in minutes") durationMinutes: Int
    ): WorkoutPlanResult {
        val exercises = when {
            goal.contains("hypertrophy", ignoreCase = true) -> listOf("Barbell Squat", "Romanian Deadlift", "Overhead Press")
            goal.contains("endurance", ignoreCase = true) -> listOf("Jumping Jacks", "High Knees", "Burpees", "Mountain Climbers")
            else -> listOf("Bodyweight Squats", "Push-ups", "Plank", "Lunges")
        }

        return WorkoutPlanResult(
            goal = goal,
            targetExercises = exercises,
            targetDurationMinutes = durationMinutes,
            recommendedRestIntervalSeconds = if (experience.equals("advanced", ignoreCase = true)) 45 else 75
        )
    }

    @Tool(description = "Analyzes an active or completed workout session using biomechanical deviations and average heart rate.")
    fun analyzeSession(
        @Param(description = "Total elapsed session duration in seconds") sessionDurationSeconds: Int,
        @Param(description = "Average heart rate recorded via Presage rPPG in BPM") averageHeartRateBpm: Double,
        @Param(description = "Total count of critical form deviations detected by MediaPipe") formDeviations: Int
    ): SessionAnalysisResult {
        val accuracy = maxOf(0f, 100f - (formDeviations * 3.5f))
        val exertion = when {
            averageHeartRateBpm > 160 -> 9
            averageHeartRateBpm > 140 -> 7
            averageHeartRateBpm > 120 -> 5
            else -> 3
        }

        return SessionAnalysisResult(
            summary = "Session completed in ${sessionDurationSeconds / 60}m with ${accuracy.toInt()}% form accuracy.",
            exertionScore = exertion,
            formAccuracyPercentage = accuracy,
            recommendation = if (accuracy < 80) "Focus on hip alignment and knee stability in next session." else "Form was consistent. Progressive overload recommended."
        )
    }

    @Tool(description = "Dynamically calculates adaptive recovery rest interval duration based on real-time heart rate.")
    fun calculateAdaptiveRest(
        @Param(description = "Base scheduled rest duration in seconds") baseRestSeconds: Int,
        @Param(description = "Current real-time heart rate from Presage rPPG in BPM") currentHeartRateBpm: Double,
        @Param(description = "Baseline resting heart rate in BPM") restingHeartRateBpm: Double = 70.0
    ): RestAdjustmentResult {
        val hrDelta = currentHeartRateBpm - restingHeartRateBpm
        return when {
            hrDelta > 65.0 -> {
                RestAdjustmentResult(
                    originalRestSeconds = baseRestSeconds,
                    adjustedRestSeconds = baseRestSeconds + 30,
                    reason = "Elevated heart rate (${currentHeartRateBpm.toInt()} BPM). Extending rest by 30s to allow cardiovascular recovery.",
                    isExtended = true
                )
            }
            hrDelta > 45.0 -> {
                RestAdjustmentResult(
                    originalRestSeconds = baseRestSeconds,
                    adjustedRestSeconds = baseRestSeconds + 15,
                    reason = "Moderate recovery rate (${currentHeartRateBpm.toInt()} BPM). Extending rest by 15s.",
                    isExtended = true
                )
            }
            else -> {
                RestAdjustmentResult(
                    originalRestSeconds = baseRestSeconds,
                    adjustedRestSeconds = baseRestSeconds,
                    reason = "Heart rate recovered (${currentHeartRateBpm.toInt()} BPM). Ready for next set on schedule.",
                    isExtended = false
                )
            }
        }
    }

    @Tool(description = "Evaluates biomechanical breakdown to protect joints and prevent exercise-induced injury.")
    fun evaluateBiomechanicalFatigue(
        @Param(description = "Count of consecutive critical form deviations on recent reps") consecutiveDeviations: Int,
        @Param(description = "Number of reps completed in current set") currentSetRepsCompleted: Int,
        @Param(description = "Target reps for current set") targetReps: Int
    ): FatigueCheckResult {
        return if (consecutiveDeviations >= 3) {
            FatigueCheckResult(
                shouldHaltSet = true,
                fatigueLevel = "High Biomechanical Breakdown",
                correctiveAction = "Stop set immediately at $currentSetRepsCompleted reps to prevent joint injury. Rest and hydrate."
            )
        } else if (consecutiveDeviations == 2) {
            FatigueCheckResult(
                shouldHaltSet = false,
                fatigueLevel = "Moderate Fatigue Warning",
                correctiveAction = "Form compromised on last 2 reps. Focus on strict tempo and core activation."
            )
        } else {
            FatigueCheckResult(
                shouldHaltSet = false,
                fatigueLevel = "Optimal Form",
                correctiveAction = "Maintain posture. Continue to target $targetReps reps."
            )
        }
    }

    @Tool(description = "Generates a structured multi-week periodization mesocycle with programmed progressive overload and deload phases.")
    fun generateMesocyclePeriodization(
        @Param(description = "Primary goal (Hypertrophy, Strength, Endurance)") goal: String,
        @Param(description = "Total number of weeks in mesocycle (typically 4-6)") weeksCount: Int = 4,
        @Param(description = "Training frequency in days per week") daysPerWeek: Int = 3
    ): MesocyclePlanResult {
        val phases = (1..weeksCount).map { week ->
            when (week) {
                1 -> MesocycleWeekPhase(week, "Accumulation (Foundation)", 10, 6, "Focus on movement groove and establishing baseline form scores >90%.")
                weeksCount -> MesocycleWeekPhase(week, "Active Deload (Supercompensation)", 6, 5, "50% volume deload to flush systemic fatigue and replenish glycogen.")
                weeksCount - 1 -> MesocycleWeekPhase(week, "Realization (Overload Peak)", 16, 9, "Peak intensity: progressive overload with strict biomechanical vigilance.")
                else -> MesocycleWeekPhase(week, "Intensification (Progressive Loading)", 13, 8, "Add +1 set or +2 reps per movement while maintaining joint stability.")
            }
        }

        return MesocyclePlanResult(
            goal = goal,
            totalWeeks = weeksCount,
            daysPerWeek = daysPerWeek,
            phases = phases
        )
    }
}
