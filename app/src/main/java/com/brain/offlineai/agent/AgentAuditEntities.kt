package com.brain.offlineai.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 21 (Master Plan v2, section 9 step 7 - "Universal Rule/Permission/
 * Risk Gate") - one real, persisted row per genuine HIGH-risk tool call
 * [ToolGateway] actually made (a real ZIP-entry patch or a real new
 * artifact file write - see [AgentTool]). This is the real audit trail
 * the Master Plan's Risk Gate concept asks for: a genuine, on-disk record
 * of what changed, not just a claim made once in a chat bubble.
 *
 * Deliberately its own Room database ([AgentAuditDatabase], own file) -
 * same migration-less-schema reasoning every other per-concern database
 * in this project already documents ([AgentTaskEntity],
 * [com.brain.offlineai.data.attachments.AttachmentEntity],
 * [com.brain.offlineai.data.artifacts.ArtifactEntity]): a brand-new file
 * needs no migration and touches zero existing rows or files.
 */
@Entity(tableName = "agent_audit_log")
data class AgentAuditEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val tool: String,
    val risk: String,
    /** Real target of the action - an entry name inside a ZIP, or a written artifact's real file name. */
    val target: String,
    /** "SUCCESS" or "FAILED" - the real, observed outcome, never assumed. */
    val outcome: String,
    /** Real, human-readable detail (e.g. a real diff summary, or a real failure reason) - never fabricated. */
    val detail: String,
    val timestamp: Long
)
