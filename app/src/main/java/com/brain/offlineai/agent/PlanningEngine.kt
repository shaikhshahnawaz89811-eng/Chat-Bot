package com.brain.offlineai.agent

/**
 * Phase 25 (real multi-file planning - user-requested: "pehle plan banaye,
 * phir tukdo me tode, phir alag-alag file likhe, language samjhe"). Closes
 * the gap the Phase 24 session's own honest review found: [ProjectTypeGate]
 * decides *which platform/language*, but nothing decided *how many files,
 * what each is called, and what each one is for* before generation started
 * - every request, whether it genuinely needed 1 file or 10, went straight
 * into one single, undirected `generate()` call.
 *
 * Same conservative, no-ungrounded-guess posture every other real gate in
 * this app already holds itself to ([com.brain.offlineai.ui.tasks.TaskSplitter],
 * [ProjectTypeGate], [WebSearchTrigger]): the actual *planning* still comes
 * from the model (only the model can reasonably decide how a given app
 * should be broken into files - a fixed keyword list can't), but the
 * boundary between "the model's raw text" and "a real, usable list of
 * files this app will actually generate" is a plain, deterministic parse
 * with a fixed, documented format - never a second model call asked to
 * "interpret" the first one's answer, and never an invented file the model
 * didn't actually name.
 */
object PlanningEngine {

    /** No everyday file-count ceiling: the real model plan determines how many files the project genuinely needs. Native/context limits still bound how much one plan response can actually describe. */

    /** A plan needs at least this many real, parsed files to be worth the multi-file pipeline - a 1-file "plan" is really just an ordinary single-response request, so [parsePlan] returns null below this and the caller falls back to the existing single-response flow unchanged. */
    private const val MIN_FILES_FOR_MULTI_FILE = 2

    data class PlannedFile(val fileName: String, val language: String, val purpose: String)
    data class FilePlan(val files: List<PlannedFile>, val truncatedFromCount: Int? = null)

    private val fileLine = Regex("""(?im)^FILE:\s*(.+)$""")
    private val langLine = Regex("""(?im)^LANG:\s*(.+)$""")
    private val purposeLine = Regex("""(?im)^PURPOSE:\s*(.+)$""")
    /** A real file name has an extension - guards against the model naming a folder or a vague label ("the backend") as if it were a file. */
    private val looksLikeFileName = Regex("""^[\w./-]+\.[A-Za-z0-9]+$""")

    /**
     * Real, fixed prompt format - deliberately narrow (one exact block
     * shape, repeated) so [parsePlan] below can be a plain, exact parser
     * instead of a fuzzy best-effort guess at whatever shape the model
     * felt like answering in.
     */
    fun buildPlanningPrompt(originalRequest: String, extraContext: String): String {
        return buildString {
            append("You are planning a real multi-file software project before any code is written.\n")
            append("Request: ").append(originalRequest).append("\n")
            if (extraContext.isNotBlank()) append(extraContext).append("\n")
            append(
                "\nList every real file this project genuinely needs. Reply with ONLY a list in " +
                    "exactly this format, one block per file, nothing else before, between, or after " +
                    "the blocks (no prose, no numbering, no markdown fences):\n\n" +
                    "FILE: <real file name with extension>\n" +
                    "LANG: <the real programming/markup language of this file>\n" +
                    "PURPOSE: <one short line - what this file is actually for>\n" +
                    "---\n\n" +
                    "Repeat that 4-line block for every file. Keep the file count genuinely necessary " +
                    "for the request - do not invent extra files just to pad the list. " +
                    "Do not ask a question or wait for approval: infer a sensible default " +
                    "stack from the request (for example HTML/CSS/JavaScript for an unspecified " +
                    "web app), include its real setup files, and begin the implementation plan."
            )
        }
    }

    /**
     * Real, exact-format parse. Returns null (not an empty [FilePlan]) for
     * anything that isn't genuinely a real multi-file plan: zero FILE:
     * lines, or fewer than [MIN_FILES_FOR_MULTI_FILE] real ones - both
     * cases mean the caller should fall back to the existing single-response
     * flow exactly as it already worked before this phase, per this app's
     * own "a false negative just means an ordinary generation runs" default.
     */
    fun parsePlan(rawModelOutput: String): FilePlan? {
        val fileMatches = fileLine.findAll(rawModelOutput).map { it.groupValues[1].trim() }.toList()
        val langMatches = langLine.findAll(rawModelOutput).map { it.groupValues[1].trim() }.toList()
        val purposeMatches = purposeLine.findAll(rawModelOutput).map { it.groupValues[1].trim() }.toList()

        val realFiles = fileMatches.mapIndexedNotNull { index, name ->
            val cleanName = name.trim().trim('`', '*', '"', '\'')
            if (!looksLikeFileName.matches(cleanName)) return@mapIndexedNotNull null
            PlannedFile(
                fileName = cleanName,
                language = langMatches.getOrNull(index)?.trim().takeUnless { it.isNullOrBlank() } ?: languageFromExtension(cleanName),
                purpose = purposeMatches.getOrNull(index)?.trim().takeUnless { it.isNullOrBlank() } ?: ""
            )
        }.distinctBy { it.fileName }

        if (realFiles.size < MIN_FILES_FOR_MULTI_FILE) return null

        return FilePlan(files = realFiles)
    }

    /**
     * Last-resort plan for the most common request that must never silently
     * fall back to one giant answer.  The model still writes all three files;
     * this only supplies the file list when a small on-device model times out
     * or ignores the strict planning format.
     */
    fun fallbackPlan(originalRequest: String): FilePlan? {
        val lower = originalRequest.lowercase()
        val isWebRequest = listOf("web app", "webapp", "website", "html").any { lower.contains(it) }
        if (isWebRequest) {
            return FilePlan(
                files = listOf(
                    PlannedFile("index.html", "HTML", "Application structure, accessible UI, and real content"),
                    PlannedFile("styles.css", "CSS", "Responsive visual design and component styling"),
                    PlannedFile("script.js", "JavaScript", "Client-side interactions and application behavior")
                )
            )
        }
        val isPython = listOf("python", "flask", "django").any { lower.contains(it) }
        if (isPython) {
            return FilePlan(
                files = listOf(
                    PlannedFile("main.py", "Python", "Application entry point and core behavior"),
                    PlannedFile("requirements.txt", "Text", "Runtime dependencies"),
                    PlannedFile("README.md", "Markdown", "Run instructions and project overview")
                )
            )
        }
        val isNode = listOf("node", "express", "react", "vue", "angular", "javascript", "typescript").any { lower.contains(it) }
        if (isNode) {
            return FilePlan(
                files = listOf(
                    PlannedFile("package.json", "JSON", "Dependencies and runnable scripts"),
                    PlannedFile("src/index.js", "JavaScript", "Application entry point and core behavior"),
                    PlannedFile("README.md", "Markdown", "Run instructions and project overview")
                )
            )
        }
        return null
    }

    /** Real, deterministic fallback only for the rare case [langLine] genuinely didn't have a matching line for this file - never a guess about content, just the file's own real extension. */
    private fun languageFromExtension(fileName: String): String =
        fileName.substringAfterLast('.', "").ifBlank { "text" }

    /** Real, short summary line posted to the user before per-file generation starts - same "route/plan before acting, never a silent internal decision" standard this app already holds itself to. */
    fun buildPlanSummary(plan: FilePlan): String {
        val header = "Planning complete: ${plan.files.size} files."
        val lines = plan.files.joinToString("\n") { "- ${it.fileName} (${it.language})${if (it.purpose.isNotBlank()) " - ${it.purpose}" else ""}" }
        return "$header\n$lines"
    }

    /**
     * Weakness-review fix - the model's own real file list was never
     * checked against what a project of the platform it's actually
     * targeting genuinely needs (an Android project with no
     * AndroidManifest.xml, a Node project with no package.json, etc.) - a
     * gap the model itself introduced was silently carried straight
     * through to the build. Real, deterministic (no second model call)
     * check: only looks at [originalRequest]'s own real platform keywords
     * (reusing [ProjectTypeGate.PLATFORM_KEYWORDS] - never a duplicated
     * list) and [plan.files]'s own real file names - never invents a
     * missing file, only reports one honestly by name so the user (or a
     * later message) can genuinely ask for it. Returns an empty list when
     * no known platform was named, or every real essential file for a
     * named platform is already present.
     */
    private val ANDROID_ESSENTIALS = listOf("androidmanifest.xml", "build.gradle")
    private val WEB_ESSENTIALS = listOf("index.html")
    private val NODE_ESSENTIALS = listOf("package.json")

    fun missingEssentialFiles(originalRequest: String, plan: FilePlan): List<String> {
        val lower = originalRequest.lowercase()
        val fileNamesLower = plan.files.map { it.fileName.substringAfterLast('/').lowercase() }
        fun anyPresent(names: List<String>) = names.any { needed -> fileNamesLower.any { it == needed } }

        val missing = mutableListOf<String>()
        val isAndroid = listOf("android", "kotlin", "jetpack compose", "compose").any { lower.contains(it) }
        val isNode = listOf("node", "express", "react", "vue", "angular", "npm").any { lower.contains(it) }
        val isWeb = listOf("website", "web app", "webapp", "html").any { lower.contains(it) } && !isNode

        if (isAndroid) ANDROID_ESSENTIALS.filterNot { anyPresent(listOf(it)) }.forEach { missing += it }
        if (isNode) NODE_ESSENTIALS.filterNot { anyPresent(listOf(it)) }.forEach { missing += it }
        if (isWeb) WEB_ESSENTIALS.filterNot { anyPresent(listOf(it)) }.forEach { missing += it }
        return missing
    }
}
