package com.brain.offlineai.data.attachments

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Real, byte-counted copy step - same shape as [com.brain.offlineai.engine.ImportProgress]. */
sealed class AttachmentCopyProgress {
    data class Copying(val bytesCopied: Long, val totalBytes: Long) : AttachmentCopyProgress()
    data class Done(val file: File, val sizeBytes: Long) : AttachmentCopyProgress()
    data class Failed(val reason: String) : AttachmentCopyProgress()
}

/**
 * Phase 10 (File/ZIP/Image/Video upload flow) - copies a real file the user
 * picked via the system document picker (Storage Access Framework) into
 * app-private storage, with real progress based on actual bytes copied
 * (queried from the ContentResolver, not estimated) - the exact same
 * pattern [com.brain.offlineai.engine.ModelFileManager] already uses for
 * GGUF imports, applied here to attachments instead of models.
 *
 * "File = kaam start" rule (explicit user instruction, this phase): this
 * class only ever copies bytes. It never reads a ZIP's contents, never
 * inspects/parses/modifies an image or video, and never triggers any AI
 * call - attaching a file is not itself a task. The real work (whatever
 * the user actually asks for) only starts when [ChatViewModel.sendMessage]
 * is genuinely called with the attachment already attached.
 */
class AttachmentFileManager(private val context: Context) {

    private val attachmentsDir: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    /**
     * Each attachment gets its own real subfolder (named by a fresh
     * [UUID]) so two attachments with the same original file name never
     * collide on disk - no filename-based overwrite risk.
     */
    fun copyAttachment(uri: Uri, displayName: String): Flow<AttachmentCopyProgress> = flow {
        val totalBytes = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) {
            -1L
        }

        val destDir = File(attachmentsDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val destFile = File(destDir, sanitizeFileName(displayName))

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(1 shl 20) // 1 MB chunks, same as ModelFileManager
                    var copied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        copied += read
                        emit(AttachmentCopyProgress.Copying(copied, totalBytes))
                    }
                }
            } ?: run {
                emit(AttachmentCopyProgress.Failed("Could not open the selected file."))
                return@flow
            }
        } catch (e: Exception) {
            destFile.delete()
            destDir.delete()
            emit(AttachmentCopyProgress.Failed("Copy failed: ${e.message}"))
            return@flow
        }

        emit(AttachmentCopyProgress.Done(destFile, destFile.length()))
    }.flowOn(Dispatchers.IO)

    /** Real, permanent removal of one attachment's file (and its now-empty per-attachment folder). */
    fun deleteAttachmentFile(storedPath: String) {
        val file = File(storedPath)
        val parent = file.parentFile
        file.delete()
        if (parent != null && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
            parent.delete()
        }
    }

    private fun sanitizeFileName(name: String): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return safe.ifBlank { "attachment" }
    }
}
