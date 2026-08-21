package com.brain.offlineai.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AgentExecutionEntity::class], version = 2, exportSchema = false)
abstract class AgentExecutionDatabase : RoomDatabase() {
    abstract fun dao(): AgentExecutionDao

    companion object {
        @Volatile private var instance: AgentExecutionDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agent_executions ADD COLUMN currentFileIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE agent_executions ADD COLUMN planJson TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AgentExecutionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentExecutionDatabase::class.java,
                    "brain_agent_executions.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
