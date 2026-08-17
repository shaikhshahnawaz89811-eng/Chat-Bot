package com.brain.offlineai.data.history

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Real CRUD over the chat-history Room table, same shape as
 * `ApiKeyRepository`. Every write here is a genuine DB operation - a
 * session created by `ChatViewModel.sendMessage()` and the messages
 * appended to it are on disk, not only held in the ViewModel's in-memory
 * `mutableStateOf` list (which is all Phase 1-6 ever did for chat state).
 */
class ChatHistoryRepository(context: Context) {

    private val dao = ChatHistoryDatabase.getInstance(context).chatHistoryDao()

    fun observeSessions(): Flow<List<ChatSessionEntity>> = dao.observeSessions()

    suspend fun getSession(sessionId: String): ChatSessionEntity? =
        withContext(Dispatchers.IO) { dao.getSession(sessionId) }

    suspend fun getMessages(sessionId: String): List<ChatHistoryMessageEntity> =
        withContext(Dispatchers.IO) { dao.getMessages(sessionId) }

    /** Creates a new session titled from the first real user message (truncated, not a placeholder like "New chat"). */
    suspend fun createSession(firstMessageText: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val title = firstMessageText.trim().let { if (it.length <= 48) it else it.take(45) + "..." }
        dao.insertSession(ChatSessionEntity(id = id, title = title, createdAt = now, updatedAt = now, messageCount = 0))
        id
    }

    /** Real upsert - called on every streamed token update as well as the final message, so a kill mid-stream doesn't lose the partial answer. */
    suspend fun saveMessage(sessionId: String, messageId: Long, text: String, isUser: Boolean, timestampMillis: Long, totalMessageCount: Int) =
        withContext(Dispatchers.IO) {
            dao.upsertMessage(sessionId, messageId, text, isUser, timestampMillis)
            dao.touchSession(sessionId, System.currentTimeMillis(), totalMessageCount)
        }

    /** Rule 3 "delete" - a real, permanent removal of the session and its messages, not a soft hide. */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteMessagesForSession(sessionId)
        dao.deleteSession(sessionId)
    }
}
