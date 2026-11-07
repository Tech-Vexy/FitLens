package com.fitlens.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitlens.app.data.model.BiomechanicalFaultEntity
import com.fitlens.app.data.model.VitalsLogEntity
import com.fitlens.app.data.model.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Local SQLite Health Vault.
 * All health vitals, joint metrics, and workout logs are operated strictly on-device.
 */
@Dao
interface WorkoutDao {

    // --- Workout Sessions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<WorkoutSessionEntity>

    @Query("SELECT AVG(formAccuracyPercentage) FROM workout_sessions")
    suspend fun getOverallAverageAccuracy(): Float?

    @Query("SELECT COUNT(*) FROM workout_sessions")
    suspend fun getTotalWorkoutCount(): Int

    // --- Local SQLite Physiological Vitals Time-Series ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVitalsLog(vitalsLog: VitalsLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVitalsLogs(vitalsLogs: List<VitalsLogEntity>): List<Long>

    @Query("SELECT * FROM local_vitals_telemetry WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getVitalsForSession(sessionId: Long): List<VitalsLogEntity>

    // --- Local SQLite Biomechanical Form Deviations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBiomechanicalFault(fault: BiomechanicalFaultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBiomechanicalFaults(faults: List<BiomechanicalFaultEntity>): List<Long>

    @Query("SELECT * FROM local_biomechanical_faults WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getFaultsForSession(sessionId: Long): List<BiomechanicalFaultEntity>

    @Query("DELETE FROM local_vitals_telemetry")
    suspend fun clearAllLocalVitals(): Int

    @Query("DELETE FROM local_biomechanical_faults")
    suspend fun clearAllLocalFaults(): Int
}
