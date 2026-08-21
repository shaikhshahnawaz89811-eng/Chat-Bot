package com.brain.offlineai.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentExecutionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(execution: AgentExecutionEntity)

    @Query("SELECT * FROM agent_executions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentExecutionEntity?

    @Query("SELECT * FROM agent_executions WHERE sessionId = :sessionId AND status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(sessionId: String, status: String): AgentExecutionEntity?

    @Query("SELECT * FROM agent_executions WHERE status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestAny(status: String): AgentExecutionEntity?

    @Query("DELETE FROM agent_executions WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
