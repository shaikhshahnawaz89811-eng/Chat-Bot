package com.brain.offlineai.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Real, plain (unencrypted) Room database for agent task-state rows - same
 * "not a live credential, no SQLCipher needed" reasoning [com.brain.offlineai.data.history.ChatHistoryDatabase]
 * already documents (Rule 20). Singleton via double-checked locking, same
 * shape as every other database in this project.
 */
@Database(entities = [AgentTaskEntity::class], version = 1, exportSchema = false)
abstract class AgentTaskDatabase : RoomDatabase() {

    abstract fun agentTaskDao(): AgentTaskDao

    companion object {
        @Volatile private var instance: AgentTaskDatabase? = null

        fun getInstance(context: Context): AgentTaskDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentTaskDatabase::class.java,
                    "brain_agent_tasks.db"
                ).build().also { instance = it }
            }
    }
}
