package com.brain.offlineai.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real CRUD over the agent-task Room table, same shape as
 * [com.brain.offlineai.data.attachments.AttachmentRepository]. This is the
 * real "persistence/resume" half of Master Plan Foundation-order step 3 -
 * a paused task genuinely survives process death / app restart (real Room
 * row on disk), not just an in-memory flag that would silently vanish.
 */
class AgentTaskRepository(context: Context) {

    private val dao = AgentTaskDatabase.getInstance(context).agentTaskDao()

    /** Real, persisted "stop and ask" record - see [AgentClarificationGate]. */
    suspend fun saveAwaitingClarification(
        id: String,
        sessionId: String,
        kind: String,
        question: String,
        attachmentId: String,
        storedPath: String,
        displayName: String,
        now: Long
    ) = withContext(Dispatchers.IO) {
        dao.upsert(
            AgentTaskEntity(
                id = id,
                sessionId = sessionId,
                kind = kind,
                status = AgentTaskStatus.AWAITING_CLARIFICATION.name,
                question = question,
                resumeAttachmentId = attachmentId,
                resumeStoredPath = storedPath,
                resumeDisplayName = displayName,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** Real, most-recent still-open clarification for this session, or null if there genuinely isn't one. */
    suspend fun getAwaitingClarification(sessionId: String): AgentTaskEntity? =
        withContext(Dispatchers.IO) {
            dao.getLatestByStatus(sessionId, AgentTaskStatus.AWAITING_CLARIFICATION.name)
        }

    /** Real resume - the user's next message genuinely resolved the question. */
    suspend fun markResumed(id: String) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, AgentTaskStatus.RESUMED.name, System.currentTimeMillis())
    }

    /**
     * Bug fix (user request) - real counterpart to [markResumed]: the
     * user's next message genuinely did NOT resolve this question, so it
     * is dropped instead of silently staying AWAITING_CLARIFICATION
     * forever - see [AgentTaskStatus.ABANDONED]'s own doc.
     */
    suspend fun markAbandoned(id: String) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, AgentTaskStatus.ABANDONED.name, System.currentTimeMillis())
    }

    /** Rule 3 "delete" - real, permanent removal, called from [com.brain.offlineai.ui.screens.history.HistoryViewModel.deleteSession] so a deleted session leaves no orphaned pending task behind. */
    suspend fun deleteForSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteForSession(sessionId)
    }
}
