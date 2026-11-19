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
     * Evaluates recent sessions and available physiological metrics to determine progressive overload.
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

        val hrSessions = recentSessions.filter {
            it.averageHeartRate.isFinite() && it.averageHeartRate > 0.0
        }
        val avgHeartRate = if (hrSessions.isEmpty()) {
            0.0
        } else {
            hrSessions.map { it.averageHeartRate }.average()
        }
        val latestSession = recentSessions.first()

        val readinessScore = when {
            hrSessions.isEmpty() -> 70
            avgHeartRate < 145.0 -> 90
            avgHeartRate < 160.0 -> 80
            else -> 65
        }

        return when {
            hrSessions.isEmpty() -> ProgressionInsight(
                title = "Limited Physiological Data",
                description = "Heart-rate samples were unavailable for recent sessions. Enable camera sensing for recovery and exertion insights.",
                recommendedRepsDelta = 0,
                recommendedRestAdjustmentSeconds = 0,
                overallReadinessScore = readinessScore
            )
            avgHeartRate < 145.0 && readinessScore >= 85 -> ProgressionInsight(
                title = "Ready for Progressive Overload",
                description = "Controlled average heart rate (${avgHeartRate.toInt()} BPM). Increase volume by 2 reps or reduce rest interval by 10s next session.",
                recommendedRepsDelta = 2,
                recommendedRestAdjustmentSeconds = -10,
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
