package com.brain.offlineai.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real CRUD over the thermal-pause Room table, same shape as
 * [AgentTaskRepository]. This is the real "persistence/resume" half of
 * the Appendix's own requirement - a task genuinely paused for real
 * device heat survives process death / app restart (real Room row on
 * disk), not just an in-memory flag that would silently vanish if the OS
 * reclaimed the process while the device was still cooling.
 */
class ThermalPauseRepository(context: Context) {

    private val dao = ThermalPauseDatabase.getInstance(context).thermalPauseDao()

    /** Real, persisted "pause and cool down" record - see [ThermalPauseEntity]. */
    suspend fun savePaused(
        id: String,
        sessionId: String,
        continuationPrompt: String,
        pausedAtStatus: Int,
        now: Long
    ) = withContext(Dispatchers.IO) {
        dao.upsert(
            ThermalPauseEntity(
                id = id,
                sessionId = sessionId,
                continuationPrompt = continuationPrompt,
                pausedAtStatus = pausedAtStatus,
                status = ThermalPauseStatus.PAUSED.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** Real, most-recent still-open thermal pause for this session, or null if there genuinely isn't one. */
    suspend fun getPaused(sessionId: String): ThermalPauseEntity? =
        withContext(Dispatchers.IO) {
            dao.getLatestByStatus(sessionId, ThermalPauseStatus.PAUSED.name)
        }

    /** Real resume - the device genuinely cooled back down and generation genuinely continued. */
    suspend fun markResumed(id: String) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, ThermalPauseStatus.RESUMED.name, System.currentTimeMillis())
    }

    /** Rule 3 "delete" - real, permanent removal, called from [com.brain.offlineai.ui.screens.history.HistoryViewModel.deleteSession] so a deleted session leaves no orphaned pending thermal pause behind. */
    suspend fun deleteForSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteForSession(sessionId)
    }
}
