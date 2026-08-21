package com.brain.offlineai.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExecutionCheckpointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: ExecutionCheckpointEntity)

    @Query("SELECT * FROM execution_checkpoints WHERE sessionId = :sessionId AND status = 'PAUSED' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getPaused(sessionId: String): ExecutionCheckpointEntity?

    @Query("UPDATE execution_checkpoints SET status = :status, continuationPrompt = :continuationPrompt, currentFileIndex = :currentFileIndex, currentFileName = :currentFileName, partialOutput = :partialOutput, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, continuationPrompt: String, currentFileIndex: Int, currentFileName: String, partialOutput: String, updatedAt: Long)

    @Query("DELETE FROM execution_checkpoints WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM execution_checkpoints WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
