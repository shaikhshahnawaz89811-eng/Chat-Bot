package com.brain.offlineai.data.attachments

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Real, plain (unencrypted) Room database for attachment metadata - same
 * "not a live credential, no SQLCipher needed" reasoning [ChatHistoryDatabase]
 * already documents for chat transcripts (Rule 20). Singleton via
 * double-checked locking, same shape as `ApiKeyDatabase`/`ChatHistoryDatabase`.
 */
@Database(entities = [AttachmentEntity::class], version = 1, exportSchema = false)
abstract class AttachmentDatabase : RoomDatabase() {

    abstract fun attachmentDao(): AttachmentDao

    companion object {
        @Volatile private var instance: AttachmentDatabase? = null

        fun getInstance(context: Context): AttachmentDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AttachmentDatabase::class.java,
                    "brain_chat_attachments.db"
                ).build().also { instance = it }
            }
    }
}
