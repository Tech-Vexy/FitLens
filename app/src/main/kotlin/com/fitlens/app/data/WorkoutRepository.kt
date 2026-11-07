package com.fitlens.app.data

import com.fitlens.app.data.dao.WorkoutDao
import com.fitlens.app.data.model.BiomechanicalFaultEntity
import com.fitlens.app.data.model.VitalsLogEntity
import com.fitlens.app.data.model.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * WorkoutRepository manages local SQLite health and biomechanical persistence.
 * Strict local-only architecture guarantees that health vitals never leave the device.
 */
class WorkoutRepository(private val workoutDao: WorkoutDao) {

    val allSessions: Flow<List<WorkoutSessionEntity>> = workoutDao.getAllSessions()

    suspend fun saveSession(session: WorkoutSessionEntity): Long {
        return workoutDao.insertSession(session)
    }

    suspend fun getRecentSessions(limit: Int = 10): List<WorkoutSessionEntity> {
        return workoutDao.getRecentSessions(limit)
    }

    suspend fun getAverageAccuracy(): Float {
        return workoutDao.getOverallAverageAccuracy() ?: 100f
    }

    suspend fun getTotalWorkouts(): Int {
        return workoutDao.getTotalWorkoutCount()
    }

    // --- Local SQLite Vitals Time-Series ---
    suspend fun recordVitalsTelemetry(vitalsList: List<VitalsLogEntity>) {
        if (vitalsList.isNotEmpty()) {
            workoutDao.insertVitalsLogs(vitalsList)
        }
    }

    suspend fun getSessionVitals(sessionId: Long): List<VitalsLogEntity> {
        return workoutDao.getVitalsForSession(sessionId)
    }

    // --- Local SQLite Biomechanical Faults ---
    suspend fun recordBiomechanicalFaults(faults: List<BiomechanicalFaultEntity>) {
        if (faults.isNotEmpty()) {
            workoutDao.insertBiomechanicalFaults(faults)
        }
    }

    suspend fun getSessionFaults(sessionId: Long): List<BiomechanicalFaultEntity> {
        return workoutDao.getFaultsForSession(sessionId)
    }
}
