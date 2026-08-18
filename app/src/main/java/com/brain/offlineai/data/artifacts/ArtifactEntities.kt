package com.brain.offlineai.data.artifacts

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 11 - one real row per artifact file this app actually generated and
 * wrote to disk for a completed bot message. Its own Room database
 * (`brain_chat_artifacts.db`, see [ArtifactDatabase]) rather than a column
 * on the existing Phase 7 `chat_history_messages` table - same
 * migration-less-schema reasoning [com.brain.offlineai.data.attachments.AttachmentEntity]
 * already documents (that table's `Room.databaseBuilder` has no migration
 * path, so a brand-new database file is the only change that touches zero
 * existing rows).
 */
@Entity(tableName = "chat_artifacts")
data class ArtifactEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val messageId: Long,
    val fileName: String,
    val mimeType: String,
    val kind: String,
    val sizeBytes: Long,
    val storedPath: String,
    val createdAt: Long
)
