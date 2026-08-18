package com.brain.offlineai.data.artifacts

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Real, plain (unencrypted) Room database for artifact metadata - not a
 * secret, same reasoning [com.brain.offlineai.data.attachments.AttachmentDatabase]
 * already documents. Singleton via double-checked locking, same shape as
 * every other `*Database` in this project.
 */
@Database(entities = [ArtifactEntity::class], version = 1, exportSchema = false)
abstract class ArtifactDatabase : RoomDatabase() {

    abstract fun artifactDao(): ArtifactDao

    companion object {
        @Volatile private var instance: ArtifactDatabase? = null

        fun getInstance(context: Context): ArtifactDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArtifactDatabase::class.java,
                    "brain_chat_artifacts.db"
                ).build().also { instance = it }
            }
    }
}
