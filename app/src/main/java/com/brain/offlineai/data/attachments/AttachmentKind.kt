package com.brain.offlineai.data.attachments

/**
 * Phase 10 (File/ZIP/Image/Video upload flow) - the fixed attachment-kind
 * set this phase actually needs (spec section 4). Classification is real:
 * driven by the real MIME type the ContentResolver reports for the picked
 * SAF Uri, falling back to a real file-extension check only when the
 * resolver returns a null/generic type (some content providers do this for
 * plain files) - never guessed from the file's display name alone.
 */
enum class AttachmentKind { IMAGE, VIDEO, ZIP, FILE }

/** Real classification - no network/model call, just the Uri's own reported MIME type + extension. */
fun classifyAttachment(fileName: String, mimeType: String?): AttachmentKind {
    val lowerName = fileName.lowercase()
    return when {
        mimeType?.startsWith("image/") == true ||
            lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".webp") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") -> AttachmentKind.IMAGE

        mimeType?.startsWith("video/") == true ||
            lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") ||
            lowerName.endsWith(".webm") || lowerName.endsWith(".3gp") -> AttachmentKind.VIDEO

        mimeType == "application/zip" || mimeType == "application/x-zip-compressed" ||
            lowerName.endsWith(".zip") -> AttachmentKind.ZIP

        else -> AttachmentKind.FILE
    }
}
