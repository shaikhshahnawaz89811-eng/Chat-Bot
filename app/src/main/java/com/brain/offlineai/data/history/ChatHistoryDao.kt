package com.brain.offlineai.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_history_messages WHERE sessionId = :sessionId ORDER BY messageId ASC")
    suspend fun getMessages(sessionId: String): List<ChatHistoryMessageEntity>

    @Insert
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt, messageCount = :messageCount WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long, messageCount: Int)

    @Insert
    suspend fun insertMessage(message: ChatHistoryMessageEntity)

    /**
     * Real upsert for a single streamed bot bubble: `ChatViewModel` calls this
     * on every token update while a response streams in, so the on-disk copy
     * always matches what's on screen instead of only being written once the
     * stream finishes (a crash/kill mid-stream would otherwise lose the
     * partial answer, unlike everything else in this app that writes real
     * data as it happens rather than batching it).
     */
    @Transaction
    suspend fun upsertMessage(sessionId: String, messageId: Long, text: String, isUser: Boolean, timestampMillis: Long) {
        val existing = getMessageRow(sessionId, messageId)
        if (existing != null) {
            updateMessageText(existing.rowId, text, timestampMillis)
        } else {
            insertMessage(
                ChatHistoryMessageEntity(
                    sessionId = sessionId,
                    messageId = messageId,
                    text = text,
                    isUser = isUser,
                    timestampMillis = timestampMillis
                )
            )
        }
    }

    @Query("SELECT * FROM chat_history_messages WHERE sessionId = :sessionId AND messageId = :messageId LIMIT 1")
    suspend fun getMessageRow(sessionId: String, messageId: Long): ChatHistoryMessageEntity?

    @Query("UPDATE chat_history_messages SET text = :text, timestampMillis = :timestampMillis WHERE rowId = :rowId")
    suspend fun updateMessageText(rowId: Long, text: String, timestampMillis: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_history_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)
}
