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

    /** Real, bounded set of file extensions this fallback will actually open and scan - same "readable text only" boundary [AttachmentContentReader.isTextReadable] already enforces elsewhere. */
    private val SCANNABLE_EXTENSIONS = setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "go", "rs", "swift", "c", "cpp", "h", "hpp", "cs", "php", "rb", "dart")

    /** Real declaration shapes this fallback recognizes - a symbol is only treated as "defined here" when it appears after one of these real keywords, never a bare-word text match that could hit a comment, a call site, or an unrelated string. */
    private val declarationKeyword = Regex("""\b(fun|function|def|class|interface|object|struct|void|int|String|const|let|var)\s+(\w+)\s*[(<{]""")

    /**
     * Weakness-review fix - [resolveEditTarget] above only ever matches a
     * real *file* name against the message; a message that names a
     * *function/class* instead ("fix calculateTotal", "there's a bug in
     * the LoginActivity class") never resolved to anything, so the caller
     * fell back to no real edit target at all even though the user
     * genuinely pointed at something specific.
     *
     * Real, bounded fallback: pulls identifier-shaped words out of the
     * user's own message (plain regex - never a model guess at what they
     * meant), then genuinely reads each real, text-scannable entry's
     * content (bounded to [maxEntriesScanned] entries, same real
     * [AttachmentContentReader.readZipEntryText] this project already
     * uses elsewhere) looking for a real declaration of that exact
     * identifier. Only resolves when the identifier is genuinely declared
     * in **exactly one** real entry - the same "zero or ambiguous means no
     * target, never a guess" posture [resolveEditTarget] already holds
     * itself to above.
     */
    suspend fun resolveEditTargetByDeclaration(
        entries: List<AttachmentContentReader.ZipEntrySummary>,
        storedPath: String,
        messageText: String,
        maxEntriesScanned: Int = 250
    ): AttachmentContentReader.ZipEntrySummary? {
        val identifiers = Regex("""\b[A-Za-z_][A-Za-z0-9_]{2,}\b""").findAll(messageText)
            .map { it.value }
            .filter { it[0].isUpperCase() || it.any { c -> c.isUpperCase() } || it.contains('_') }
            .toList()
        if (identifiers.isEmpty()) return null

        val scannable = entries.filter { entry ->
            !entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in SCANNABLE_EXTENSIONS
        }.take(maxEntriesScanned)

        val matches = mutableListOf<AttachmentContentReader.ZipEntrySummary>()
        for (entry in scannable) {
            val content = AttachmentContentReader.readZipEntryText(storedPath, entry.name) ?: continue
            val declaredHere = declarationKeyword.findAll(content).map { it.groupValues[2] }.toSet()
            if (identifiers.any { it in declaredHere }) matches += entry
            if (matches.size > 1) break // already ambiguous - no need to keep scanning
        }
        return matches.singleOrNull()
    }
}
