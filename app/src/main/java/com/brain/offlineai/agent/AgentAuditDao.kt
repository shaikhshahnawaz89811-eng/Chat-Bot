package com.brain.offlineai.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentAuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AgentAuditEntity)

    /** Real, most-recent-first audit rows for one session - used to show a real "recent changes" trail if a later screen wants it. */
    @Query("SELECT * FROM agent_audit_log WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getForSession(sessionId: String): List<AgentAuditEntity>

    @Query("DELETE FROM agent_audit_log WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
