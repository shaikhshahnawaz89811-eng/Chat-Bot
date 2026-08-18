package com.brain.offlineai.data.attachments

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real CRUD over the attachment-metadata Room table, same shape as
 * `ChatHistoryRepository`. [fileManager] is also owned here so the DB row
 * and the real on-disk file it points at are always deleted together
 * (Rule 3 - one real delete action, not a DB row that outlives its file or
 * a file that outlives its row).
 */
class AttachmentRepository(context: Context) {

    private val dao = AttachmentDatabase.getInstance(context).attachmentDao()
    private val fileManager = AttachmentFileManager(context)

    suspend fun save(attachment: AttachmentEntity) = withContext(Dispatchers.IO) {
        dao.insert(attachment)
    }

    suspend fun getForSession(sessionId: String): List<AttachmentEntity> =
        withContext(Dispatchers.IO) { dao.getForSession(sessionId) }

    /** Real, permanent removal of every attachment row *and* its real on-disk file for a deleted session. */
    suspend fun deleteForSession(sessionId: String) = withContext(Dispatchers.IO) {
        val rows = dao.getForSession(sessionId)
        rows.forEach { fileManager.deleteAttachmentFile(it.storedPath) }
        dao.deleteForSession(sessionId)
    }
}
