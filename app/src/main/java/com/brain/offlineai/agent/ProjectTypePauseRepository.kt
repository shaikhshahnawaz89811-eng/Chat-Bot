package com.brain.offlineai.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real CRUD over the project-type-pause Room table, same shape as
 * [AgentTaskRepository]. Gives the [ProjectTypeGate] question a real,
 * persisted "stop and ask, then resume" record that survives process
 * death - not just an in-memory flag.
 */
class ProjectTypePauseRepository(context: Context) {

    private val dao = ProjectTypePauseDatabase.getInstance(context).projectTypePauseDao()

    suspend fun saveAwaiting(id: String, sessionId: String, originalRequest: String, question: String, now: Long) =
        withContext(Dispatchers.IO) {
            dao.upsert(
                ProjectTypePauseEntity(
                    id = id,
                    sessionId = sessionId,
                    originalRequest = originalRequest,
                    question = question,
                    status = AgentTaskStatus.AWAITING_CLARIFICATION.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

    /** Real, most-recent still-open question for this session, or null if there genuinely isn't one. */
    suspend fun getAwaiting(sessionId: String): ProjectTypePauseEntity? =
        withContext(Dispatchers.IO) {
            dao.getLatestByStatus(sessionId, AgentTaskStatus.AWAITING_CLARIFICATION.name)
        }

    /** Real resume - the user's next message genuinely named a real platform/language. */
    suspend fun markResumed(id: String) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, AgentTaskStatus.RESUMED.name, System.currentTimeMillis())
    }

    /** Real counterpart to [markResumed] - the next message did not name a real platform, same "genuinely moved on" reasoning [AgentTaskStatus.ABANDONED] already documents. */
    suspend fun markAbandoned(id: String) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, AgentTaskStatus.ABANDONED.name, System.currentTimeMillis())
    }

    /** Rule 3 "delete" - real, permanent removal, called from [com.brain.offlineai.ui.screens.history.HistoryViewModel.deleteSession]. */
    suspend fun deleteForSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteForSession(sessionId)
    }
}
