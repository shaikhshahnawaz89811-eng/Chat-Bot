package com.brain.offlineai.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: AgentTaskEntity)

    /** Real, most-recent pending task for this session and status - used by [AgentTaskRepository.getAwaitingClarification]. */
    @Query("SELECT * FROM agent_tasks WHERE sessionId = :sessionId AND status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestByStatus(sessionId: String, status: String): AgentTaskEntity?

    @Query("UPDATE agent_tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("DELETE FROM agent_tasks WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
