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
     * Bug fix (user request) - a real filename directly in the fence tag
     * (e.g. ```` ```MainActivity.kt ```` or ```` ```activity_main.xml ````)
     * is a genuine name-plus-extension pair, not a language tag - matched
     * only when it actually has that shape, so an ordinary language tag
     * like `kotlin` or `xml` never accidentally matches this and falls
     * through to the existing generic-naming path unchanged.
     */
    private val filenameTagPattern = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9]+$")

    /**
     * Returns one [ArtifactCandidate] per real fenced block in [finalText],
     * in order. An empty list (the common case - most replies are plain
     * prose) means no artifacts are created for that message - no fenced
     * block, no file, no fabricated placeholder.
     */
    /**
     * Deterministic extractor for a simple web-app reply. A plain "create web app"
     * request is a single-file task, so only a real HTML/HTM fenced block is
     * promoted to an artifact. CSS/JS/bash blocks that the small model may
     * accidentally emit are left in the visible response instead of silently
     * becoming extra files. Explicit multi-file web requests continue to use
     * [extract] through the multi-file pipeline.
     */
    fun extractWebApp(finalText: String): List<ArtifactCandidate> =
        extract(finalText).filter { candidate ->
            candidate.fileName.endsWith(".html", ignoreCase = true) ||
                candidate.fileName.endsWith(".htm", ignoreCase = true)
        }.take(1)

    fun extract(finalText: String): List<ArtifactCandidate> {
        val matches = fencePattern.findAll(finalText).toList()
        // Real names already used earlier in this same response - a model
        // that genuinely repeats a filename across two separate blocks
        // (or names a real file the same as another block's language-tag
        // fallback) never silently overwrites/collides in the eventual
        // real ZIP entry list; a later duplicate falls back to the
        // existing generic naming instead.
        val usedNames = mutableSetOf<String>()
        return matches.mapIndexedNotNull { index, match ->
            val rawTag = match.groupValues[1].trim()
            val body = match.groupValues[2]
            if (body.isBlank()) return@mapIndexedNotNull null
            var fileName = resolveFileName(rawTag, index, body)
            if (!usedNames.add(fileName)) {
                val languageTag = rawTag.lowercase()
                val extension = extensionForLanguage[languageTag] ?: fileName.substringAfterLast('.', "txt")
                fileName = "artifact_${index + 1}.$extension"
                usedNames.add(fileName)
            }
            ArtifactCandidate(fileName, body)
        }
    }

    /**
     * A real filename the model itself wrote in the fence tag is used
     * as-is (case preserved - `MainActivity.kt` stays `MainActivity.kt`,
     * never lowercased into something that won't match a real Android
     * class name). A normal language tag maps to a deterministic extension;
     * when a small model omits the language tag entirely, strong syntax
     * markers are used before falling back to `artifact_N.txt`.
     */
    private fun resolveFileName(rawTag: String, index: Int, body: String): String {
        if (rawTag.isNotBlank() && filenameTagPattern.matches(rawTag)) return rawTag
        val languageTag = rawTag.lowercase()
        return when (languageTag) {
            "html" -> "index.html"
            "css" -> "styles.css"
            "javascript", "js" -> "script.js"
            "typescript", "ts" -> "script.ts"
            "kotlin", "kt" -> "Main.kt"
            "java" -> "Main.java"
            "python", "py" -> "main.py"
            "json" -> "data.json"
            "xml" -> "layout.xml"
            "bash", "sh", "shell" -> "script.sh"
            else -> {
                // Small local models sometimes omit the fence language entirely.
                // Infer only from strong, deterministic syntax markers so a real
                // Python/HTML/JS response does not become artifact_N.txt.
                val lower = body.lowercase()
                when {
                    "<!doctype html" in lower || "<html" in lower || "<head" in lower || "<body" in lower -> "index.html"
                    "def " in lower || "print(" in lower || "if __name__" in lower || "from " in lower && " import " in lower -> "main.py"
                    "function " in lower || "const " in lower || "let " in lower || "=>" in body -> "script.js"
                    "fun " in lower || "val " in lower && "var " in lower -> "Main.kt"
                    "public static void main" in lower || "system.out." in lower -> "Main.java"
                    "{\n" in body && ":" in body -> "data.json"
                    else -> {
                        val extension = extensionForLanguage[languageTag] ?: "txt"
                        "artifact_${index + 1}.$extension"
                    }
                }
            }
        }
    }
}
