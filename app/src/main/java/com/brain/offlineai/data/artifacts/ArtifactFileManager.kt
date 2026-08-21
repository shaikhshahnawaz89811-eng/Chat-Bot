package com.brain.offlineai.data.artifacts

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real, byte-counted export step - same shape as Phase 10's `AttachmentCopyProgress`. */
sealed class ArtifactExportProgress {
    data class Copying(val bytesCopied: Long, val totalBytes: Long) : ArtifactExportProgress()
    data class Done(val uri: Uri) : ArtifactExportProgress()
    data class Failed(val reason: String) : ArtifactExportProgress()
}

/** Where a real export actually lands - drives which real Android API [ArtifactFileManager.exportToDownloads] uses. */
enum class ArtifactDownloadTarget { SAVE_TO_DEVICE, SHARE, OPEN_IN_FILE_MANAGER }

/**
 * Phase 11 (Artifact card + ZIP/file output + download flow, spec section
 * 5) - owns the real, on-disk life of a generated artifact: writing its
 * real text bytes, zipping several real artifacts into one real ZIP, and
 * exporting a real file out of app-private storage to somewhere the user
 * can actually keep it. No step here is simulated - every byte written,
 * zipped, or copied is real content that was either genuinely produced by
 * [com.brain.offlineai.engine.BrainEngine]'s completed generation or is a
 * real ZIP container built from those real files.
 */
class ArtifactFileManager(private val context: Context) {

    private val artifactsDir: File
        get() = File(context.filesDir, "artifacts").apply { mkdirs() }

    /**
     * Each artifact gets its own real subfolder (named by a fresh [UUID])
     * so two artifacts sharing a file name never collide on disk.
     *
     * Bug fix (user report - web preview par CSS/JS load nahi hoti thi).
     * A real multi-file build (index.html + styles.css + script.js, etc)
     * used to give every one of its own real files a *different* random
     * UUID folder, so `index.html`'s own real relative
     * `<link href="styles.css">`/`<script src="script.js">` genuinely
     * could never resolve inside [com.brain.offlineai.ui.screens.preview.WebPreviewScreen]'s
     * real `file://` WebView load - not a preview bug, a real "the
     * sibling file genuinely isn't in the same folder" problem.
     * [projectDirId], when given, reuses the exact same real subfolder for
     * every file that passes the same id - the caller (see
     * [com.brain.offlineai.ui.screens.chat.ChatViewModel.runMultiFileBuild])
     * generates one real UUID per build and threads it through every file
     * in that build, so the whole project genuinely lands together in one
     * real folder, the same way any real, ordinary multi-file project
     * already sits on disk. Null (the default) keeps every existing
     * single-artifact call site's behavior completely unchanged - its own
     * fresh UUID folder, same as before this fix.
     */
    fun writeArtifact(fileName: String, content: String, projectDirId: String? = null): File {
        val destDir = File(artifactsDir, projectDirId ?: UUID.randomUUID().toString()).apply { mkdirs() }
        val destFile = File(destDir, sanitizeRelativePath(fileName))
        destFile.parentFile?.mkdirs()
        if (destFile.exists()) {
            val existing = runCatching { destFile.readText(Charsets.UTF_8) }.getOrNull()
            require(existing == content) { "Artifact path collision: $fileName maps to an existing different file" }
            return destFile
        }
        destFile.writeText(content, Charsets.UTF_8)
        // Weakness-review fix ("file sach me ban rahi hai ya nahi check
        // kare") - writeText() normally throws on a real IO failure, but a
        // partial/interrupted write (e.g. disk full mid-write) can still
        // leave a file that exists with the wrong byte count without ever
        // throwing. A real, cheap post-write check - re-read the file's
        // own real length and compare to the real content that was meant
        // to be written - so a silently-truncated save is reported as a
        // genuine failure instead of the caller assuming success just
        // because no exception was thrown.
        val expectedBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
        if (!destFile.exists() || destFile.length() != expectedBytes) {
            throw java.io.IOException(
                "Write verification failed for ${destFile.name}: expected $expectedBytes bytes, " +
                    "found ${if (destFile.exists()) destFile.length() else -1}."
            )
        }
        return destFile
    }

    /** Real readback of a multi-file project's current on-disk files, used to reconstruct a persisted build after process death. */
    fun listProjectFiles(projectDirId: String): List<Pair<String, File>> {
        val root = File(artifactsDir, projectDirId)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown().filter { it.isFile }.map { file ->
            root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/') to file
        }.toList()
    }

    /** Real, permanent removal of one artifact's file (and its now-empty per-artifact folder). */
    fun deleteArtifactFile(storedPath: String) {
        val file = File(storedPath)
        val parent = file.parentFile
        file.delete()
        if (parent != null && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
            parent.delete()
        }
    }

    /**
     * Real ZIP container built from real files already on disk - one
     * `ZipEntry` per artifact, streamed in real 64 KB chunks (no full-file
     * read into memory, so this scales to genuinely large artifact sets).
     */
    fun createZipFromFiles(files: List<File>, zipName: String): File {
        return createZip(files.map { it.name to it }, zipName)
    }

    /**
     * Project-aware ZIP packaging.  The published artifact files are stored
     * in UUID folders and may have sanitized basenames, so using File.name
     * loses real source paths (for example app/src/.../MainActivity.kt).
     * This overload preserves the planned relative path inside the ZIP.
     */
    fun createZip(files: List<Pair<String, File>>, zipName: String): File {
        val destDir = File(artifactsDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val zipFile = File(destDir, sanitizeFileName(zipName).let { if (it.endsWith(".zip")) it else "$it.zip" })
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            val buffer = ByteArray(64 * 1024)
            val seenEntries = mutableSetOf<String>()
            files.forEach { (entryName, file) ->
                val safeEntry = normalizeZipEntryName(entryName)
                require(seenEntries.add(safeEntry)) { "Duplicate ZIP entry: $entryName" }
                require(file.isFile) { "Cannot package missing/non-file artifact: ${file.absolutePath}" }
                zipOut.putNextEntry(ZipEntry(safeEntry))
                FileInputStream(file).use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        zipOut.write(buffer, 0, read)
                    }
                }
                zipOut.closeEntry()
            }
        }
        return zipFile
    }

    /**
     * Phase 16 (Real ZIP content edit) - real, full ZIP-to-ZIP copy where
     * exactly the entries named in [replacements] get their real content
     * swapped for the real new text, and every other real entry is
     * streamed through byte-for-byte, unchanged. Never a partial/half-built
     * archive: [sourceZip] is read start to finish and every one of its
     * real entries is written to the new file - either the real replacement
     * text, or the original real bytes.
     */
    fun patchZip(sourceZip: File, replacements: Map<String, String>, zipName: String): File {
        val destDir = File(artifactsDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val zipFile = File(destDir, sanitizeFileName(zipName).let { if (it.endsWith(".zip")) it else "$it.zip" })
        val buffer = ByteArray(64 * 1024)
        java.util.zip.ZipInputStream(FileInputStream(sourceZip)).use { zipIn ->
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                val seenEntries = mutableSetOf<String>()
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val safeEntry = normalizeZipEntryName(entry.name)
                    require(seenEntries.add(safeEntry)) { "Duplicate ZIP entry: ${entry.name}" }
                    val outEntry = ZipEntry(safeEntry).apply {
                        time = entry.time
                    }
                    zipOut.putNextEntry(outEntry)
                    val replacementText = if (!entry.isDirectory) replacements[entry.name] else null
                    if (replacementText != null) {
                        zipOut.write(replacementText.toByteArray(Charsets.UTF_8))
                    } else {
                        var read: Int
                        while (zipIn.read(buffer).also { read = it } != -1) {
                            zipOut.write(buffer, 0, read)
                        }
                    }
                    zipOut.closeEntry()
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
        return zipFile
    }

    /**
     * Real "Save to Device" - copies [sourceFile]'s real bytes out of
     * app-private storage into the device's genuine Downloads location, so
     * the file survives an app uninstall (matches the spec's "Download to
     * Save on Device" option). Two real code paths, not one faked with a
     * version check that does nothing different:
     * - API 29+ (Q, scoped storage): real `MediaStore.Downloads` insert +
     *   `ContentResolver` stream copy - no storage permission needed.
     * - API 26-28: real direct `File` write under
     *   `Environment.DIRECTORY_DOWNLOADS`, which genuinely requires
     *   `WRITE_EXTERNAL_STORAGE` to already be granted (checked by the
     *   caller - [com.brain.offlineai.ui.screens.chat.ChatScreen] - before
     *   this is ever invoked on those API levels).
     */
    fun exportToDownloads(sourceFile: File, mimeType: String): Flow<ArtifactExportProgress> = flow {
        val totalBytes = sourceFile.length()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: run {
                        emit(ArtifactExportProgress.Failed("Could not create the download entry."))
                        return@flow
                    }
                resolver.openOutputStream(itemUri)?.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        copyWithProgress(input, output, totalBytes) { copied ->
                            emit(ArtifactExportProgress.Copying(copied, totalBytes))
                        }
                    }
                } ?: run {
                    resolver.delete(itemUri, null, null)
                    emit(ArtifactExportProgress.Failed("Could not open the download destination."))
                    return@flow
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                emit(ArtifactExportProgress.Done(itemUri))
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = uniqueLegacyFile(downloadsDir, sourceFile.name)
                FileOutputStream(destFile).use { output ->
                    FileInputStream(sourceFile).use { input ->
                        copyWithProgress(input, output, totalBytes) { copied ->
                            emit(ArtifactExportProgress.Copying(copied, totalBytes))
                        }
                    }
                }
                emit(ArtifactExportProgress.Done(Uri.fromFile(destFile)))
            }
        } catch (e: Exception) {
            emit(ArtifactExportProgress.Failed("Save failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: suspend (Long) -> Unit
    ) {
        val buffer = ByteArray(1 shl 20) // 1 MB chunks, same convention as ModelFileManager/AttachmentFileManager
        var copied = 0L
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            copied += read
            onProgress(copied)
        }
        if (totalBytes <= 0) onProgress(copied)
    }

    private fun uniqueLegacyFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val newName = if (ext.isNotEmpty()) "$base ($index).$ext" else "$base ($index)"
            candidate = File(dir, newName)
            index++
        }
        return candidate
    }

    /**
     * Real content:// Uri via the app's real FileProvider (see the
     * `<provider>` entry in AndroidManifest.xml and `res/xml/file_paths.xml`)
     * - used for the spec's "Share" and "Open in File Manager" download
     * options, both of which hand the file to another real app rather than
     * copying it anywhere.
     */
    fun getShareUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun normalizeZipEntryName(name: String): String {
        val normalized = name.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized != "." && !normalized.split('/').contains("..")) {
            "Unsafe ZIP entry name: $name"
        }
        return normalized
    }

    private fun sanitizeFileName(name: String): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return safe.ifBlank { "artifact" }
    }

    /**
     * Bug fix (user report - "src/index.js" jaisi file preview me apne
     * html se link nahi hoti thi). [sanitizeFileName] flattens every '/'
     * into '_', which is fine for a lone artifact's own file name but
     * silently broke any real relative reference a project's own real
     * HTML/plan gave a one-subfolder-deep file - the file that actually
     * landed on disk ("src_index.js") never matched the real path the
     * generated content itself pointed at ("src/index.js"). This keeps
     * each real path segment (still sanitized on its own, same safe-
     * character rule as [sanitizeFileName]) instead of collapsing them,
     * so a real relative reference now genuinely resolves. A literal
     * ".." segment is rejected outright - same "no real escape out of
     * the artifact's own folder" rule [createZip]'s own `safeEntry` check
     * already holds itself to - never a silent guess at "did they mean
     * something safe".
     */
    private fun sanitizeRelativePath(name: String): String {
        val segments = name.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
        require(segments.none { it == ".." }) { "Unsafe artifact path: $name" }
        return segments.map { sanitizeFileName(it) }.joinToString(File.separator).ifBlank { "artifact" }
    }
}
