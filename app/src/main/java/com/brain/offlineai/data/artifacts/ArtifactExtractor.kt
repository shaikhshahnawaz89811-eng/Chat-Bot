package com.brain.offlineai.data.artifacts

/** One real fenced code block found in a genuinely completed response - not yet written to disk. */
data class ArtifactCandidate(val fileName: String, val content: String)

/**
 * Phase 11 - real, separate extraction pass over a *completed* generation's
 * full text, run once from `ChatViewModel.streamRealResponse`'s
 * `onCompletion` (never mid-stream - only a genuinely finished response's
 * fences are turned into real files). This is intentionally its own
 * function rather than reusing `ChatViewModel.renderMessage`'s fence
 * handling: that function's job is picking the *first* fence to drive the
 * live CODING/CODE_DONE bubble while tokens are still streaming, and it
 * stays untouched (Document-Editing Convention) - this one finds *every*
 * fence in the final text, since a real response can legitimately contain
 * more than one code block/file.
 */
object ArtifactExtractor {

    private val fencePattern = Regex("```([a-zA-Z0-9+#._-]*)\\n([\\s\\S]*?)```")

    /** Real language-tag -> real file extension, only for tags an actual model reply is likely to use. */
    private val extensionForLanguage = mapOf(
        "kotlin" to "kt", "kt" to "kt",
        "java" to "java",
        "python" to "py", "py" to "py",
        "javascript" to "js", "js" to "js",
        "typescript" to "ts", "ts" to "ts",
        "json" to "json",
        "xml" to "xml",
        "html" to "html",
        "css" to "css",
        "c" to "c",
        "cpp" to "cpp", "c++" to "cpp",
        "bash" to "sh", "sh" to "sh", "shell" to "sh",
        "yaml" to "yaml", "yml" to "yaml",
        "sql" to "sql",
        "gradle" to "gradle",
        "markdown" to "md", "md" to "md"
    )

    /**
     * Returns one [ArtifactCandidate] per real fenced block in [finalText],
     * in order. An empty list (the common case - most replies are plain
     * prose) means no artifacts are created for that message - no fenced
     * block, no file, no fabricated placeholder.
     */
    fun extract(finalText: String): List<ArtifactCandidate> {
        val matches = fencePattern.findAll(finalText).toList()
        return matches.mapIndexedNotNull { index, match ->
            val languageTag = match.groupValues[1].trim().lowercase()
            val body = match.groupValues[2]
            if (body.isBlank()) return@mapIndexedNotNull null
            val extension = extensionForLanguage[languageTag] ?: "txt"
            val fileName = "artifact_${index + 1}.$extension"
            ArtifactCandidate(fileName, body)
        }
    }
}
