package com.brain.offlineai.agent

import com.brain.offlineai.data.attachments.AttachmentContentReader

/**
 * Master Plan v2, section 4 ("File/project inspection... identify module/
 * entry points -> map structure -> dependencies... Do not replace or
 * duplicate an existing abstraction before inspecting/reusing it") and
 * Rule 18 ("multi-component" - a real Android build genuinely mixes
 * Kotlin/Java logic, XML layout, and Gradle build-config as separate real
 * components, not one guessed "project language").
 *
 * Built only from a ZIP's own real entry list, already genuinely read by
 * [AttachmentContentReader.listZipEntries] (Phase 14) - no entry content
 * is opened here; this is real structure *inspection*, not editing. A
 * real, deterministic extension -> component-label map, never guessed
 * from a file's name/content.
 */
data class ProjectContext(
    val totalEntries: Int,
    val fileCount: Int,
    val directoryCount: Int,
    /** Real per-component file counts, e.g. {"Kotlin": 42, "XML/Layout": 11, "Gradle build": 3}. */
    val componentBreakdown: Map<String, Int>
)

object ProjectContextLoader {

    /** Fixed, real extension -> component label map (Rule 18's multi-component idea) - not a guess. */
    private val COMPONENT_LABELS = mapOf(
        "kt" to "Kotlin", "kts" to "Gradle build (KTS)", "java" to "Java",
        "xml" to "XML/Layout", "gradle" to "Gradle build",
        "json" to "JSON config", "py" to "Python", "js" to "JavaScript",
        "ts" to "TypeScript", "c" to "C", "cpp" to "C++", "h" to "C/C++ header",
        "hpp" to "C/C++ header", "md" to "Docs", "yml" to "YAML", "yaml" to "YAML",
        "properties" to "Config", "toml" to "Config"
    )

    /**
     * Real, bounded structure summary for the ZIP already genuinely copied
     * to [storedPath] (Phase 10's own "file = kaam start" rule still
     * applies - this only reads the entry *list*, same real, already-used
     * capability [AttachmentContentReader.listZipEntries] provides).
     */
    suspend fun load(storedPath: String): ProjectContext {
        val entries = AttachmentContentReader.listZipEntries(storedPath)
        val files = entries.filter { !it.isDirectory }
        val dirs = entries.filter { it.isDirectory }
        val breakdown = mutableMapOf<String, Int>()
        files.forEach { entry ->
            val ext = entry.name.substringAfterLast('.', "").lowercase()
            val label = COMPONENT_LABELS[ext] ?: return@forEach
            breakdown[label] = (breakdown[label] ?: 0) + 1
        }
        return ProjectContext(
            totalEntries = entries.size,
            fileCount = files.size,
            directoryCount = dirs.size,
            componentBreakdown = breakdown
        )
    }
}
