package com.brain.offlineai.agent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Separate concern database so this new checkpoint store does not alter existing Room schemas. */
@Database(entities = [ExecutionCheckpointEntity::class], version = 1, exportSchema = false)
abstract class ExecutionCheckpointDatabase : RoomDatabase() {
    abstract fun dao(): ExecutionCheckpointDao

    companion object {
        @Volatile private var instance: ExecutionCheckpointDatabase? = null
        fun getInstance(context: Context): ExecutionCheckpointDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExecutionCheckpointDatabase::class.java,
                    "brain_execution_checkpoints.db"
                ).build().also { instance = it }
            }
    }
}
