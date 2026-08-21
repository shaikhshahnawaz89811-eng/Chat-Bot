package com.brain.offlineai.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persisted execution state for work that can legitimately outlive a ViewModel/process. */
@Entity(tableName = "execution_checkpoints")
data class ExecutionCheckpointEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val kind: String,
    val status: String,
    val originalRequest: String,
    val extraContext: String,
    val continuationPrompt: String,
    val planJson: String,
    val currentFileIndex: Int,
    val projectDirId: String,
    val planMessageId: Long,
    val currentFileName: String,
    val partialOutput: String,
    val createdAt: Long,
    val updatedAt: Long
)
