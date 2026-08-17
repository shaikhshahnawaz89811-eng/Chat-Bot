package com.brain.offlineai.data.apikeys

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row in the SQLCipher-encrypted `api_keys` table. `keyValue` is the
 * real secret (not a hash) - Key Details (mockup screen 7) needs to reveal
 * the full key again on demand, so the encryption-at-rest of the whole DB
 * file (via SQLCipher) is what protects it, the same tradeoff any local
 * secrets manager with a "reveal" feature makes.
 */
@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val keyValue: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val lastUsedAt: Long?,
    val revokedAt: Long?
)

enum class KeyStatus { ACTIVE, EXPIRED, REVOKED }

/**
 * Status is computed at read time from real timestamps rather than stored
 * as its own column - a stored "EXPIRED" flag would go stale the instant
 * time passes without anyone writing to the row, which is exactly the kind
 * of silently-wrong state this project's rules avoid.
 */
fun ApiKeyEntity.statusAt(now: Long = System.currentTimeMillis()): KeyStatus = when {
    revokedAt != null -> KeyStatus.REVOKED
    expiresAt != null && expiresAt <= now -> KeyStatus.EXPIRED
    else -> KeyStatus.ACTIVE
}
