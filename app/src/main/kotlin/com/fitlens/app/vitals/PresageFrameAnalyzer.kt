package com.fitlens.app.vitals

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fitlens.app.data.profile.UserProfile
import com.presagetech.smartspectra.FrameSubmissionResult
import com.presagetech.smartspectra.FrameTransform
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ---------------------------------------------------------------------------
// Domain types
// ---------------------------------------------------------------------------

/**
 * Snapshot of all physiological and behavioral metrics from the SmartSpectra pipeline.
 */
data class VitalsReading(
    val heartRateBpm: Double = 0.0,
    val heartRateVariabilityMs: Double = 0.0,
    val breathingRateBpm: Double = 0.0,
    val facialExpression: String? = null,
    val arterialPressureTrace: List<Float> = emptyList(),
    val chestBreathingTrace: List<Float> = emptyList(),
    val abdomenBreathingTrace: List<Float> = emptyList(),
    val signalConfidence: Float = 0.0f,
    val heartRateZone: HeartRateZone = HeartRateZone.RESTING,
    val validationHint: String = "",
    val smartSpectraLlmInsight: String? = null,
    val isReliable: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class HeartRateZone(val label: String, val colorHex: Long) {
    RESTING("Resting",              0xFF9E9E9E),
    WARMUP("Warmup (Zone 1)",       0xFF4CAF50),
    AEROBIC("Aerobic (Zone 2)",     0xFF2196F3),
    ANAEROBIC("Anaerobic (Zone 3)", 0xFFFF9800),
    MAX("Max Threshold (Zone 4)",   0xFFF44336)
}

// ---------------------------------------------------------------------------
// PresageFrameAnalyzer
// ---------------------------------------------------------------------------

/**
 * CameraX ImageAnalysis.Analyzer that submits each frame to SmartSpectra via the
 * official Headless / custom-input API:
 *
 *   val input = SmartSpectraSdk.shared.useCustomInput(frameTransform)
 *   sdk.start()
 *   input.sendFrame(imageProxy)
 *
 * The SDK's LiveData (sdk.metrics, sdk.insight, sdk.error, sdk.validationStatus) is
 * observed in WorkoutViewModel and pushed into [vitalsFlow].
 */
class PresageFrameAnalyzer(
    userProfile: UserProfile = UserProfile()
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "PresageFrameAnalyzer"
    }

    @Volatile private var activeProfile: UserProfile = userProfile

    private val _vitalsFlow = MutableStateFlow(VitalsReading())
    val vitalsFlow: StateFlow<VitalsReading> = _vitalsFlow.asStateFlow()

    @Volatile private var customInput: com.presagetech.smartspectra.CustomInput? = null
    @Volatile private var frameTransform: FrameTransform = FrameTransform.NONE

    // -----------------------------------------------------------------------
    // Camera frame submission
    // -----------------------------------------------------------------------

    /**
     * Configures an optional [FrameTransform] for custom input sessions.
     * Changing the transform resets the cached handle so the next session picks it up.
     */
    fun setFrameTransform(transform: FrameTransform) {
        if (frameTransform != transform) {
            frameTransform = transform
            customInput = null
        }
    }

    /**
     * Obtains or reuses the [CustomInput] handle. Must be called while the SDK is stopped
     * (before [SmartSpectraSdk.start]), per the SmartSpectra headless-mode contract.
     */
    @Synchronized
    fun ensureCustomInput(): com.presagetech.smartspectra.CustomInput {
        return customInput ?: SmartSpectraSdk.shared.useCustomInput(frameTransform).also {
            customInput = it
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (SmartSpectraSdk.shared.processingStatus.value != ProcessingStatus.RUNNING) {
                return
            }

            val input = customInput ?: return
            when (val result = input.sendFrame(imageProxy)) {
                is FrameSubmissionResult.Accepted -> Unit
                is FrameSubmissionResult.Rejected -> {
                    Log.w(TAG, "Frame rejected: ${result.error}")
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "sendFrame skipped: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Resets the cached [CustomInput] handle after [SmartSpectraSdk.stop()].
     */
    fun resetCustomInput() {
        customInput = null
    }

    // -----------------------------------------------------------------------
    // Vitals reading & SDK stream updates
    // -----------------------------------------------------------------------

    fun updateVitalsReading(
        hr: Double,
        hrv: Double,
        breathingRate: Double,
        confidence: Float,
        llmInsight: String? = _vitalsFlow.value.smartSpectraLlmInsight,
        pulseTrace: List<Float> = _vitalsFlow.value.arterialPressureTrace,
        chestTrace: List<Float> = _vitalsFlow.value.chestBreathingTrace,
        abdomenTrace: List<Float> = _vitalsFlow.value.abdomenBreathingTrace,
        expression: String? = _vitalsFlow.value.facialExpression
    ) {
        val zone = calculateZone(hr)
        val currentHint = _vitalsFlow.value.validationHint
        _vitalsFlow.value = VitalsReading(
            heartRateBpm = hr,
            heartRateVariabilityMs = hrv,
            breathingRateBpm = breathingRate,
            facialExpression = expression,
            arterialPressureTrace = pulseTrace,
            chestBreathingTrace = chestTrace,
            abdomenBreathingTrace = abdomenTrace,
            signalConfidence = confidence,
            heartRateZone = zone,
            validationHint = currentHint,
            smartSpectraLlmInsight = llmInsight,
            isReliable = confidence > 0.65f,
            timestampMs = System.currentTimeMillis()
        )
    }

    fun updateValidationHint(hint: String) {
        _vitalsFlow.update { it.copy(validationHint = hint) }
    }

    fun updateSmartSpectraInsight(insight: String?) {
        _vitalsFlow.update { it.copy(smartSpectraLlmInsight = insight) }
    }

    fun clearSessionReadings() {
        _vitalsFlow.value = VitalsReading()
    }

    // -----------------------------------------------------------------------
    // Profile & zone classification
    // -----------------------------------------------------------------------

    fun updateUserProfile(profile: UserProfile) {
        activeProfile = profile
        val currentHr = _vitalsFlow.value.heartRateBpm
        if (currentHr > 0.0) {
            _vitalsFlow.update { it.copy(heartRateZone = profile.getHeartRateZone(currentHr)) }
        }
        Log.d(TAG, "UserProfile updated: age=${profile.age}, restHR=${profile.restingHeartRate}")
    }

    private fun calculateZone(bpm: Double): HeartRateZone = activeProfile.getHeartRateZone(bpm)

    // -----------------------------------------------------------------------
    // CI / emulator / live demo simulation helpers
    // -----------------------------------------------------------------------

    /**
     * Synthesises a plausible multi-signal vitals reading proportional to [exertionFactor] (0..1),
     * including pulse, HRV, breathing rate, arterial/breathing waveforms, and facial expression.
     */
    fun simulateExerciseExertion(exertionFactor: Float) {
        val clamped = exertionFactor.coerceIn(0f, 1f)
        val profile = activeProfile
        val targetHr = profile.restingHeartRate +
            clamped * (profile.maxHeartRate - profile.restingHeartRate)
        val hr  = (targetHr + (Math.random() - 0.5) * 4.0).coerceIn(55.0, 195.0)
        val hrv = (65.0 - clamped * 35.0).coerceIn(15.0, 90.0)
        val br  = (14.0 + clamped * 18.0).coerceIn(10.0, 36.0)

        val pulseWave = List(48) { i ->
            val phi = (i / 16f) * 2 * Math.PI
            (Math.sin(phi) + 0.35 * Math.sin(phi * 2.0)).toFloat()
        }
        val breathWave = List(48) { i ->
            val phi = (i / 24f) * 2 * Math.PI
            Math.sin(phi).toFloat()
        }
        val simulatedExpression = when {
            clamped > 0.80f -> "Strain"
            clamped < 0.45f -> "Happy"
            else -> "Neutral"
        }
        val insight = if (clamped > 0.7f) {
            "SmartSpectra: Anaerobic threshold & elevated breathing (${br.toInt()} brpm) — paced recovery breathing recommended."
        } else {
            "SmartSpectra: Aerobic rhythm steady (${hrv.toInt()} ms HRV). Good autonomic balance."
        }

        updateVitalsReading(
            hr = hr,
            hrv = hrv,
            breathingRate = br,
            confidence = 0.92f,
            llmInsight = insight,
            pulseTrace = pulseWave,
            chestTrace = breathWave,
            abdomenTrace = breathWave.map { it * 0.85f },
            expression = simulatedExpression
        )
    }
}
