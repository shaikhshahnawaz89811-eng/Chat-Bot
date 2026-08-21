package com.brain.offlineai.data.attachments

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Phase 14 (Multimodal input use-case routing) - real, bounded reads of an
 * attachment file that Phase 10 already genuinely copied into app-private
 * storage. The "file = kaam start" rule from Phase 10's own doc is
 * unaffected: nothing in this object is ever called until
 * [com.brain.offlineai.ui.screens.chat.ChatViewModel.sendMessage] has
 * genuinely sent the message - picking/copying an attachment still never
 * triggers a read of its content.
 *
 * Two real capabilities only:
 *  - a bounded, real text read for attachments whose extension this app
 *    can honestly decode as text (source code, config, plain text) - see
 *    [readTextPreview]. Truncated (never silently) past [MAX_TEXT_PREVIEW_BYTES].
 *  - a bounded, real ZIP entry listing (name/size/isDirectory only) via
 *    the JDK's own `java.util.zip.ZipInputStream` - see [listZipEntries].
 *    Entries are never themselves opened/extracted here, only listed.
 *
 * Deliberately does **not** attempt to read pixel/frame data from an IMAGE
 * or VIDEO attachment. This project's own tech-stack table records the
 * local model as Qwen2.5-1.5B-Instruct, a text-only checkpoint - it has no
 * real vision capability, and inventing a fake "the image shows..."
 * description would be exactly the kind of fabrication every earlier
 * phase's "Explicitly NOT faked" section already refuses to do (e.g.
 * Phase 4 never invented a token-usage number it couldn't compute for
 * real). Only a real file name/size/role ever gets passed along for those
 * two kinds - see [com.brain.offlineai.ui.multimodal.AttachmentPromptBuilder].
 */
object AttachmentContentReader {

    /** Fixed, real set of extensions this app can honestly decode as UTF-8 text - not guessed from content. */
    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "kt", "kts", "java", "py", "json", "xml", "csv", "yml", "yaml",
        "gradle", "properties", "html", "css", "js", "ts", "c", "cpp", "h", "hpp",
        "sh", "toml", "ini", "log"
    )

    private const val MAX_TEXT_PREVIEW_BYTES = 8_000
    private const val MAX_PDF_PREVIEW_CHARS = 8_000

    fun isTextReadable(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

    /** Real, fixed check - only ".pdf" is handled by [readPdfTextPreview] this phase. */
    fun isPdfReadable(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() == "pdf"

    /**
     * Phase 24 - real, bounded text extraction from an already-copied real
     * PDF file, via PDFBox-Android's real [PDDocument]/[PDFTextStripper]
     * (see [com.brain.offlineai.BrainApplication] for the required
     * one-time `PDFBoxResourceLoader.init` call). Returns null when the
     * file genuinely can't be opened/parsed as a PDF (e.g. it's a scanned
     * image-only PDF with no real embedded text layer) - this app has no
     * OCR capability, so a scanned PDF is honestly reported as unreadable
     * rather than a fabricated description standing in for text that was
     * never actually extracted.
     */
    suspend fun readPdfTextPreview(storedPath: String, maxChars: Int = MAX_PDF_PREVIEW_CHARS): String? =
        withContext(Dispatchers.IO) {
            val file = File(storedPath)
            if (!file.exists() || !file.isFile) return@withContext null
            try {
                PDDocument.load(file).use { document ->
                    val fullText = PDFTextStripper().getText(document)
                    if (fullText.isBlank()) {
                        null
                    } else if (fullText.length > maxChars) {
                        "${fullText.take(maxChars)}\n... (truncated - ${fullText.length} characters total)"
                    } else {
                        fullText
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Real bounded read of the real file at [storedPath]. Returns null if
     * the file genuinely doesn't exist or can't be read - never guesses or
     * fabricates a substitute for content that couldn't actually be read.
     */
    suspend fun readTextPreview(storedPath: String, maxBytes: Int = MAX_TEXT_PREVIEW_BYTES): String? =
        withContext(Dispatchers.IO) {
            val file = File(storedPath)
            if (!file.exists() || !file.isFile) return@withContext null
            try {
                val bytesToRead = minOf(file.length(), maxBytes.toLong()).toInt()
                val buffer = ByteArray(bytesToRead)
                val actuallyRead = file.inputStream().use { it.read(buffer) }
                val safeCount = if (actuallyRead > 0) actuallyRead else 0
                val text = String(buffer, 0, safeCount, Charsets.UTF_8)
                if (file.length() > safeCount) "$text\n... (truncated - ${file.length()} bytes total)" else text
            } catch (e: Exception) {
                null
            }
        }

    data class ZipEntrySummary(val name: String, val sizeBytes: Long, val isDirectory: Boolean)

    /**
     * Real ZIP entry listing (names/sizes only, bounded to
     * [maxEntries]) read straight from the real ZIP bytes already on
     * disk. An empty list means the file genuinely couldn't be read as a
     * ZIP - never a fabricated "no files found" claim about a ZIP this app
     * never actually opened.
     */
    suspend fun listZipEntries(storedPath: String, maxEntries: Int = Int.MAX_VALUE): List<ZipEntrySummary> =
        withContext(Dispatchers.IO) {
            val file = File(storedPath)
            if (!file.exists() || !file.isFile) return@withContext emptyList()
            val results = mutableListOf<ZipEntrySummary>()
            try {
                ZipInputStream(file.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && results.size < maxEntries) {
                        results.add(ZipEntrySummary(entry.name, entry.size, entry.isDirectory))
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } catch (e: Exception) {
                return@withContext emptyList()
            }
            results
        }

    /**
     * Phase 16 (Real ZIP entry edit) - real, bounded read of exactly one
     * real entry's real content from a ZIP already on disk. Streams every
     * entry via `ZipInputStream` until the requested [entryName] is found
     * (name match only, real bytes - never guessed), then reads up to
     * [maxBytes] of its real content. Returns null when the ZIP can't be
     * read, the entry genuinely doesn't exist, or it's a directory entry -
     * never a fabricated substitute for content that wasn't actually read.
     */
    suspend fun readZipEntryText(storedPath: String, entryName: String, maxBytes: Int = MAX_TEXT_PREVIEW_BYTES): String? =
        withContext(Dispatchers.IO) {
            val file = File(storedPath)
            if (!file.exists() || !file.isFile) return@withContext null
            try {
                ZipInputStream(file.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name == entryName) {
                            val buffer = ByteArray(maxBytes)
                            val actuallyRead = zip.read(buffer)
                            val safeCount = if (actuallyRead > 0) actuallyRead else 0
                            val text = String(buffer, 0, safeCount, Charsets.UTF_8)
                            // A real, honest truncation note when this real entry is
                            // larger than the cap - never silently cut with no sign.
                            val hasMore = zip.read() != -1
                            return@withContext if (hasMore) "$text\n... (truncated at $maxBytes bytes)" else text
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
}
