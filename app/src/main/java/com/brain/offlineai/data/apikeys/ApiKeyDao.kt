package com.brain.offlineai.data.apikeys

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {

    @Query("SELECT * FROM api_keys ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE id = :id")
    suspend fun getById(id: String): ApiKeyEntity?

    @Query("SELECT * FROM api_keys WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ApiKeyEntity?

    // Phase 4: Local API Server auth needs a real lookup by the secret
    // itself (the Authorization: Bearer <key> header the Rani app sends),
    // not just by id/name.
    @Query("SELECT * FROM api_keys WHERE keyValue = :keyValue LIMIT 1")
    suspend fun getByKeyValue(keyValue: String): ApiKeyEntity?

    @Insert
    suspend fun insert(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE api_keys SET name = :newName WHERE id = :id")
    suspend fun rename(id: String, newName: String)

    @Query("UPDATE api_keys SET revokedAt = :timestamp WHERE id = :id")
    suspend fun revoke(id: String, timestamp: Long)

    @Query("UPDATE api_keys SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun touchLastUsed(id: String, timestamp: Long)
}
