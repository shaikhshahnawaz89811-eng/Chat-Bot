package com.brain.offlineai.data.attachments

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttachmentDao {

    @Insert
    suspend fun insert(attachment: AttachmentEntity)

    @Query("SELECT * FROM chat_attachments WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getForSession(sessionId: String): List<AttachmentEntity>

    @Query("SELECT * FROM chat_attachments WHERE sessionId = :sessionId AND messageId = :messageId ORDER BY createdAt ASC")
    suspend fun getForMessage(sessionId: String, messageId: Long): List<AttachmentEntity>

    @Query("DELETE FROM chat_attachments WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
