package com.brain.offlineai.data.artifacts

/**
 * Phase 11 - a real, already-written-to-app-private-storage output file
 * this app actually produced (a genuine fenced code block from a completed
 * real generation, written to real bytes on disk - never a placeholder
 * name with no backing file). Same "own the bytes" shape as Phase 10's
 * `AttachmentInfo` ([com.brain.offlineai.data.attachments.AttachmentInfo]).
 */
data class ArtifactInfo(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val kind: ArtifactKind,
    val mimeType: String,
    val storedPath: String
)
