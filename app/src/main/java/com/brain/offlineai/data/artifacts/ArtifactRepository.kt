package com.brain.offlineai.data.artifacts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real CRUD over the artifact-metadata Room table, same shape as
 * [com.brain.offlineai.data.attachments.AttachmentRepository]. [fileManager]
 * is owned here too, so a deleted session's DB rows and their real on-disk
 * files are always removed together (Rule 3).
 */
class ArtifactRepository(context: Context) {

    private val dao = ArtifactDatabase.getInstance(context).artifactDao()
    private val fileManager = ArtifactFileManager(context)

    suspend fun save(artifact: ArtifactEntity) = withContext(Dispatchers.IO) {
        dao.insert(artifact)
    }

    suspend fun getForSession(sessionId: String): List<ArtifactEntity> =
        withContext(Dispatchers.IO) { dao.getForSession(sessionId) }

    /** Real, permanent removal of every artifact row *and* its real on-disk file for a deleted session. */
    suspend fun deleteForSession(sessionId: String) = withContext(Dispatchers.IO) {
        val rows = dao.getForSession(sessionId)
        rows.forEach { fileManager.deleteArtifactFile(it.storedPath) }
        dao.deleteForSession(sessionId)
    }
}
