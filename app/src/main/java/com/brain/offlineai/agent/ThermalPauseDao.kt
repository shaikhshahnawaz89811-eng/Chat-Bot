package com.brain.offlineai.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ThermalPauseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pause: ThermalPauseEntity)

    /** Real, most-recent still-open thermal pause for this session, or null if there genuinely isn't one. */
    @Query("SELECT * FROM thermal_pauses WHERE sessionId = :sessionId AND status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestByStatus(sessionId: String, status: String): ThermalPauseEntity?

    @Query("UPDATE thermal_pauses SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("DELETE FROM thermal_pauses WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
