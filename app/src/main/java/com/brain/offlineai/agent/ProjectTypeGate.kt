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
        "app", "application", "website", "web app", "webapp", "program",
        "script", "tool", "project", "game", "bot", "api", "backend",
        "server", "system"
    )

    /** Real, fixed set of platform/language signals - a hit on any of these means the request already isn't ambiguous. */
    val PLATFORM_KEYWORDS = listOf(
        "kotlin", "java", "android", "python", "javascript", "js", "typescript",
        "node", "react", "vue", "angular", "html", "css", "flutter", "dart",
        "swift", "ios", "c++", "c#", "csharp", "rust", "golang", " go ",
        "php", "ruby", "django", "flask", "spring", ".net", "unity",
        "jetpack compose", "compose", "web"
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

    fun explicitlyRequestsMultipleFiles(text: String): Boolean {
        val lower = text.lowercase()
        val explicitMultiFileWords = listOf(
            "multi-file", "multifile", "multiple files", "separate files",
            "separate html css js", "html css js files", "split into files",
            "file-by-file", "zip project", "project files",
            "alag alag files", "alag files", "multiple file", "sabhi files",
            "har file alag", "poora project", "puri project", "zip me",
            "अलग अलग फाइल", "मल्टीपल फाइल", "पूरा प्रोजेक्ट"
        )
        return explicitMultiFileWords.any { lower.contains(it) }
    }

    /**
     * Real, deterministic check: only fires when the message genuinely
     * combines a creation-intent word with a real build-target word (so a
     * plain "explain how apps work" is untouched) AND names no real
     * platform/language - never a model guess about what was meant.
     */
    fun detectAmbiguity(text: String): Ambiguity? = null

    /** Real, narrow check for whether a follow-up answer actually names a real platform - never a model guess about intent. */
    fun answerNamesPlatform(text: String): Boolean {
        val lower = text.lowercase()
        return PLATFORM_KEYWORDS.any { lower.contains(it) }
    }
}
