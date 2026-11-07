package com.fitlens.app.vitals

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fitlens.app.data.profile.UserProfile
import com.presagetech.smartspectra.FrameSubmissionResult
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------------------------------------------------------------------------
// Domain types
// ---------------------------------------------------------------------------

/**
 * Snapshot of all physiological metrics from the SmartSpectra rPPG pipeline.
 */
data class VitalsReading(
    val heartRateBpm: Double = 0.0,
    val heartRateVariabilityMs: Double = 0.0,
    val breathingRateBpm: Double = 0.0,
    val arterialPressureTrace: List<Float> = emptyList(),
    val signalConfidence: Float = 0.0f,
    val heartRateZone: HeartRateZone = HeartRateZone.RESTING,
    val smartSpectraLlmInsight: String? = null,
    val isReliable: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class HeartRateZone(val label: String, val colorHex: Long) {
    RESTING("Resting",            0xFF9E9E9E),
    WARMUP("Warmup (Zone 1)",     0xFF4CAF50),
    AEROBIC("Aerobic (Zone 2)",   0xFF2196F3),
    ANAEROBIC("Anaerobic (Zone 3)", 0xFFFF9800),
    MAX("Max Threshold (Zone 4)", 0xFFF44336)
}

// ---------------------------------------------------------------------------
// PresageFrameAnalyzer
// ---------------------------------------------------------------------------

/**
 * CameraX ImageAnalysis.Analyzer that submits each frame to SmartSpectra via the
 * official Headless / custom-input API:
 *
 *   val input = SmartSpectraSdk.shared.useCustomInput()
 *   sdk.start()          // called once from the coroutine that owns the session
 *   input.sendFrame(imageProxy)   // called here for every CameraX frame
 *
 * The SDK's LiveData (sdk.metrics, sdk.error, sdk.validationStatus) must be observed
 * in the Activity / ViewModel — see WorkoutViewModel for that wiring.
 *
 * This class also maintains a [vitalsFlow] StateFlow so that the ViewModel and UI can
 * collect the latest reading reactively even before SDK LiveData arrives.
 *
 * @param userProfile Used for personalised Karvonen heart-rate zone classification.
 *                    Call [updateUserProfile] whenever the DataStore emits an update.
 */
class PresageFrameAnalyzer(
    userProfile: UserProfile = UserProfile()
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "PresageFrameAnalyzer"
    }

    // Mutable so the ViewModel can push DataStore profile updates
    @Volatile private var activeProfile: UserProfile = userProfile

    private val _vitalsFlow = MutableStateFlow(VitalsReading())
    val vitalsFlow: StateFlow<VitalsReading> = _vitalsFlow.asStateFlow()

    // -----------------------------------------------------------------------
    // Camera frame submission
    // -----------------------------------------------------------------------

    /**
     * Called by CameraX on every captured frame.
     *
     * The [CustomInput] handle is obtained once per SDK session via [useCustomInput]
     * and reused across frames. Frames are submitted directly — no copy, no bitmap
     * conversion required for YUV_420_888 images that are unrotated.
     *
     * Always closes [imageProxy] in the finally block, including on rejection, as
     * required by the SDK contract.
     */
    override fun analyze(imageProxy: ImageProxy) {
        val sdk = SmartSpectraSdk.shared
        // Only submit frames while the SDK pipeline is active
        try {
            val input = sdk.useCustomInput()
            when (val result = input.sendFrame(imageProxy)) {
                is FrameSubmissionResult.Accepted -> Unit
                is FrameSubmissionResult.Rejected -> {
                    Log.w(TAG, "Frame rejected: ${result.error}")
                }
            }
        } catch (e: Exception) {
            // SDK may not be started yet (e.g. first few frames before sdk.start() completes)
            Log.v(TAG, "sendFrame skipped: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    // -----------------------------------------------------------------------
    // Vitals reading — pushed by WorkoutViewModel from sdk.metrics LiveData
    // -----------------------------------------------------------------------

    /**
     * Updates [vitalsFlow] with a new reading decoded from sdk.metrics.
     * Called by WorkoutViewModel's metrics observer on the main thread.
     */
    fun updateVitalsReading(
        hr: Double,
        hrv: Double,
        breathingRate: Double,
        confidence: Float,
        llmInsight: String? = null,
        pulseTrace: List<Float> = emptyList()
    ) {
        val zone = calculateZone(hr)
        _vitalsFlow.value = VitalsReading(
            heartRateBpm         = hr,
            heartRateVariabilityMs = hrv,
            breathingRateBpm     = breathingRate,
            arterialPressureTrace = pulseTrace,
            signalConfidence     = confidence,
            heartRateZone        = zone,
            smartSpectraLlmInsight = llmInsight,
            isReliable           = confidence > 0.65f,
            timestampMs          = System.currentTimeMillis()
        )
    }

    // -----------------------------------------------------------------------
    // Profile & zone classification
    // -----------------------------------------------------------------------

    /**
     * Updates the UserProfile used for Karvonen zone classification.
     * Call this whenever the DataStore profile Flow emits a new value.
     */
    fun updateUserProfile(profile: UserProfile) {
        activeProfile = profile
        Log.d(TAG, "UserProfile updated: age=${profile.age}, restHR=${profile.restingHeartRate}")
    }

    private fun calculateZone(bpm: Double): HeartRateZone = activeProfile.getHeartRateZone(bpm)

    // -----------------------------------------------------------------------
    // CI / emulator testing helpers
    // -----------------------------------------------------------------------

    /**
     * Synthesises a plausible vitals reading proportional to [exertionFactor] (0..1).
     * Used by HeadlessVideoFeeder and the Day-1 smoke test when no real camera is available.
     */
    fun simulateExerciseExertion(exertionFactor: Float) {
        val hr  = (70.0 + exertionFactor * 90.0 + (Math.random() - 0.5) * 4.0).coerceIn(55.0, 195.0)
        val hrv = (65.0 - exertionFactor * 35.0).coerceIn(15.0, 90.0)
        val br  = (14.0 + exertionFactor * 18.0).coerceIn(10.0, 36.0)

        val wave = List(32) { i ->
            val phi = (i / 32f) * 2 * Math.PI
            (Math.sin(phi) + 0.35 * Math.sin(phi * 2.0)).toFloat()
        }
        val insight = if (exertionFactor > 0.7f)
            "SmartSpectra: Anaerobic threshold — consider extending rest interval."
        else
            "SmartSpectra: Aerobic rhythm steady. Good autonomic balance."

        updateVitalsReading(hr, hrv, br, 0.92f, insight, wave)
    }
}
