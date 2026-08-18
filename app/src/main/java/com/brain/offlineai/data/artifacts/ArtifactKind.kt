package com.brain.offlineai.data.artifacts

/**
 * Phase 11 (Artifact card + ZIP/file output + download flow, spec section
 * 5) - the fixed artifact-kind set this phase needs to pick a real icon and
 * a real MIME type for each real generated file. Same shape as Phase 10's
 * `AttachmentKind` (data/attachments/AttachmentKind.kt), classified from the
 * real file extension actually written to disk - never guessed from
 * anything the user typed.
 */
enum class ArtifactKind { CODE, ZIP, TEXT }

/** Real classification, driven only by the real extension [ArtifactFileManager] actually wrote. */
fun classifyArtifact(fileName: String): ArtifactKind {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "zip" -> ArtifactKind.ZIP
        "txt", "md" -> ArtifactKind.TEXT
        "" -> ArtifactKind.TEXT
        else -> ArtifactKind.CODE
    }
}

/** Real MIME type for the real extension - used for the FileProvider share/open Intent and MediaStore export. */
fun mimeTypeForArtifact(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "zip" -> "application/zip"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "html", "htm" -> "text/html"
        "md" -> "text/markdown"
        "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "hpp", "sh",
        "yaml", "yml", "sql", "css", "gradle", "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
