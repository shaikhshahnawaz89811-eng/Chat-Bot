package com.brain.offlineai.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AgentExecutionEntity::class], version = 1, exportSchema = false)
abstract class AgentExecutionDatabase : RoomDatabase() {
    abstract fun dao(): AgentExecutionDao

    companion object {
        @Volatile private var instance: AgentExecutionDatabase? = null

        fun getInstance(context: Context): AgentExecutionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentExecutionDatabase::class.java,
                    "brain_agent_executions.db"
                ).build().also { instance = it }
            }
    }
}
