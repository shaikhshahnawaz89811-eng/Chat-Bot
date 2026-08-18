package com.brain.offlineai.data.artifacts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ArtifactDao {

    @Insert
    suspend fun insert(artifact: ArtifactEntity)

    @Query("SELECT * FROM chat_artifacts WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getForSession(sessionId: String): List<ArtifactEntity>

    @Query("SELECT * FROM chat_artifacts WHERE sessionId = :sessionId AND messageId = :messageId ORDER BY createdAt ASC")
    suspend fun getForMessage(sessionId: String, messageId: Long): List<ArtifactEntity>

    @Query("DELETE FROM chat_artifacts WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
