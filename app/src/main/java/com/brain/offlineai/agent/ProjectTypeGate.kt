package com.brain.offlineai.agent

/**
 * Phase 24 (real "confirm project type/platform/language" gate) - closes
 * the second of the two real gaps PROGRESS.md's own "Post-Phase 23
 * bug-fix session" section recorded as genuinely NOT started: a brand-new
 * (no attached project ZIP) code-creation request used to leave picking
 * Kotlin vs Python vs web etc entirely to the small on-device model's own
 * guess from the message text, with no real confirm step.
 *
 * Same conservative, no-ungrounded-guess posture every other real gate in
 * this app already established ([com.brain.offlineai.ui.tasks.TaskSplitter],
 * [com.brain.offlineai.ui.normalize.InputNormalizer], [AgentClarificationGate],
 * [com.brain.offlineai.agent.WebSearchTrigger]): this is a plain,
 * deterministic keyword check over the user's own real message text -
 * never a model call asking "which platform did they mean?". A false
 * negative (a creation request this gate doesn't recognize) just means an
 * ordinary generation runs, the same safe default every other gate in this
 * app falls back to.
 */
object ProjectTypeGate {

    private val CREATION_KEYWORDS = listOf(
        "build", "create", "make me", "make a", "develop", "design", "banao",
        "bana", "banaye", "banani", "banane", "bana do", "banado",
        "बना", "बनाओ", "बनानी", "बनाने",
        "write me", "generate a", "generate an"
    )

    private val BUILD_TARGET_WORDS = listOf(
        "app", "application", "apk", "website", "web app", "webapp", "program",
        "script", "tool", "project", "game", "bot", "api", "backend",
        "server", "system"
    )

    /** Real, fixed set of platform/language signals - a hit on any of these means the request already isn't ambiguous. */
    val PLATFORM_KEYWORDS = listOf(
        "kotlin", "java", "android", "python", "javascript", "js", "typescript",
        "node", "react", "vue", "angular", "html", "css", "flutter", "dart",
        "swift", "ios", "c++", "c#", "csharp", "rust", "golang", " go ",
        "php", "ruby", "django", "flask", "spring", ".net", "unity",
        "jetpack compose", "compose", "web", "website", "web app", "webapp"
    )

    /** Phase 25 - real, public aliases of this file's own existing keyword lists above, reused (not duplicated) by [WebSearchTrigger.buildTargetSearchQuery] and by [isCreationRequest] below - same "don't duplicate an existing abstraction before reusing it" reasoning [ContextManager]'s own doc already gives. */
    val CREATION_KEYWORDS_PUBLIC: List<String> get() = CREATION_KEYWORDS
    val BUILD_TARGET_WORDS_PUBLIC: List<String> get() = BUILD_TARGET_WORDS

    data class Ambiguity(val question: String)

    /**
     * Phase 25 (real multi-file pipeline trigger check) - the exact same
     * real "creation intent + build-target word" test [detectAmbiguity]
     * already computes internally, exposed as its own function so
     * [com.brain.offlineai.ui.screens.chat.ChatViewModel] can decide
     * "is this genuinely a build request at all?" (to try
     * [PlanningEngine]) without re-deriving or duplicating the same real
     * keyword logic a second time.
     */
    fun isCreationRequest(text: String): Boolean {
        val lower = text.lowercase()
        return CREATION_KEYWORDS.any { lower.contains(it) } && BUILD_TARGET_WORDS.any { lower.contains(it) }
    }

    /**
     * Multi-file generation is expensive on-device. A normal "make a web
     * app" request must not be routed through planning unless the user
     * actually asks for separate files/project packaging.
     *
     * Bug fix (user report - "web app bolte hi sab fail ho jata hai") -
     * this check was always English-only, even though [CREATION_KEYWORDS]
     * above already accepts Hinglish/Hindi ("banao", "बनाओ", ...). Added
     * the equivalent Hinglish/Hindi phrasings so a real, explicit
     * multi-file ask in Hinglish is recognized the same honest way an
     * English one already was - not a second, stricter bar just because
     * of the language used.
     */
    /** True only for explicit web-site/app creation wording. */
    fun isWebAppCreationRequest(text: String): Boolean {
        val lower = text.lowercase()
        return isCreationRequest(text) && listOf(
            "web app", "webapp", "website", "web site", "html css", "html/js", "html css js"
        ).any { lower.contains(it) }
    }

    /** Real coding prompts should be sampled more conservatively than casual prose. */
    fun isCodeCreationRequest(text: String): Boolean =
        isCreationRequest(text) || listOf("code", "coding", "program", "script", "html", "css", "javascript", "kotlin", "python", "java").any { text.lowercase().contains(it) }


    /**
     * True when the user's own wording explicitly asks for a generated
     * file/project artifact now. Ordinary code generation is deliberately
     * excluded: writing code in chat must not silently create files.
     */

    /** True when the user explicitly asks for an explanation around generated code. */
    fun explicitlyRequestsCodeExplanation(text: String): Boolean {
        val lower = text.lowercase()
        return listOf(
            "explain", "explanation", "why", "how does", "how it works",
            "samjha", "samjhao", "samjha do", "kaise", "kyun", "क्यों", "समझा", "समझाओ"
        ).any { lower.contains(it) }
    }

    /** Code-only requests can safely finish at the first complete fenced block. */
    fun shouldStopAfterFirstCodeBlock(text: String): Boolean =
        isCodeCreationRequest(text) && !explicitlyRequestsCodeExplanation(text)

    fun explicitlyRequestsArtifactOutput(text: String): Boolean {
        val lower = text.lowercase()
        val outputWords = listOf(
            "file me", "file mein", "fill me", "fill mein", "as a file", "save as file", "save it as a file",
            "isko file", "isko ek file", "file bana", "file do", "file me do",
            "artifact", "download", "save it", "save this", "export it",
            "create a file", "create file", "make a file", "generate a file",
            "file banao", "file banado", "file bana do", "फाइल में", "फाइल बना",
            "फाइल दो", "फाइल में दो", "डाउनलोड"
        )
        if (outputWords.any { lower.contains(it) }) return true
        return Regex("\\b(?:html?|css|js|ts|jsx|tsx|py|python|kt|kotlin|java|json|xml|md|txt)\\s+file\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    fun explicitlyRequestsMultipleFiles(text: String): Boolean {
        val lower = text.lowercase()
        val explicitMultiFileWords = listOf(
            "multi-file", "multifile", "multi file", "multiple files", "multiple file",
            "separate files", "separate html css js", "html css js files", "html css and js files",
            "html, css and javascript", "html css javascript files", "split into files",
            "file-by-file", "one file at a time", "zip project", "project files", "project zip",
            "alag alag files", "alag files", "alag html css js", "multiple file", "sabhi files",
            "har file alag", "poora project", "puri project", "zip me",
            "अलग अलग फाइल", "मल्टीपल फाइल", "पूरा प्रोजेक्ट", "हर फाइल अलग"
        )
        // A plain creation request is intentionally NOT multi-file by default.
        // "create web app" follows the documented simple web path (one
        // self-contained HTML artifact). The expensive planner is reserved
        // for an explicit request for separate/multiple/project files.
        if (explicitMultiFileWords.any { lower.contains(it) }) return true
        val webTripleFileRequest =
            (lower.contains("html") && lower.contains("css") && (lower.contains("javascript") || lower.contains("js"))) &&
            listOf("separate", "files", "file", "split", "project", "zip", "alag", "अलग").any { lower.contains(it) }
        return webTripleFileRequest
    }

    /**
     * Real, deterministic check: only fires when the message genuinely
     * combines a creation-intent word with a real build-target word (so a
     * plain "explain how apps work" is untouched) AND names no real
     * platform/language - never a model guess about what was meant.
     */
    fun detectAmbiguity(text: String): Ambiguity? {
        if (!isCreationRequest(text)) return null
        val lower = text.lowercase()
        if (PLATFORM_KEYWORDS.any { lower.contains(it) }) return null
        return Ambiguity(
            "I can build this, but I need the target platform/language first. For example: Android/Kotlin, web/HTML-CSS-JS, Python, Node.js, Flutter/Dart, or another platform. Reply with the one you want and I'll continue this same task."
        )
    }

    /** Real, narrow check for whether a follow-up answer actually names a real platform - never a model guess about intent. */
    fun answerNamesPlatform(text: String): Boolean {
        val lower = text.lowercase()
        return PLATFORM_KEYWORDS.any { lower.contains(it) }
    }
}
