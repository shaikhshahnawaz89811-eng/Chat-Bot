package com.brain.offlineai.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProjectTypePauseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectTypePauseEntity)

    /** Real, most-recent pending question for this session - used by [ProjectTypePauseRepository.getAwaiting]. */
    @Query("SELECT * FROM project_type_pauses WHERE sessionId = :sessionId AND status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestByStatus(sessionId: String, status: String): ProjectTypePauseEntity?

    @Query("UPDATE project_type_pauses SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("DELETE FROM project_type_pauses WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
