package com.brain.offlineai.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Real, plain (unencrypted) Room database for chat transcripts.
 *
 * Deliberately NOT SQLCipher-wrapped like `ApiKeyDatabase` (Phase 3): Rule
 * 20 is "no unrelated payload/deps pulled in early" - the API Keys DB is
 * encrypted because a leaked row there is a live credential, but a chat
 * transcript isn't a secret in the same sense, and this DB doesn't need a
 * second SQLCipher passphrase/Keystore entry to earn its place. Singleton
 * via double-checked locking, same shape as `ApiKeyDatabase`.
 */
@Database(entities = [ChatSessionEntity::class, ChatHistoryMessageEntity::class], version = 1, exportSchema = false)
abstract class ChatHistoryDatabase : RoomDatabase() {

    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        @Volatile private var instance: ChatHistoryDatabase? = null

        fun getInstance(context: Context): ChatHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatHistoryDatabase::class.java,
                    "brain_chat_history.db"
                ).build().also { instance = it }
            }
    }
}
