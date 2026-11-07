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
     */
    fun initializeSdk(context: Context) {
        val sdk = SmartSpectraSdk.shared

        // Inject credentials from the build-time secret
        sdk.config.apiKey = getApiKey()

        // Disable raw bitmap output — we render the CameraX PreviewView directly
        sdk.config.imageOutputEnabled = false

        // Request the full cardio + breathing + expression metric bundle
        sdk.config.requestedMetrics =
            SmartSpectraConfig.cardioMetrics +
            SmartSpectraConfig.breathingMetrics +
            listOf(MetricType.EXPRESSIONS)

        Log.i(TAG, "SmartSpectraSdk configured (cardio + breathing + expressions).")
    }
}
