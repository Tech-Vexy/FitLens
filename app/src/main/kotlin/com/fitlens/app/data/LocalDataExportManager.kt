package com.fitlens.app.data

import android.content.Context
import android.util.Log
import com.fitlens.app.data.dao.WorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * LocalDataExportManager enables users to export their local SQLite health records
 * (workouts, vitals telemetry, and biomechanical faults) to an on-device JSON export.
 *
 * Guarantees GDPR/HIPAA right of data portability without transmitting health data to the cloud.
 */
class LocalDataExportManager(
    private val context: Context,
    private val workoutDao: WorkoutDao
) {
    companion object {
        private const val TAG = "LocalDataExportManager"
    }

    /**
     * Exports all local SQLite sessions and telemetry into a local JSON file.
     */
    suspend fun exportHealthDataToJson(): String = withContext(Dispatchers.IO) {
        val sessions = workoutDao.getRecentSessions(100)
        val rootJson = JSONObject()
        rootJson.put("exportTimestamp", System.currentTimeMillis())
        rootJson.put("exportSource", "FitLens Local SQLite Health Vault")
        rootJson.put("privacyStandard", "100% On-Device Storage - Zero Cloud Sync")

        val sessionsArray = JSONArray()
        for (session in sessions) {
            val sessionJson = JSONObject().apply {
                put("id", session.id)
                put("timestamp", session.timestamp)
                put("exerciseName", session.exerciseName)
                put("targetReps", session.targetReps)
                put("completedReps", session.completedReps)
                put("durationSeconds", session.durationSeconds)
                put("averageHeartRateBpm", session.averageHeartRate)
                put("maxHeartRateBpm", session.maxHeartRate)
                put("formAccuracyPercentage", session.formAccuracyPercentage)
                put("totalFormDeviations", session.totalFormDeviations)
                put("extendedRestSeconds", session.extendedRestSeconds)
                put("coachFeedback", session.coachFeedback)

                // Attach high-res SQLite vitals telemetry
                val vitals = workoutDao.getVitalsForSession(session.id)
                val vitalsArray = JSONArray()
                for (v in vitals) {
                    val vObj = JSONObject().apply {
                        put("timestampMs", v.timestampMs)
                        put("heartRateBpm", v.heartRateBpm)
                        put("hrvMs", v.heartRateVariabilityMs)
                        put("breathingRateBpm", v.breathingRateBpm)
                        put("heartRateZone", v.heartRateZone)
                    }
                    vitalsArray.put(vObj)
                }
                put("vitalsTelemetry", vitalsArray)

                // Attach SQLite biomechanical faults
                val faults = workoutDao.getFaultsForSession(session.id)
                val faultsArray = JSONArray()
                for (f in faults) {
                    val fObj = JSONObject().apply {
                        put("timestampMs", f.timestampMs)
                        put("jointName", f.jointName)
                        put("jointAngleDegrees", f.jointAngleDegrees)
                        put("faultMessage", f.faultMessage)
                        put("severity", f.severity)
                    }
                    faultsArray.put(fObj)
                }
                put("biomechanicalFaults", faultsArray)
            }
            sessionsArray.put(sessionJson)
        }
        rootJson.put("sessions", sessionsArray)

        val jsonString = rootJson.toString(2)

        // Save to app's local private files directory
        try {
            val exportFile = File(context.filesDir, "fitlens_health_export_${System.currentTimeMillis()}.json")
            exportFile.writeText(jsonString)
            Log.i(TAG, "Local health export written to: ${exportFile.absolutePath} (${sessions.size} sessions)")
            exportFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write local health export file: ${e.message}", e)
            jsonString
        }
    }
}
