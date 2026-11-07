package com.fitlens.app.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.fitlens.app.vitals.HeartRateZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fitlens_user_profile")

data class UserProfile(
    val name: String = "Athlete",
    val age: Int = 28,
    val restingHeartRate: Double = 65.0,
    val maxHeartRate: Double = 190.0,
    val primaryGoal: String = "Hypertrophy & Biomechanics",
    val experienceLevel: String = "Intermediate"
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
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_NAME = stringPreferencesKey("user_name")
        val KEY_AGE = intPreferencesKey("user_age")
        val KEY_RESTING_HR = doublePreferencesKey("resting_hr")
        val KEY_MAX_HR = doublePreferencesKey("max_hr")
        val KEY_GOAL = stringPreferencesKey("primary_goal")
        val KEY_EXPERIENCE = stringPreferencesKey("experience_level")
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
            experienceLevel = prefs[KEY_EXPERIENCE] ?: "Intermediate"
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
        }
    }
}
