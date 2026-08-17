package com.brain.offlineai.data.apikeys

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Real CRUD over the SQLCipher-encrypted api_keys table - every write here
 * is a genuine DB operation, nothing held only in memory, so keys survive
 * process death exactly like the local API server (Phase 4) will need.
 *
 * Applies the full Rule 3 name-ops set from PROGRESS.md to key names:
 * create, rename, delete, read (all below) plus active-pointer - the same
 * "which one is current" pattern ModelFileManager already uses for the
 * installed model path (KEY_LAST_MODEL_PATH), applied here to the
 * most-recently-generated key so the Create -> Details flow always knows
 * which row it just made without threading extra state through nav args.
 */
class ApiKeyRepository(context: Context) {

    private val dao = ApiKeyDatabase.getInstance(context).apiKeyDao()
    private val prefs = context.getSharedPreferences("brain_api_keys_prefs", Context.MODE_PRIVATE)

    fun observeKeys(): Flow<List<ApiKeyEntity>> = dao.observeAll()

    suspend fun getKey(id: String): ApiKeyEntity? = withContext(Dispatchers.IO) { dao.getById(id) }

    /** Phase 4: real auth lookup for the Local API Server - matches the raw secret from an incoming request. */
    suspend fun getKeyByValue(keyValue: String): ApiKeyEntity? = withContext(Dispatchers.IO) { dao.getByKeyValue(keyValue) }

    /** Rule 3 "create" - enforces the name-uniqueness a real key manager needs. */
    suspend fun createKey(name: String, expiration: ExpirationOption): Result<ApiKeyEntity> =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Key name can't be empty."))
            }
            if (dao.getByName(trimmed) != null) {
                return@withContext Result.failure(IllegalArgumentException("A key named \"$trimmed\" already exists."))
            }

            val now = System.currentTimeMillis()
            val entity = ApiKeyEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                keyValue = KeyGenerator.generate(),
                createdAt = now,
                expiresAt = expiration.expiresAtFrom(now),
                lastUsedAt = null,
                revokedAt = null
            )
            dao.insert(entity)
            setActivePointer(entity.id)
            Result.success(entity)
        }

    /** Rule 3 "rename" - same uniqueness check as create, excluding the row being renamed. */
    suspend fun renameKey(id: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Key name can't be empty."))
        }
        val existing = dao.getByName(trimmed)
        if (existing != null && existing.id != id) {
            return@withContext Result.failure(IllegalArgumentException("A key named \"$trimmed\" already exists."))
        }
        dao.rename(id, trimmed)
        Result.success(Unit)
    }

    /** Rule 3 "delete" - a real row removal, not a soft hide. */
    suspend fun deleteKey(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
        if (getActivePointer() == id) clearActivePointer()
    }

    suspend fun revokeKey(id: String) = withContext(Dispatchers.IO) {
        dao.revoke(id, System.currentTimeMillis())
    }

    suspend fun touchLastUsed(id: String) = withContext(Dispatchers.IO) {
        dao.touchLastUsed(id, System.currentTimeMillis())
    }

    /** Rule 3 "active-pointer". */
    fun getActivePointer(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    private fun setActivePointer(id: String) {
        prefs.edit { putString(KEY_ACTIVE_ID, id) }
    }

    private fun clearActivePointer() {
        prefs.edit { remove(KEY_ACTIVE_ID) }
    }

    companion object {
        private const val KEY_ACTIVE_ID = "active_key_id"
    }
}
