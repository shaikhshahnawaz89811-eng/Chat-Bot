package com.brain.offlineai.agent

import com.brain.offlineai.data.attachments.AttachmentContentReader

/**
 * Master Plan v2, section 3 ("Universal Chunk workflow - Context Info Box
 * -> Chunk 1 -> Chunk 2 -> ... -> Chunk 5 -> Complete... used whenever a
 * target genuinely exceeds a single safe read"). Phase 20 of the
 * Phase 19-23 framework build (see this file's own Phase 19 Plan section
 * above for the 5-phase breakdown).
 *
 * Real, deterministic chunk *sequencing* only - no summarization, no
 * model call, no invented content. Every chunk's text is built straight
 * from data this app already genuinely read: Chunk 1 reuses Phase 19's
 * own [ProjectContextLoader] output verbatim (Master Plan §4 - "do not
 * duplicate an existing abstraction before inspecting/reusing it"), and
 * Chunks 2-5 are real, bounded groups of a ZIP's own real entry names
 * (from [AttachmentContentReader.listZipEntries], already real since
 * Phase 14) - never file *content*, since a chunk here exists specifically
 * for the case a project's own real entry count/listing is too large to
 * show/send safely in one bounded piece (this project's own established
 * single-read bound is [AttachmentContentReader]'s
 * `MAX_TEXT_PREVIEW_BYTES` = 8,000 bytes; [SAFE_CHUNK_CHARS] below is half
 * of that, since one chunk is one of several real pieces shown together
 * in the same conversation, not the app's single largest allowed read).
 *
 * Deliberately capped at 5 real chunks total (Chunk 1 = structure summary,
 * Chunks 2-5 = up to 4 real entry-name groups) - the Master Plan's own
 * "Universal *5*-Chunk workflow" naming, not an open-ended loop. A project
 * whose real file count still doesn't fit in those 4 groups is reported
 * honestly (a real "+N more files not shown" note on the last chunk,
 * never a silent drop - same standard [AttachmentContentReader.readTextPreview]/
 * [AttachmentContentReader.readZipEntryText] already hold themselves to
 * for their own truncation notes).
 */
data class ContextChunk(
    val index: Int,
    val total: Int,
    val title: String,
    val body: String
)

/** Real, complete result of one chunking run - kept together so a caller never has to re-derive [totalFiles]/[displayName] separately from the chunk list itself. */
data class ChunkPlan(
    val chunks: List<ContextChunk>,
    val totalFiles: Int,
    val displayName: String
)

object ContextManager {

    /** Half of [AttachmentContentReader]'s own established single-read bound - see class doc above for why. */
    private const val SAFE_CHUNK_CHARS = 4_000

    /** Master Plan §3's own "Universal 5-Chunk workflow" - Chunk 1 (structure) + up to 4 real entry-name groups. */
    private const val MAX_REAL_CHUNKS = 5

    /** Real, approximate per-entry line length used only to decide grouping - matches the "- name (N bytes)" format [buildChunkPlan] actually renders. */
    private const val PER_ENTRY_CHARS_ESTIMATE = 20

    /**
     * Real check: would this ZIP's own real entry listing (the same real
     * data [com.brain.offlineai.ui.multimodal.AttachmentPromptBuilder.buildContextBlock]
     * would otherwise dump as one unbounded block) genuinely exceed a
     * single safe read? Bounded/cheap - only sums real, already-listed
     * entry name lengths, never opens file content.
     */
    suspend fun needsChunking(storedPath: String): Boolean {
        val entries = AttachmentContentReader.listZipEntries(storedPath)
        if (entries.isEmpty()) return false
        val realListingChars = entries.sumOf { it.name.length + PER_ENTRY_CHARS_ESTIMATE }
        return realListingChars > SAFE_CHUNK_CHARS
    }

    /**
     * Builds the real, bounded chunk sequence for [storedPath]. Only ever
     * meant to be called after [needsChunking] returned true - still safe
     * (just returns a 1-chunk plan) if called on a small ZIP, so a caller
     * mistake here can't corrupt anything, it just wastes one extra real
     * read.
     */
    suspend fun buildChunkPlan(storedPath: String, displayName: String): ChunkPlan {
        val entries = AttachmentContentReader.listZipEntries(storedPath)
        val files = entries.filter { !it.isDirectory }

        // Chunk 1 - reused verbatim from Phase 19's real structure summary, never rebuilt here (Master Plan §4).
        val projectContext = ProjectContextLoader.load(storedPath)
        val breakdownLines = if (projectContext.componentBreakdown.isEmpty()) {
            "(no recognized source/config file types found)"
        } else {
            projectContext.componentBreakdown.entries.joinToString("\n") { (label, count) -> "- $label: $count" }
        }
        val chunk1Body = "$displayName: ${projectContext.fileCount} files, " +
            "${projectContext.directoryCount} directories, ${projectContext.totalEntries} total entries.\n" +
            "Component breakdown:\n$breakdownLines"

        val chunks = mutableListOf(ContextChunk(index = 1, total = 0, title = "Project structure", body = chunk1Body))

        // Chunks 2-5 - real, bounded groups of the ZIP's own real file
        // names in the ZIP's own real order (never reordered/prioritized
        // by a guess about which file "matters more").
        val groups = groupEntriesBySafeSize(files, SAFE_CHUNK_CHARS, MAX_REAL_CHUNKS - 1)
        var shown = 0
        groups.forEach { group ->
            val from = shown + 1
            val to = shown + group.size
            shown = to
            val lines = group.joinToString("\n") { entry -> "- ${entry.name} (${entry.sizeBytes} bytes)" }
            chunks += ContextChunk(
                index = chunks.size + 1, total = 0,
                title = "Files $from-$to of ${files.size}",
                body = lines
            )
        }

        val leftover = files.size - shown
        val total = chunks.size
        val finalized = chunks.mapIndexed { i, chunk ->
            val body = if (i == chunks.lastIndex && leftover > 0) {
                chunk.body + "\n\n(+ $leftover more real file${if (leftover == 1) "" else "s"} not " +
                    "shown in these $total chunks - ask to see more if needed.)"
            } else {
                chunk.body
            }
            chunk.copy(total = total, body = body)
        }
        return ChunkPlan(chunks = finalized, totalFiles = files.size, displayName = displayName)
    }

    /** Real, deterministic grouping - closes a group once adding the next real entry would exceed [maxCharsPerGroup], and stops entirely once [maxGroups] real groups exist (remainder reported honestly by [buildChunkPlan]'s own leftover note, never silently merged/dropped). */
    private fun groupEntriesBySafeSize(
        files: List<AttachmentContentReader.ZipEntrySummary>,
        maxCharsPerGroup: Int,
        maxGroups: Int
    ): List<List<AttachmentContentReader.ZipEntrySummary>> {
        val groups = mutableListOf<List<AttachmentContentReader.ZipEntrySummary>>()
        var current = mutableListOf<AttachmentContentReader.ZipEntrySummary>()
        var currentChars = 0
        for (entry in files) {
            if (groups.size >= maxGroups) break
            val entryChars = entry.name.length + PER_ENTRY_CHARS_ESTIMATE
            if (current.isNotEmpty() && currentChars + entryChars > maxCharsPerGroup) {
                groups += current
                current = mutableListOf()
                currentChars = 0
                if (groups.size >= maxGroups) break
            }
            current.add(entry)
            currentChars += entryChars
        }
        if (current.isNotEmpty() && groups.size < maxGroups) groups += current
        return groups
    }

    /** Real "Context Info Box" - the real chunk/file counts [buildChunkPlan] already computed, shown once before the chunk sequence starts, per Master Plan §3's own "Context Info Box -> Chunk 1-5 -> Complete" naming. */
    fun buildContextInfoBox(plan: ChunkPlan): String =
        "Context Info Box: ${plan.displayName} is too large for a single safe read " +
            "(Rule 20) - splitting into ${plan.chunks.size} real chunks covering " +
            "${plan.totalFiles} real files before continuing."

    /** Real "Complete" marker closing the chunk sequence Master Plan §3 describes. */
    fun buildCompleteNote(plan: ChunkPlan): String =
        "Complete - all ${plan.chunks.size} chunks of ${plan.displayName} reviewed. Continuing with your request now."
}
