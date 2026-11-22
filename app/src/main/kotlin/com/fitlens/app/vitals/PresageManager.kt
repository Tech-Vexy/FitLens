package com.fitlens.app.vitals

import android.util.Log
import com.fitlens.app.BuildConfig
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk

/**
 * Initializes and configures Presage SmartSpectra SDK.
 *
 * Authentication: API key injected at build time via the Secrets Gradle Plugin
 *   → BuildConfig.PRESAGE_API_KEY (defined in secrets.properties, git-ignored).
 *
 * Requested metrics:
 *   • SmartSpectraConfig.cardioMetrics  (pulse rate, HRV, arterial pressure trace)
 *   • SmartSpectraConfig.breathingMetrics (respiratory rate, chest/abdomen waveforms)
 *   • SmartSpectraConfig.faceMetrics    (landmarks, blinking, talking, expressions)
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

    /**
     * Configures SmartSpectraSdk.shared with the API key, telemetry preference, and metrics.
     *
     * Note: We use the custom input (headless) mode — the SDK's built-in camera is NOT
     * used. CameraX provides the preview and ImageAnalysis, and frames are forwarded
     * to SmartSpectra via useCustomInput(). This avoids camera device lock contention
     * between SmartSpectra and MediaPipe.
     */
    fun initializeSdk(enableTelemetry: Boolean = true) {
        val sdk = SmartSpectraSdk.shared
        sdk.config.apiKey = getApiKey()

        // Disable raw bitmap output — we render the CameraX PreviewView directly
        sdk.config.imageOutputEnabled = false

        // Respect user's opt-out preference for aggregate diagnostic SDK telemetry
        sdk.config.enableTelemetry = enableTelemetry

        // Request the full cardio + breathing + face metric bundle
        // (faceMetrics includes EXPRESSIONS plus landmarks/blinking/talking)
        sdk.config.requestedMetrics =
            SmartSpectraConfig.cardioMetrics +
            SmartSpectraConfig.breathingMetrics +
            SmartSpectraConfig.faceMetrics

        Log.i(
            TAG,
            "SmartSpectraSdk initialized (v${sdk.version}, cardio + breathing + face, telemetry=$enableTelemetry)."
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
