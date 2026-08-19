package com.brain.offlineai.agent

import com.brain.offlineai.data.attachments.AttachmentContentReader

/**
 * Master Plan v2, section 2 ("Clarification Gate - mandatory... If the
 * agent cannot reliably understand... which of multiple valid approaches
 * the user wants... it must stop before modification and ask a concise,
 * specific question... No silent guessing, no random placement...").
 *
 * Implemented for the one real, achievable case this app can resolve
 * today: a single attached ZIP whose target file
 * [com.brain.offlineai.ui.multimodal.ZipEditResolver] could **not**
 * resolve (zero or 2+ real name matches - see that class's own doc)
 * while the user's own words genuinely signal an edit-type request. Same
 * conservative, no-model-guess posture every earlier deterministic gate
 * in this project already uses (TaskSplitter / InputNormalizer /
 * ZipEditResolver / classifyAttachmentRole) - this never asks the model
 * which file is meant; it only asks the *user*, and only when there are
 * genuinely 2+ real candidate files. A plain "what's in this zip"
 * question with no edit-intent keyword is not blocked here - Phase 14's
 * existing listing/routing already answers that honestly without needing
 * to pick one file.
 */
data class ClarificationRequest(val question: String)

object AgentClarificationGate {

    /** Fixed, real edit-intent keyword set - a message needs at least one of these before this gate considers asking. */
    private val EDIT_INTENT_KEYWORDS = listOf(
        "fix", "update", "change", "edit", "modify", "correct", "patch", "rewrite"
    )

    private const val MAX_NAMES_IN_QUESTION = 10

    /**
     * Real, deterministic ambiguity check. Returns a real, specific
     * question only when [entries] contains 2+ real (non-directory) files
     * AND [messageText] contains a real edit-intent keyword. The caller is
     * only expected to invoke this after
     * [com.brain.offlineai.ui.multimodal.ZipEditResolver.resolveEditTarget]
     * has already genuinely failed to find exactly one match - this gate
     * does not duplicate that name-matching itself.
     */
    fun evaluateZipEditAmbiguity(
        entries: List<AttachmentContentReader.ZipEntrySummary>,
        messageText: String,
        zipDisplayName: String
    ): ClarificationRequest? {
        val files = entries.filter { !it.isDirectory }
        if (files.size < 2) return null
        val lower = messageText.lowercase()
        val hasEditIntent = EDIT_INTENT_KEYWORDS.any { lower.contains(it) }
        if (!hasEditIntent) return null
        val names = files.map { it.name.substringAfterLast('/') }.filter { it.isNotBlank() }
        if (names.isEmpty()) return null
        val shown = names.take(MAX_NAMES_IN_QUESTION)
        val suffix = if (names.size > MAX_NAMES_IN_QUESTION) ", + ${names.size - MAX_NAMES_IN_QUESTION} more" else ""
        return ClarificationRequest(
            "$zipDisplayName has ${names.size} files - which one should I edit? " +
                "(${shown.joinToString(", ")}$suffix). Reply with the exact file name."
        )
    }
}
