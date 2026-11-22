package com.fitlens.app.vitals

import android.util.Log
import com.fitlens.app.BuildConfig
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initializes and configures Presage SmartSpectra SDK.
 *
 * Authentication: API key injected at build time via the Secrets Gradle Plugin
 *   → BuildConfig.PRESAGE_API_KEY (defined in secrets.properties, git-ignored).
 *
 * Requested metrics:
 *   • SmartSpectraConfig.cardioMetrics  (pulse rate, HRV, arterial pressure trace)
 *   • SmartSpectraConfig.breathingMetrics (respiratory rate, chest/abdomen waveforms)
 */
class PresageManager {

    companion object {
        private const val TAG = "PresageManager"
        const val PLACEHOLDER_KEY = "PLACEHOLDER_RUN_SETUP"
    }

    /**
     * Returns true when secrets.properties has not been configured with a live Presage key.
     */
    fun isPlaceholderKey(): Boolean {
        val key = BuildConfig.PRESAGE_API_KEY
        return key.isBlank() || key == PLACEHOLDER_KEY
    }

    /**
     * Returns the API key resolved from BuildConfig (secrets.properties at build time).
     * Logs a warning if it's still the placeholder, so the developer knows to fill it in.
     */
    fun getApiKey(): String {
        val key = BuildConfig.PRESAGE_API_KEY
        if (isPlaceholderKey()) {
            Log.w(TAG, "PRESAGE_API_KEY is placeholder — set a real key in secrets.properties.")
        } else {
            Log.d(TAG, "PRESAGE_API_KEY resolved (${key.take(4)}…${key.takeLast(4)}).")
        }
        return key
    }

    companion object {
        private const val TAG = "PresageManager"
        const val PLACEHOLDER_KEY = "PLACEHOLDER_RUN_SETUP"

        private val initialized = AtomicBoolean(false)
    }

    /**
     * Configures SmartSpectraSdk.shared with the API key, telemetry preference, and metrics.
     *
     * This method is strictly idempotent — calling it multiple times is a no-op after
     * the first successful initialization. This prevents Compose recompositions or
     * error callbacks from re-configuring the SDK mid-session, which would cancel
     * in-flight model artifact downloads ("Artifact request cancelled").
     *
     * Note: We use the custom input (headless) mode — the SDK's built-in camera is NOT
     * used. CameraX provides the preview and ImageAnalysis, and frames are forwarded
     * to SmartSpectra via useCustomInput(). This avoids camera device lock contention
     * between SmartSpectra and MediaPipe.
     */
    fun initializeSdk(enableTelemetry: Boolean = true) {
        // Idempotent guard: only configure once per process lifetime.
        if (!initialized.compareAndSet(false, true)) {
            Log.d(TAG, "SmartSpectraSdk already initialized — skipping duplicate call.")
            return
        }

        val sdk = SmartSpectraSdk.shared
        sdk.config.apiKey = getApiKey()

        // Disable raw bitmap output — we render the CameraX PreviewView directly
        sdk.config.imageOutputEnabled = false

        // Respect user's opt-out preference for aggregate diagnostic SDK telemetry
        sdk.config.enableTelemetry = enableTelemetry

        // Cardio + breathing only. The face bundle (landmarks/expressions) is
        // intentionally excluded: it requires the expressions-inference model
        // artifact and isn't needed for workout vitals.
        sdk.config.requestedMetrics =
            SmartSpectraConfig.cardioMetrics +
            SmartSpectraConfig.breathingMetrics

        Log.i(
            TAG,
            "SmartSpectraSdk initialized (v${sdk.version}, cardio + breathing, telemetry=$enableTelemetry)."
        )
    }

    /**
     * Updates SmartSpectra aggregate diagnostic telemetry opt-out setting at runtime.
     */
    fun setTelemetryEnabled(enabled: Boolean) {
        runCatching {
            SmartSpectraSdk.shared.config.enableTelemetry = enabled
            Log.i(TAG, "SmartSpectra diagnostic telemetry updated: enabled=$enabled")
        }.onFailure { e ->
            Log.w(TAG, "Unable to update SmartSpectra telemetry flag: ${e.message}")
        }
    }
}
