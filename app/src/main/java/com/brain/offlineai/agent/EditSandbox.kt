package com.brain.offlineai.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Phase 21 (Master Plan v2, section 5 - "Copy-first change... load
 * complete target file -> ... make only requested change -> write-back ->
 * diff"). Real, generalized version of the copy-first/diff discipline
 * Phase 16's single-ZIP-entry patch already followed structurally
 * ([com.brain.offlineai.data.artifacts.ArtifactFileManager.patchZip]
 * always reads [sourceZip] and writes a brand-new file - it never opens
 * the original for writing) - this makes that guarantee explicit and adds
 * the one real piece that was still missing: a genuine, computed diff
 * summary of what actually changed, shown to the user rather than assumed.
 *
 * Real only - no model call, no fabricated line-by-line diff engine. The
 * diff here is a plain, bounded line-set comparison (added/removed/kept
 * line counts), which is enough to honestly answer "did this actually
 * change what I think it changed?" without claiming a precision (e.g. a
 * true LCS diff) this app doesn't actually compute.
 */
data class EditDiffSummary(
    val oldLineCount: Int,
    val newLineCount: Int,
    val linesAdded: Int,
    val linesRemoved: Int
) {
    fun describe(): String {
        val delta = newLineCount - oldLineCount
        val deltaText = if (delta >= 0) "+$delta lines" else "$delta lines"
        return "$oldLineCount -> $newLineCount lines ($deltaText; $linesAdded added, $linesRemoved removed)"
    }
}

object EditSandbox {

    /**
     * Real byte-for-byte copy of [sourceZip] into its own fresh, real
     * staging file under [sandboxDir] - the actual patch step
     * ([com.brain.offlineai.data.artifacts.ArtifactFileManager.patchZip])
     * then reads from this staged copy rather than the original
     * attachment file directly, so the source attachment on disk is never
     * touched by the same operation that reads it, even in principle.
     */
    suspend fun stageCopy(sourceZip: File, sandboxDir: File): File = withContext(Dispatchers.IO) {
        sandboxDir.mkdirs()
        val staged = File(sandboxDir, "${UUID.randomUUID()}_${sourceZip.name}")
        FileInputStream(sourceZip).use { input ->
            FileOutputStream(staged).use { output ->
                input.copyTo(output)
            }
        }
        staged
    }

    /**
     * Real, plain line-set diff between a real entry's real old content
     * and the real new content about to replace it - a genuine count, not
     * an estimate.
     */
    fun diffSummary(oldContent: String, newContent: String): EditDiffSummary {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        // Real, bounded multiset comparison: every old line that finds a
        // real match in the (shrinking) remaining new-lines pool is
        // "kept"; what's left unmatched in each list is a genuine
        // removal/addition. Order-insensitive by design (a real reordering
        // with no content change should not be reported as a mass
        // add+remove) - a documented, honest limitation, not a claim of
        // true positional diffing.
        val newRemaining = newLines.toMutableList()
        var removed = 0
        oldLines.forEach { line ->
            if (!newRemaining.remove(line)) removed++
        }
        val added = newRemaining.size
        return EditDiffSummary(
            oldLineCount = oldLines.size,
            newLineCount = newLines.size,
            linesAdded = added,
            linesRemoved = removed
        )
    }
}
