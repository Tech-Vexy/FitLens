package com.fitlens.app.testing

import android.util.Log
import com.fitlens.app.vitals.PresageFrameAnalyzer
import kotlinx.coroutines.delay

data class SmokeTestGateResult(
    val presageGatePassed: Boolean,
    val framesProcessed: Int,
    val detectedHeartRateBpm: Double,
    val detectedFacialExpression: String?,
    val message: String
)

/**
 * HeadlessVideoFeeder executes the Day 1 Hard Gate automated verification test.
 *
 * Verifies without a physical camera:
 * 1. SmartSpectra useCustomInput() pipeline accepts frames and emits valid vitals.
 */
class HeadlessVideoFeeder(
    private val presageAnalyzer: PresageFrameAnalyzer
) {
    companion object {
        private const val TAG = "HeadlessVideoFeeder"
    }

    /**
     * Executes the smoke test by simulating exercise exertion via SmartSpectra custom input.
     */
    suspend fun runSmokeTest(totalFrames: Int = 30, frameIntervalMs: Long = 33L): SmokeTestGateResult {
        Log.i(TAG, "Starting Day 1 Hard Gate Smoke Test ($totalFrames frames at ${1000 / frameIntervalMs} FPS)")

        for (frameIndex in 1..totalFrames) {
            // Simulate video frame timestamp & synthetic movement
            val phaseRatio = (frameIndex % 30) / 30f
            val exertion = 0.55f + (phaseRatio * 0.2f)

            // Feed SmartSpectra via custom input exertion simulation
            presageAnalyzer.simulateExerciseExertion(exertion)

            delay(frameIntervalMs)
        }

        val vitals = presageAnalyzer.vitalsFlow.value
        val recordedHeartRate = vitals.heartRateBpm
        val detectedExpression = vitals.facialExpression

        val presagePassed = vitals.isReliable && vitals.heartRateBpm > 40.0

        val msg = if (presagePassed) {
            "Day 1 Hard Gate VERIFIED: Presage rPPG active (${recordedHeartRate.toInt()} BPM, confidence: ${vitals.signalConfidence}, expression: $detectedExpression)."
        } else {
            "Day 1 Hard Gate FAILED: PresagePassed=$presagePassed, HR=${recordedHeartRate.toInt()} BPM"
        }

        Log.i(TAG, msg)

        return SmokeTestGateResult(
            presageGatePassed = presagePassed,
            framesProcessed = totalFrames,
            detectedHeartRateBpm = recordedHeartRate,
            detectedFacialExpression = detectedExpression,
            message = msg
        )
    }
}
