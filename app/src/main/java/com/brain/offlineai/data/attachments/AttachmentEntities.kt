package com.brain.offlineai.data.attachments

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 10 - one real row per attachment actually sent with a chat
 * message. Deliberately its own Room database (`brain_chat_attachments.db`,
 * see [AttachmentDatabase]) rather than a new column bolted onto the
 * existing Phase 7 `chat_history_messages` table: this project's history
 * DB has no migration path defined (fresh `Room.databaseBuilder` with no
 * `addMigrations`/`fallbackToDestructiveMigration`), so altering that
 * table's schema would crash on every existing install that already has a
 * `brain_chat_history.db` on disk. A brand-new database file needs no
 * migration and touches zero existing rows or files - the same
 * separate-database-per-concern shape this project already uses for
 * `ApiKeyDatabase` vs `ChatHistoryDatabase`.
 */
@Entity(tableName = "chat_attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val messageId: Long,
    val fileName: String,
    val mimeType: String?,
    val kind: String,
    val sizeBytes: Long,
    val storedPath: String,
    val createdAt: Long
)
