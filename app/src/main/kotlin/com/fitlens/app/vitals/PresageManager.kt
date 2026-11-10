package com.fitlens.app.vitals

import android.content.Context
import android.util.Log
import com.fitlens.app.BuildConfig
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType

/**
 * Initializes and configures Presage SmartSpectra SDK.
 *
 * Authentication: API key injected at build time via the Secrets Gradle Plugin
 *   → BuildConfig.PRESAGE_API_KEY (defined in secrets.properties, git-ignored).
 *
 * Requested metrics:
 *   • SmartSpectraConfig.cardioMetrics  (pulse rate, HRV, arterial pressure trace)
 *   • SmartSpectraConfig.breathingMetrics (respiratory rate, chest/abdomen waveforms)
 *   • MetricType.EXPRESSIONS (facial expression classification)
 */
class PresageManager {

    companion object {
        private const val TAG = "PresageManager"
    }

    /**
     * Returns the API key resolved from BuildConfig (secrets.properties at build time).
     * Logs a warning if it's still the placeholder, so the developer knows to fill it in.
     */
    fun getApiKey(): String {
        val key = BuildConfig.PRESAGE_API_KEY
        if (key.isBlank() || key == "PLACEHOLDER_RUN_SETUP") {
            Log.w(TAG, "PRESAGE_API_KEY is placeholder — set a real key in secrets.properties.")
        } else {
            Log.d(TAG, "PRESAGE_API_KEY resolved (${key.take(4)}…${key.takeLast(4)}).")
        }
        return key
    }

    /**
     * Configures SmartSpectraSdk.shared with the API key and requested metrics.
     * Call this once, before sdk.start() or sdk.useCustomInput(), typically from
     * Application.onCreate() or the Activity's onCreate().
     *
     * The SDK stores singleton state; calling this again simply refreshes config values.
     *
     * Note: We use the custom input (headless) mode — the SDK's built-in camera is NOT
     * used. CameraX provides the preview and ImageAnalysis, and frames are forwarded
     * to SmartSpectra via useCustomInput(). This avoids camera device lock contention
     * between SmartSpectra and MediaPipe.
     */
    fun initializeSdk(context: Context) {
        val config = SmartSpectraConfig().apply {
            // Inject credentials from the build-time secret
            apiKey = getApiKey()

            // Disable raw bitmap output — we render the CameraX PreviewView directly
            imageOutputEnabled = false

            // Request the full cardio + breathing + expression metric bundle
            requestedMetrics =
                SmartSpectraConfig.cardioMetrics +
                SmartSpectraConfig.breathingMetrics +
                listOf(MetricType.EXPRESSIONS)
        }

        // Initialize the shared SDK singleton with our config
        SmartSpectraSdk.initialize(context, config)

        Log.i(TAG, "SmartSpectraSdk initialized (cardio + breathing + expressions, custom input mode).")
    }
}
