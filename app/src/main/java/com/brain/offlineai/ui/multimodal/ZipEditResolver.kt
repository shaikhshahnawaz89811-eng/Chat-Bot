package com.brain.offlineai.ui.multimodal

import com.brain.offlineai.data.attachments.AttachmentContentReader

/**
 * Phase 16 (Real ZIP content edit) - real, deterministic resolution of
 * *which single file inside an attached ZIP* the user's own message is
 * actually naming, so a "change/fix X" request against a ZIP can target a
 * real entry's real content instead of only ever seeing the ZIP's entry
 * *listing* (Phase 14's own, still-real, but more limited capability).
 *
 * Same "no ungrounded model guess" posture [com.brain.offlineai.ui.tasks.TaskSplitter],
 * [com.brain.offlineai.ui.normalize.InputNormalizer] and
 * [classifyAttachmentRole] already established: this never asks the model
 * to guess a target file. It only ever matches the user's own literal
 * words against the ZIP's own real entry names - a plain, real, bounded
 * string search over data this app already genuinely read
 * ([AttachmentContentReader.listZipEntries]).
 *
 * Deliberately conservative: if the message names **zero** or **more than
 * one** real entry, this returns null - no target is picked, no risky
 * "closest guess" edit is attempted. A null result means the caller falls
 * back to Phase 14's existing entry-listing behavior (still real, just
 * without a real content-level edit) rather than editing the wrong file.
 */
/**
 * Phase 16 (Real ZIP content edit) - the real, already-resolved target for
 * an edit against one specific entry inside one specific attached ZIP.
 * Only ever constructed when [ZipEditResolver.resolveEditTarget] found
 * exactly one unambiguous real match - see that function's own doc.
 */
data class ZipEditTarget(
    val attachmentId: String,
    val zipStoredPath: String,
    val zipDisplayName: String,
    val entryName: String
)

object ZipEditResolver {

    fun resolveEditTarget(
        entries: List<AttachmentContentReader.ZipEntrySummary>,
        messageText: String
    ): AttachmentContentReader.ZipEntrySummary? {
        val lower = messageText.lowercase()
        val candidates = entries.filter { entry ->
            if (entry.isDirectory) return@filter false
            val fileName = entry.name.substringAfterLast('/')
            if (fileName.isBlank()) return@filter false
            // Real, literal substring match on the entry's own file name
            // (e.g. "ChatViewModel.kt") against the user's own message text -
            // never a fuzzy/approximate match that could pick the wrong file.
            lower.contains(fileName.lowercase())
        }
        // Exactly one real, unambiguous match only - see class doc above.
        return candidates.singleOrNull()
    }
}
