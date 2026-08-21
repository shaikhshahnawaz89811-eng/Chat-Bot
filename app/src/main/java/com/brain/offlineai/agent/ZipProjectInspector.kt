package com.brain.offlineai.agent

import com.brain.offlineai.data.attachments.AttachmentContentReader

/**
 * Real project inspection for user-supplied ZIP work.
 *
 * This is intentionally deterministic: the ZIP is inspected from its real
 * entry list, Markdown/docs and build files are preferred, requested files
 * and declarations are located by real text, and only real readable source
 * content is sent onward. It never invents a file or claims that an unseen
 * file was inspected.
 */
object ZipProjectInspector {
    private val docNames = setOf("readme.md", "readme", "contributing.md", "architecture.md", "design.md", "progress.md")
    private val buildNames = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "pom.xml", "package.json", "pyproject.toml", "cargo.toml", "androidmanifest.xml")
    private const val MAX_FILES = 28
    private const val MAX_FILE_CHARS = 6000
    private const val MAX_TOTAL_CHARS = 70_000

    data class FileContext(val name: String, val content: String, val score: Int)
    data class Inspection(
        val totalFiles: Int,
        val selectedFiles: List<FileContext>,
        val referencedFiles: List<String>,
        val omittedReadableFiles: Int
    )

    suspend fun inspect(storedPath: String, request: String, preferredFile: String? = null): Inspection {
        val entries = AttachmentContentReader.listZipEntries(storedPath, maxEntries = 5000)
            .filter { !it.isDirectory && AttachmentContentReader.isTextReadable(it.name) }
        val lowerRequest = request.lowercase()
        val requestWords = Regex("\\b[A-Za-z_][A-Za-z0-9_]{2,}\\b").findAll(request)
            .map { it.value.lowercase() }.toSet()
        val preferred = preferredFile?.lowercase()

        val scored = entries.map { entry ->
            val base = entry.name.substringAfterLast('/').lowercase()
            val ext = base.substringAfterLast('.', "")
            var score = 0
            if (preferred != null && entry.name.lowercase() == preferred) score += 1000
            if (base in docNames) score += 300
            if (base in buildNames) score += 250
            if (ext in setOf("kt", "java", "py", "js", "ts", "cpp", "c", "h", "hpp")) score += 80
            val nameWords = Regex("[a-z0-9_]+", RegexOption.IGNORE_CASE).findAll(base).map { it.value }.toSet()
            score += requestWords.intersect(nameWords).size * 60
            if (entry.name.lowercase().contains("test")) score += 20
            if (entry.name.lowercase().contains("/src/main/")) score += 30
            entry to score
        }.sortedByDescending { it.second }

        val selected = mutableListOf<FileContext>()
        var totalChars = 0
        for ((entry, score) in scored) {
            if (selected.size >= MAX_FILES || totalChars >= MAX_TOTAL_CHARS) break
            val content = AttachmentContentReader.readZipEntryText(storedPath, entry.name, MAX_FILE_CHARS) ?: continue
            val chars = content.length
            if (chars == 0) continue
            selected += FileContext(entry.name, content, score)
            totalChars += chars
        }

        val referenced = if (preferred != null) {
            val targetText = selected.firstOrNull { it.name.equals(preferredFile, ignoreCase = true) }?.content.orEmpty()
            val imports = Regex("(?m)^\\s*(?:import|from)\\s+([^;\\n]+)").findAll(targetText).map { it.groupValues[1].trim() }.toList()
            selected.filter { candidate ->
                candidate.name != preferredFile && imports.any { imp ->
                    val tail = imp.substringAfterLast('.').substringBefore(' ')
                    tail.isNotBlank() && candidate.name.contains(tail, ignoreCase = true)
                }
            }.map { it.name }
        } else emptyList()

        return Inspection(
            totalFiles = entries.size,
            selectedFiles = selected,
            referencedFiles = referenced.distinct(),
            omittedReadableFiles = (entries.size - selected.size).coerceAtLeast(0)
        )
    }

    fun renderForModel(inspection: Inspection, purpose: String): String = buildString {
        append("\n\n--- Real project inspection (${purpose}) ---\n")
        append("Readable source/config files in ZIP: ${inspection.totalFiles}.\n")
        append("Files inspected in this pass: ${inspection.selectedFiles.size}.\n")
        if (inspection.omittedReadableFiles > 0) append("Additional readable files not included in this pass: ${inspection.omittedReadableFiles}. They were not inspected by the model and must not be claimed as reviewed.\n")
        if (inspection.referencedFiles.isNotEmpty()) append("Real files related by imports/name evidence: ${inspection.referencedFiles.joinToString(", ")}.\n")
        inspection.selectedFiles.forEach { file ->
            append("\n--- ${file.name} ---\n")
            append(file.content)
            append("\n--- End ${file.name} ---\n")
        }
        append("--- End real project inspection ---")
    }
}
