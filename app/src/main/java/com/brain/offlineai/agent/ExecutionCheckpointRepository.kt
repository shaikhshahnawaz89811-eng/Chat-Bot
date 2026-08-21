package com.brain.offlineai.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExecutionCheckpointKind {
    const val REPLY = "reply"
    const val MULTI_FILE = "multi_file"
}

class ExecutionCheckpointRepository(context: Context) {
    private val dao = ExecutionCheckpointDatabase.getInstance(context).dao()

    suspend fun savePaused(
        id: String,
        sessionId: String,
        kind: String,
        originalRequest: String,
        extraContext: String,
        continuationPrompt: String,
        planJson: String = "",
        currentFileIndex: Int = 0,
        projectDirId: String = "",
        planMessageId: Long = 0L,
        currentFileName: String = "",
        partialOutput: String = ""
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.upsert(ExecutionCheckpointEntity(id, sessionId, kind, "PAUSED", originalRequest, extraContext,
            continuationPrompt, planJson, currentFileIndex, projectDirId, planMessageId, currentFileName,
            partialOutput.takeLast(120_000), now, now))
    }

    suspend fun updateProgress(
        id: String,
        continuationPrompt: String,
        currentFileIndex: Int,
        currentFileName: String,
        partialOutput: String = ""
    ) = withContext(Dispatchers.IO) {
        dao.updateProgress(id, "PAUSED", continuationPrompt, currentFileIndex, currentFileName,
            partialOutput.takeLast(120_000), System.currentTimeMillis())
    }

    suspend fun getPaused(sessionId: String): ExecutionCheckpointEntity? = withContext(Dispatchers.IO) { dao.getPaused(sessionId) }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }
    suspend fun deleteForSession(sessionId: String) = withContext(Dispatchers.IO) { dao.deleteForSession(sessionId) }
}
