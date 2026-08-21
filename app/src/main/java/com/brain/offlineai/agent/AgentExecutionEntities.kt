package com.brain.offlineai.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persistent execution cursor for a user-started long-running chat task. */
@Entity(tableName = "agent_executions")
data class AgentExecutionEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val kind: String,
    val status: String,
    val originalPrompt: String,
    val continuationPrompt: String,
    val currentFileName: String,
    val currentChunk: Int,
    val totalChunks: Int,
    val createdAt: Long,
    val updatedAt: Long
)
