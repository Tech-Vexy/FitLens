package com.fitlens.app.workout

import com.fitlens.app.data.model.WorkoutSessionEntity

data class ProgressionInsight(
    val title: String,
    val description: String,
    val recommendedRepsDelta: Int,
    val recommendedRestAdjustmentSeconds: Int,
    val overallReadinessScore: Int // 1 to 100
)

class ProgressAnalyzer {

    /**
     * Evaluates recent sessions and form metrics to determine progressive overload.
     */
    fun analyzeProgression(recentSessions: List<WorkoutSessionEntity>): ProgressionInsight {
        if (recentSessions.isEmpty()) {
            return ProgressionInsight(
                title = "Baseline Assessment",
                description = "Complete your first workout to generate individualized progression insights.",
                recommendedRepsDelta = 0,
                recommendedRestAdjustmentSeconds = 0,
                overallReadinessScore = 75
            )
        }

        val avgAccuracy = recentSessions.map { it.formAccuracyPercentage }.average()
        val avgHeartRate = recentSessions.map { it.averageHeartRate }.average()
        val latestSession = recentSessions.first()

        val readinessScore = when {
            avgAccuracy >= 90.0 && avgHeartRate < 145.0 -> 92
            avgAccuracy >= 80.0 && avgHeartRate < 160.0 -> 80
            avgAccuracy < 75.0 -> 60
            else -> 70
        }

        return when {
            avgAccuracy >= 90.0 && readinessScore > 85 -> ProgressionInsight(
                title = "Ready for Progressive Overload",
                description = "Exceptional form stability (${avgAccuracy.toInt()}%). Increase volume by 2 reps or reduce rest interval by 10s next session.",
                recommendedRepsDelta = 2,
                recommendedRestAdjustmentSeconds = -10,
                overallReadinessScore = readinessScore
            )
            avgAccuracy < 80.0 -> ProgressionInsight(
                title = "Form Consolidation Priority",
                description = "Form accuracy dipped to ${avgAccuracy.toInt()}%. Maintain current volume and focus on joint alignment before increasing load.",
                recommendedRepsDelta = 0,
                recommendedRestAdjustmentSeconds = 15,
                overallReadinessScore = readinessScore
            )
            latestSession.extendedRestSeconds > 30 -> ProgressionInsight(
                title = "Cardiovascular Recovery Adaptation",
                description = "Heart rate was elevated during rest intervals. Extending baseline rest by 15s to optimize ATP replenishment.",
                recommendedRepsDelta = 0,
                recommendedRestAdjustmentSeconds = 15,
                overallReadinessScore = readinessScore
            )
            else -> ProgressionInsight(
                title = "Steady Progression",
                description = "Solid exertion and control. Continue with scheduled plan parameters.",
                recommendedRepsDelta = 1,
                recommendedRestAdjustmentSeconds = 0,
                overallReadinessScore = readinessScore
            )
        }
    }
}
