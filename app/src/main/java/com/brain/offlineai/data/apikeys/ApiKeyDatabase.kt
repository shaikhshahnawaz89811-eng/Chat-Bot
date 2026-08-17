package com.brain.offlineai.data.apikeys

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Real SQLCipher-encrypted Room database - `openHelperFactory(SupportFactory(...))`
 * is what actually makes the .db file on disk AES-256 encrypted, not just
 * the app-private-storage sandboxing every Android app already gets for
 * free. Singleton via double-checked locking (standard Room pattern, same
 * shape as other single-owner state in this codebase like BrainEngine).
 */
@Database(entities = [ApiKeyEntity::class], version = 1, exportSchema = false)
abstract class ApiKeyDatabase : RoomDatabase() {

    abstract fun apiKeyDao(): ApiKeyDao

    companion object {
        @Volatile private var instance: ApiKeyDatabase? = null

        fun getInstance(context: Context): ApiKeyDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): ApiKeyDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyProvider(context).getOrCreatePassphrase()
            val factory = SupportOpenHelperFactory(passphrase.concatToString().toByteArray(Charsets.UTF_8))
            return Room.databaseBuilder(context, ApiKeyDatabase::class.java, "brain_api_keys.db")
                .openHelperFactory(factory)
                .build()
        }
    }
}
