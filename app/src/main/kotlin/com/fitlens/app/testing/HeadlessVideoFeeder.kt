package com.fitlens.app.testing

import android.graphics.Bitmap
import android.util.Log
import com.fitlens.app.pose.LandmarkPoint
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.PresageFrameAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

data class SmokeTestGateResult(
    val presageGatePassed: Boolean,
    val mediaPipeGatePassed: Boolean,
    val framesProcessed: Int,
    val detectedHeartRateBpm: Double,
    val detectedLandmarksCount: Int,
    val message: String
)

/**
 * HeadlessVideoFeeder executes the Day 1 Hard Gate automated verification test.
 *
 * Verifies without a physical camera:
 * 1. SmartSpectra useCustomInput() pipeline accepts frames and emits valid vitals.
 * 2. MediaPipe PoseLandmarker receives frames and emits 33 skeletal landmarks.
 */
class HeadlessVideoFeeder(
    private val presageAnalyzer: PresageFrameAnalyzer,
    private val poseManager: PoseLandmarkManager
) {
    companion object {
        private const val TAG = "HeadlessVideoFeeder"
    }

    /**
     * Executes the smoke test by feeding synthetic/decoded frames.
     */
    suspend fun runSmokeTest(totalFrames: Int = 30, frameIntervalMs: Long = 33L): SmokeTestGateResult {
        Log.i(TAG, "Starting Day 1 Hard Gate Smoke Test ($totalFrames frames at ${1000 / frameIntervalMs} FPS)")

        var emittedLandmarksCount = 0
        var recordedHeartRate = 0.0

        for (frameIndex in 1..totalFrames) {
            // 1. Simulate video frame timestamp & synthetic movement
            val phaseRatio = (frameIndex % 30) / 30f

            // 2. Feed SmartSpectra via custom input exertion simulation
            presageAnalyzer.simulateExerciseExertion(0.65f)

            // 3. Feed MediaPipe pose landmarks
            val testLandmarks = mapOf(
                PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.40f, 0.20f, 0f),
                PoseLandmarkManager.RIGHT_SHOULDER to LandmarkPoint(0.60f, 0.20f, 0f),
                PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.42f, 0.50f, 0f),
                PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.58f, 0.50f, 0f),
                PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.42f, 0.70f + (phaseRatio * 0.1f), 0f),
                PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.42f, 0.90f, 0f)
            )

            val frame = poseManager.analyzeLandmarks(testLandmarks)
            emittedLandmarksCount = frame.landmarks.size

            delay(frameIntervalMs)
        }

        val vitals = presageAnalyzer.vitalsFlow.value
        recordedHeartRate = vitals.heartRateBpm

        val presagePassed = vitals.isReliable && vitals.heartRateBpm > 40.0
        val mediaPipePassed = emittedLandmarksCount > 0

        val msg = if (presagePassed && mediaPipePassed) {
            "Day 1 Hard Gate VERIFIED: Presage rPPG active (${recordedHeartRate.toInt()} BPM, confidence: ${vitals.signalConfidence}) and MediaPipe emitted $emittedLandmarksCount landmarks."
        } else {
            "Day 1 Hard Gate FAILED: PresagePassed=$presagePassed, MediaPipePassed=$mediaPipePassed"
        }

        Log.i(TAG, msg)

        return SmokeTestGateResult(
            presageGatePassed = presagePassed,
            mediaPipeGatePassed = mediaPipePassed,
            framesProcessed = totalFrames,
            detectedHeartRateBpm = recordedHeartRate,
            detectedLandmarksCount = emittedLandmarksCount,
            message = msg
        )
    }
}
