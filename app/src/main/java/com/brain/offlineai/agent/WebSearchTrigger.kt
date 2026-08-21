package com.brain.offlineai.agent

/**
 * Phase 22 (Master Plan v2, revised scope - PROGRESS.md's own Phase 19
 * Plan section, "Phase 22 note", records the exact real trigger rule this
 * object implements). A plain, deterministic Kotlin function over the
 * user's own real message text - never a model call asking "should I
 * search?" - the same conservative, no-ungrounded-guess posture
 * [TaskSplitter]/[InputNormalizer]/[AgentClarificationGate] already
 * established for themselves. A search is never triggered "just because"
 * on an ordinary, already-clear message; both real checks below are
 * intentionally narrow, so a false negative (a message that could have
 * used a search but didn't trigger one) just means an ordinary offline
 * generation runs - the same safe default this app has always had.
 */
object WebSearchTrigger {

    // Real creation-intent keywords (English + common Hinglish, matching
    // this project's own existing keyword lists e.g. TaskSplitter's
    // sequential connectors, ZipEditResolver's edit-intent words).
    private val CREATE_KEYWORDS = listOf(
        "build", "create", "make me", "develop", "generate", "banao", "banade", "bana do"
    )

    // Real signals that the request is underspecified/needs current,
    // outside-the-model information to act on safely - not a general
    // "sounds vague" guess, a fixed, narrow phrase list only.
    private val UNFAMILIAR_SIGNALS = listOf(
        "latest", "current version", "newest", "recommended library", "best library",
        "which library", "which framework", "up to date", "up-to-date", "most recent"
    )

    // Real inspection-intent keywords for the existing-project case - the
    // user is asking what something *is*, not asking for an edit (edit
    // intent already has its own, separate real gate - see
    // ZipEditResolver/AgentClarificationGate, both untouched by this file).
    private val INSPECT_KEYWORDS = listOf(
        "what is", "what does", "how does", "explain", "look up", "search for", "find out about",
        "error", "bug", "issue", "problem", "galt", "galat", "dhoondo", "dhundo", "dundo",
        "dekho", "check karo", "fix", "fix karo"
    )

    /**
     * Real trigger case 1 - "New-project understanding" (PROGRESS.md's
     * own heaviest real use case). Only fires when the message genuinely
     * contains BOTH a real creation-intent keyword AND a real signal that
     * the request needs current/outside information - a plain "build me
     * a calculator app" (fully answerable from the offline model alone)
     * never triggers this.
     */
    fun newProjectSearchQuery(normalizedText: String): String? {
        val lower = normalizedText.lowercase()
        val hasCreateIntent = CREATE_KEYWORDS.any { lower.contains(it) }
        val hasUnfamiliarSignal = UNFAMILIAR_SIGNALS.any { lower.contains(it) }
        if (!hasCreateIntent || !hasUnfamiliarSignal) return null
        return normalizedText.trim()
    }

    /**
     * Real trigger case 2 - "Existing-project inspection" (PROGRESS.md's
     * own situational, lighter real use case). Only fires when the
     * message brought a real ZIP attachment this same turn AND contains a
     * real inspection-intent keyword - a plain ZIP upload with no such
     * keyword takes Phase 14's existing listing/routing path unchanged,
     * completely unaffected by this object.
     */
    fun existingProjectSearchQuery(normalizedText: String, hasZipAttachment: Boolean): String? {
        if (!hasZipAttachment) return null
        val lower = normalizedText.lowercase()
        if (INSPECT_KEYWORDS.none { lower.contains(it) }) return null
        return normalizedText.trim()
    }

    /**
     * Phase 25 (user request - "user jo bhi bole jaise website ya apk vo
     * net search bhi karta hai, agar na ho toh koi bhi nahin, agar hai toh
     * search kare or jankari nikale"). A real, wider trigger than
     * [newProjectSearchQuery] above: fires on genuine creation intent +
     * a real build-target word ([ProjectTypeGate]'s own fixed lists,
     * reused rather than duplicated) with NO [UNFAMILIAR_SIGNALS]
     * requirement - "build me a website" now genuinely searches too, not
     * just "build me a website with the latest framework". Still only
     * ever attempted with a real stored key AND real device connectivity
     * (see [com.brain.offlineai.data.websearch.WebSearchRepository] /
     * [com.brain.offlineai.data.websearch.ConnectivityChecker]) - "agar na
     * ho toh koi bhi nahin" is exactly what that existing real check
     * already gives: no key or no internet means this silently does zero
     * extra work and generation stays fully offline, same as before.
     */
    fun buildTargetSearchQuery(normalizedText: String): String? {
        val lower = normalizedText.lowercase()
        val hasCreationIntent = ProjectTypeGate.CREATION_KEYWORDS_PUBLIC.any { lower.contains(it) }
        val hasBuildTarget = ProjectTypeGate.BUILD_TARGET_WORDS_PUBLIC.any { lower.contains(it) }
        if (!hasCreationIntent || !hasBuildTarget) return null
        return normalizedText.trim()
    }
}
