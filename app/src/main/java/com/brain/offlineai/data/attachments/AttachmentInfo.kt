package com.brain.offlineai.data.attachments

/**
 * Phase 10 - the real, already-copied-to-app-private-storage attachment
 * this app actually has on disk. [storedPath] always points at a genuine
 * file under app-private storage (never the original picked Uri, which can
 * become unreadable once the SAF picker's transient grant ends) - same
 * "own the bytes, don't hold a dangling Uri" approach [com.brain.offlineai.engine.ModelFileManager]
 * already uses for imported GGUF models.
 */
data class AttachmentInfo(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val kind: AttachmentKind,
    val mimeType: String?,
    val storedPath: String
)
