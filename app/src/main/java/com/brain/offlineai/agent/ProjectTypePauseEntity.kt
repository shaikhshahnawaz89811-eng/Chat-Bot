package com.brain.offlineai.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 24 - one real, persisted row per paused "which platform/language"
 * question (see [ProjectTypeGate]). Deliberately its own Room database
 * ([ProjectTypePauseDatabase], own file) rather than a new column on
 * [AgentTaskEntity] - same migration-less-schema reasoning every other
 * per-concern database in this project already documents ([AgentTaskEntity]
 * itself, [com.brain.offlineai.data.attachments.AttachmentEntity], etc.): a
 * brand-new file needs no migration and touches zero existing rows.
 * [status] reuses the real, generic [AgentTaskStatus] names
 * (AWAITING_CLARIFICATION/RESUMED/ABANDONED) as a plain string, same
 * pattern [AgentTaskEntity.status] already uses.
 */
@Entity(tableName = "project_type_pauses")
data class ProjectTypePauseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val originalRequest: String,
    val question: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
