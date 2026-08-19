package com.brain.offlineai.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Real, plain (unencrypted) Room database for thermal-pause task-state
 * rows - same "not a live credential, no SQLCipher needed" reasoning
 * [AgentTaskDatabase] already documents (Rule 20). Singleton via double-
 * checked locking, same shape as every other database in this project.
 */
@Database(entities = [ThermalPauseEntity::class], version = 1, exportSchema = false)
abstract class ThermalPauseDatabase : RoomDatabase() {

    abstract fun thermalPauseDao(): ThermalPauseDao

    companion object {
        @Volatile private var instance: ThermalPauseDatabase? = null

        fun getInstance(context: Context): ThermalPauseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThermalPauseDatabase::class.java,
                    "brain_thermal_pauses.db"
                ).build().also { instance = it }
            }
    }
}
