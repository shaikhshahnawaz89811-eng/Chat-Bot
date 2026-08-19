package com.brain.offlineai.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real CRUD over the agent-audit Room table, same shape as
 * [AgentTaskRepository]. This is the real, persisted half of Phase 21's
 * Permission/Risk Gate - every genuine HIGH-risk tool call [ToolGateway]
 * makes gets one real row here, so it's an actual, queryable record, not
 * just a claim shown once and forgotten.
 */
class AgentAuditRepository(context: Context) {

    private val dao = AgentAuditDatabase.getInstance(context).agentAuditDao()

    suspend fun record(
        sessionId: String,
        tool: AgentTool,
        target: String,
        outcome: Boolean,
        detail: String
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            AgentAuditEntity(
                id = java.util.UUID.randomUUID().toString(),
                sessionId = sessionId,
                tool = tool.name,
                risk = tool.risk.name,
                target = target,
                outcome = if (outcome) "SUCCESS" else "FAILED",
                detail = detail,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getForSession(sessionId: String): List<AgentAuditEntity> =
        withContext(Dispatchers.IO) { dao.getForSession(sessionId) }

    /** Rule 3 "delete" - real, permanent removal, called from [com.brain.offlineai.ui.screens.history.HistoryViewModel.deleteSession] so a deleted session leaves no orphaned audit rows behind. */
    suspend fun deleteForSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteForSession(sessionId)
    }
}
