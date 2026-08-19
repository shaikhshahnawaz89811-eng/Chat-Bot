package com.brain.offlineai.ui.multimodal

import com.brain.offlineai.data.attachments.AttachmentContentReader
import com.brain.offlineai.data.attachments.AttachmentInfo
import com.brain.offlineai.data.attachments.AttachmentKind

/**
 * Phase 14 (Multimodal input use-case routing) - turns a message's real
 * [AttachmentRoute]s into a real, bounded text block appended to the
 * actual prompt [com.brain.offlineai.engine.BrainEngine.generate] receives.
 * Only genuinely readable content is included (see
 * [AttachmentContentReader]) - a ZIP contributes its real entry list, a
 * readable text file contributes its real (possibly truncated) content, and
 * an IMAGE/VIDEO attachment contributes only its real file name/size/role
 * plus an honest note that this build can't read its visual content -
 * never a fabricated description standing in for content the app never
 * actually read.
 */
object AttachmentPromptBuilder {

    suspend fun buildContextBlock(
        routes: List<AttachmentRoute>,
        attachmentsById: Map<String, AttachmentInfo>,
        chunkedZipSummaries: Map<String, String> = emptyMap()
    ): String {
        if (routes.isEmpty()) return ""

        val sections = routes.map { route ->
            val info = attachmentsById[route.attachmentId]
            val header = "[Attachment: ${route.fileName} - role: ${route.role.label} (${route.reason})]"
            val body = when (route.kind) {
                AttachmentKind.FILE -> when {
                    info == null -> "(attachment metadata unavailable)"
                    AttachmentContentReader.isTextReadable(route.fileName) -> {
                        val preview = AttachmentContentReader.readTextPreview(info.storedPath)
                        if (preview != null) "Content:\n$preview" else "(file content could not be read as text)"
                    }
                    // Phase 24 - real PDF text extraction (PROGRESS.md's
                    // own recorded open gap). A scanned/image-only PDF
                    // with no real text layer honestly falls through to
                    // the same "could not be read" message below - this
                    // app has no OCR, so it never fabricates text for one.
                    AttachmentContentReader.isPdfReadable(route.fileName) -> {
                        val preview = AttachmentContentReader.readPdfTextPreview(info.storedPath)
                        if (preview != null) "Content:\n$preview" else "(PDF content could not be extracted - it may be a scanned/image-only PDF with no real text layer)"
                    }
                    else -> "(binary file - content is not readable by this app)"
                }
                AttachmentKind.ZIP -> when {
                    info == null -> "(attachment metadata unavailable)"
                    // Phase 20 (Context Manager) - this ZIP's real entry
                    // listing was too large for a single safe read; the
                    // caller already ran the real Chunk 1-5 sequence as
                    // its own visible SYSTEM_NOTEs (see ChatViewModel) and
                    // hands back only Chunk 1's own bounded structure
                    // summary here, instead of the unbounded raw entry
                    // dump below - never both at once.
                    chunkedZipSummaries.containsKey(route.attachmentId) ->
                        "Project structure (full entry list shown separately via the chunked context above):\n" +
                            chunkedZipSummaries.getValue(route.attachmentId)
                    else -> {
                        val entries = AttachmentContentReader.listZipEntries(info.storedPath)
                        if (entries.isEmpty()) {
                            "(ZIP entry list could not be read)"
                        } else {
                            val entryLines = entries.joinToString("\n") { entry ->
                                if (entry.isDirectory) "- ${entry.name}" else "- ${entry.name} (${entry.sizeBytes} bytes)"
                            }
                            "Contains ${entries.size} entr${if (entries.size == 1) "y" else "ies"}:\n$entryLines"
                        }
                    }
                }
                AttachmentKind.IMAGE ->
                    "(image file - this build's local model is text-only and cannot read pixel content; only the file's name/role are provided)"
                AttachmentKind.VIDEO ->
                    "(video file - this build's local model is text-only and cannot read frame content; only the file's name/role are provided)"
            }
            "$header\n$body"
        }

        return "\n\n--- Attached files ---\n" + sections.joinToString("\n\n") + "\n--- End attached files ---"
    }

    /** Real, short, human-readable routing summary shown to the user before generation runs (spec §8 - "route before acting", never a silent internal decision). */
    fun buildRoutingSummary(routes: List<AttachmentRoute>): String {
        if (routes.isEmpty()) return ""
        val lines = routes.joinToString("\n") { route -> "- ${route.fileName} -> ${route.role.label} (${route.reason})" }
        return "Routed ${routes.size} attachment${if (routes.size > 1) "s" else ""}:\n$lines"
    }
}
