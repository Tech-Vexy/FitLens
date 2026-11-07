package com.fitlens.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fitlens.app.data.dao.WorkoutDao
import com.fitlens.app.data.model.BiomechanicalFaultEntity
import com.fitlens.app.data.model.VitalsLogEntity
import com.fitlens.app.data.model.WorkoutSessionEntity

/**
 * Local SQLite database for FitLens health, vitals, and workout telemetry.
 * All tables are stored 100% on-device. Zero health data leaves the local SQLite instance.
 */
@Database(
    entities = [
        WorkoutSessionEntity::class,
        VitalsLogEntity::class,
        BiomechanicalFaultEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: WorkoutDatabase? = null

        fun getDatabase(context: Context): WorkoutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java,
                    "fitlens_health_sqlite.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
