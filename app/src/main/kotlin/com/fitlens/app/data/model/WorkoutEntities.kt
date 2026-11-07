package com.fitlens.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local SQLite table for completed workout sessions.
 * Stored 100% on-device in local SQLite (Room).
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val exerciseName: String,
    val targetReps: Int,
    val completedReps: Int,
    val durationSeconds: Int,
    val averageHeartRate: Double,
    val maxHeartRate: Double,
    val formAccuracyPercentage: Float,
    val totalFormDeviations: Int,
    val extendedRestSeconds: Int = 0,
    val coachFeedback: String
)

/**
 * Local SQLite table for high-resolution physiological vitals time-series.
 * Stores Presage rPPG readings strictly locally on device.
 *
 * ForeignKey to workout_sessions ensures orphaned vitals records are
 * automatically removed when their parent session is deleted.
 */
@Entity(
    tableName = "local_vitals_telemetry",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class VitalsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long = System.currentTimeMillis(),
    val heartRateBpm: Double,
    val heartRateVariabilityMs: Double,
    val breathingRateBpm: Double,
    val heartRateZone: String,
    val signalConfidence: Float
)

/**
 * Local SQLite table for biomechanical form deviation events.
 * Stores MediaPipe joint angles and form faults strictly locally on device.
 *
 * ForeignKey to workout_sessions ensures orphaned fault records are
 * automatically removed when their parent session is deleted.
 */
@Entity(
    tableName = "local_biomechanical_faults",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class BiomechanicalFaultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long = System.currentTimeMillis(),
    val exerciseName: String,
    val jointName: String,
    val jointAngleDegrees: Double,
    val faultMessage: String,
    val severity: String
)
