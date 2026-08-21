package com.brain.offlineai.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentExecutionRepository(context: Context) {
    private val dao = AgentExecutionDatabase.getInstance(context).dao()

    suspend fun start(
        id: String,
        sessionId: String,
        kind: String,
        originalPrompt: String,
        continuationPrompt: String,
        currentFileName: String = "",
        currentChunk: Int = 0,
        totalChunks: Int = 0,
        currentFileIndex: Int = 0,
        planJson: String = ""
    ) = update(
        AgentExecutionEntity(
            id = id,
            sessionId = sessionId,
            kind = kind,
            status = AgentExecutionStatus.RUNNING.name,
            originalPrompt = originalPrompt,
            continuationPrompt = continuationPrompt,
            currentFileName = currentFileName,
            currentChunk = currentChunk,
            totalChunks = totalChunks,
            currentFileIndex = currentFileIndex,
            planJson = planJson,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    )

    suspend fun updateCursor(
        id: String,
        continuationPrompt: String,
        currentFileName: String = "",
        currentChunk: Int = 0,
        totalChunks: Int = 0,
        currentFileIndex: Int? = null,
        planJson: String? = null,
        status: AgentExecutionStatus = AgentExecutionStatus.RUNNING
    ) = withContext(Dispatchers.IO) {
        val base = dao.getById(id) ?: return@withContext
        dao.upsert(base.copy(
            status = status.name,
            continuationPrompt = continuationPrompt,
            currentFileName = currentFileName,
            currentChunk = currentChunk,
            totalChunks = totalChunks,
            currentFileIndex = currentFileIndex ?: base.currentFileIndex,
            planJson = planJson ?: base.planJson,
            updatedAt = System.currentTimeMillis()
        ))
    }


    suspend fun configurePlan(
        id: String,
        kind: String,
        planJson: String,
        currentFileIndex: Int = 0,
        currentFileName: String = "",
        totalChunks: Int = 0
    ) = withContext(Dispatchers.IO) {
        val base = dao.getById(id) ?: return@withContext
        dao.upsert(base.copy(
            kind = kind,
            planJson = planJson,
            currentFileIndex = currentFileIndex,
            currentFileName = currentFileName,
            currentChunk = 0,
            totalChunks = totalChunks,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun mark(id: String, status: AgentExecutionStatus, continuationPrompt: String? = null) = withContext(Dispatchers.IO) {
        val current = dao.getById(id)
        if (current != null) {
            dao.upsert(current.copy(
                status = status.name,
                continuationPrompt = continuationPrompt ?: current.continuationPrompt,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    suspend fun get(id: String): AgentExecutionEntity? = withContext(Dispatchers.IO) { dao.getById(id) }

    suspend fun latestPaused(sessionId: String): AgentExecutionEntity? = withContext(Dispatchers.IO) {
        dao.latest(sessionId, AgentExecutionStatus.PAUSED.name)
    }

    suspend fun latestPausedAny(): AgentExecutionEntity? = withContext(Dispatchers.IO) {
        dao.latestAny(AgentExecutionStatus.PAUSED.name)
    }

    suspend fun deleteForSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteForSession(sessionId)
    }

    private suspend fun update(entity: AgentExecutionEntity) = withContext(Dispatchers.IO) {
        dao.upsert(entity)
    }
}
