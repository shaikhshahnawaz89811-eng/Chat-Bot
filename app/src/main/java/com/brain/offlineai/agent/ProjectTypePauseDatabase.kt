package com.brain.offlineai.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Real, plain (unencrypted) Room database for paused project-type
 * questions - same "not a live credential, no SQLCipher needed" reasoning
 * [AgentTaskDatabase] already documents. Singleton via double-checked
 * locking, same shape as every other database in this project.
 */
@Database(entities = [ProjectTypePauseEntity::class], version = 1, exportSchema = false)
abstract class ProjectTypePauseDatabase : RoomDatabase() {

    abstract fun projectTypePauseDao(): ProjectTypePauseDao

    companion object {
        @Volatile private var instance: ProjectTypePauseDatabase? = null

        fun getInstance(context: Context): ProjectTypePauseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProjectTypePauseDatabase::class.java,
                    "brain_project_type_gate.db"
                ).build().also { instance = it }
            }
    }
}
