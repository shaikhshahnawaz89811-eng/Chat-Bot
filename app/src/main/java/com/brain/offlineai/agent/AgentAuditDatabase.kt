package com.brain.offlineai.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Real, plain (unencrypted) Room database for the agent tool-call audit
 * log - same "not a live credential, no SQLCipher needed" reasoning
 * [com.brain.offlineai.data.history.ChatHistoryDatabase]/[AgentTaskDatabase]
 * already document (Rule 20). Singleton via double-checked locking, same
 * shape as every other database in this project.
 */
@Database(entities = [AgentAuditEntity::class], version = 1, exportSchema = false)
abstract class AgentAuditDatabase : RoomDatabase() {

    abstract fun agentAuditDao(): AgentAuditDao

    companion object {
        @Volatile private var instance: AgentAuditDatabase? = null

        fun getInstance(context: Context): AgentAuditDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentAuditDatabase::class.java,
                    "brain_agent_audit.db"
                ).build().also { instance = it }
            }
    }
}
