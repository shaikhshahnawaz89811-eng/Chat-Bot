package com.brain.offlineai.agent

/**
 * Master Plan v2, section 0 ("Understand -> Inspect -> Plan -> Clarify if
 * needed -> Risk-check -> Implement -> Build -> Test -> Verify -> Audit ->
 * Optimize") and section 2 ("Clarification Gate - mandatory... No silent
 * guessing... while clarification is pending"). Real, minimal status set
 * for the one real task kind this phase (Phase 19) actually produces - see
 * [AgentClarificationGate] - kept intentionally generic (Rule 21 - small,
 * single-purpose) so a later phase's task producer can reuse the same
 * table/status set rather than inventing a second one.
 */
enum class AgentTaskStatus {
    /** A real, specific clarification question was asked and this app is genuinely waiting on the user's next message to resolve it. */
    AWAITING_CLARIFICATION,
    /** The user's next message genuinely resolved the question - real resume, not a restart. */
    RESUMED,
    /**
     * Bug fix (user request) - real counterpart to [RESUMED] (Rule 8 -
     * every construct needs its natural other half): the user's next
     * message did NOT resolve this question (didn't name a real file from
     * the same ZIP) - same "a genuinely different message means the user
     * moved on" reasoning [com.brain.offlineai.ui.screens.chat.ChatViewModel.PendingContinuation]
     * already uses for its own stale-state case. Before this existed, a
     * clarification that didn't get answered just stayed
     * AWAITING_CLARIFICATION in the DB forever with no visible signal to
     * the user that it was ever dropped - a real stale-state gap, not
     * just a cosmetic one (Rule 17 - the row's status must reflect what
     * genuinely happened, not just "exist").
     */
    ABANDONED
}
