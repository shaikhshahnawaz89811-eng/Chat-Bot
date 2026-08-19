package com.brain.offlineai.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 19 (Master Plan v2 - Foundation order step 3: "Build Task State +
 * persistence/resume") - one real, persisted row per paused agent task.
 * The only real producer right now is [AgentClarificationGate] pausing a
 * ZIP-edit request whose target file is genuinely ambiguous (2+ real
 * candidate files, see that class's own doc) - [kind] is a plain string
 * rather than a closed enum so a later phase's different task kind can
 * reuse this same table without a schema change.
 *
 * Deliberately its own Room database ([AgentTaskDatabase], own file) -
 * same migration-less-schema reasoning
 * [com.brain.offlineai.data.attachments.AttachmentEntity] already
 * documents for every other per-concern database this project has
 * (ApiKeyDatabase / ChatHistoryDatabase / AttachmentDatabase /
 * ArtifactDatabase): a brand-new file needs no migration and touches zero
 * existing rows or files.
 */
@Entity(tableName = "agent_tasks")
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val kind: String,
    val status: String,
    val question: String,
    val resumeAttachmentId: String,
    val resumeStoredPath: String,
    val resumeDisplayName: String,
    val createdAt: Long,
    val updatedAt: Long
)
