package com.fitlens.app.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.fitlens.app.vitals.HeartRateZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fitlens_user_profile")

data class UserProfile(
    val name: String = "Athlete",
    val age: Int = 28,
    val restingHeartRate: Double = 65.0,
    val maxHeartRate: Double = 190.0,
    val primaryGoal: String = "Hypertrophy & Biomechanics",
    val experienceLevel: String = "Intermediate",
    val hasAcceptedSensingConsent: Boolean = false,
    val smartSpectraTelemetryEnabled: Boolean = true,
    val preferLocalEdgeAi: Boolean = false
) {
    /**
     * Calculates personalized heart rate zones using the Karvonen Heart Rate Reserve (HRR) formula:
     * Target HR = ((Max HR - Resting HR) * %Intensity) + Resting HR
     */
    fun getHeartRateZone(currentBpm: Double): HeartRateZone {
        val hrr = maxHeartRate - restingHeartRate
        val intensity = (currentBpm - restingHeartRate) / maxOf(1.0, hrr)

        return when {
            intensity < 0.50 -> HeartRateZone.RESTING
            intensity < 0.65 -> HeartRateZone.WARMUP
            intensity < 0.80 -> HeartRateZone.AEROBIC
            intensity < 0.90 -> HeartRateZone.ANAEROBIC
            else -> HeartRateZone.MAX
        }
    }

    /**
     * Computes personalized BPM ranges for each Karvonen HRR zone for UI display.
     */
    fun getKarvonenZoneThresholds(): List<Pair<HeartRateZone, IntRange>> {
        val hrr = maxOf(1.0, maxHeartRate - restingHeartRate)
        fun bpmAt(pct: Double): Int = (restingHeartRate + hrr * pct).roundToInt()

        val z1Start = bpmAt(0.50)
        val z2Start = bpmAt(0.65)
        val z3Start = bpmAt(0.80)
        val z4Start = bpmAt(0.90)
        val maxInt = maxHeartRate.roundToInt()

        return listOf(
            HeartRateZone.RESTING to (restingHeartRate.roundToInt() until z1Start),
            HeartRateZone.WARMUP to (z1Start until z2Start),
            HeartRateZone.AEROBIC to (z2Start until z3Start),
            HeartRateZone.ANAEROBIC to (z3Start until z4Start),
            HeartRateZone.MAX to (z4Start..maxOf(z4Start, maxInt))
        )
    }
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_NAME = stringPreferencesKey("user_name")
        val KEY_AGE = intPreferencesKey("user_age")
        val KEY_RESTING_HR = doublePreferencesKey("resting_hr")
        val KEY_MAX_HR = doublePreferencesKey("max_hr")
        val KEY_GOAL = stringPreferencesKey("primary_goal")
        val KEY_EXPERIENCE = stringPreferencesKey("experience_level")
        val KEY_SENSING_CONSENT = booleanPreferencesKey("has_accepted_sensing_consent")
        val KEY_SMARTSPECTRA_TELEMETRY = booleanPreferencesKey("smartspectra_telemetry_enabled")
        val KEY_PREFER_LOCAL_EDGE_AI = booleanPreferencesKey("prefer_local_edge_ai")
    }

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        val age = prefs[KEY_AGE] ?: 28
        // Tanaka formula fallback: 208 - (0.7 * age)
        val defaultMaxHr = 208.0 - (0.7 * age)

        UserProfile(
            name = prefs[KEY_NAME] ?: "Athlete",
            age = age,
            restingHeartRate = prefs[KEY_RESTING_HR] ?: 65.0,
            maxHeartRate = prefs[KEY_MAX_HR] ?: defaultMaxHr,
            primaryGoal = prefs[KEY_GOAL] ?: "Hypertrophy & Biomechanics",
            experienceLevel = prefs[KEY_EXPERIENCE] ?: "Intermediate",
            hasAcceptedSensingConsent = prefs[KEY_SENSING_CONSENT] ?: false,
            smartSpectraTelemetryEnabled = prefs[KEY_SMARTSPECTRA_TELEMETRY] ?: true,
            preferLocalEdgeAi = prefs[KEY_PREFER_LOCAL_EDGE_AI] ?: false
        )
    }

    suspend fun updateProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NAME] = profile.name
            prefs[KEY_AGE] = profile.age
            prefs[KEY_RESTING_HR] = profile.restingHeartRate
            prefs[KEY_MAX_HR] = profile.maxHeartRate
            prefs[KEY_GOAL] = profile.primaryGoal
            prefs[KEY_EXPERIENCE] = profile.experienceLevel
            prefs[KEY_SENSING_CONSENT] = profile.hasAcceptedSensingConsent
            prefs[KEY_SMARTSPECTRA_TELEMETRY] = profile.smartSpectraTelemetryEnabled
            prefs[KEY_PREFER_LOCAL_EDGE_AI] = profile.preferLocalEdgeAi
        }
    }

    suspend fun setSensingConsent(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SENSING_CONSENT] = accepted
        }
    }
}
