package com.brain.offlineai.ui.multimodal

import com.brain.offlineai.data.attachments.AttachmentInfo
import com.brain.offlineai.data.attachments.AttachmentKind

/**
 * Phase 14 (Multimodal input use-case routing, Claude-style UI spec
 * section 8) - the fixed 5-role set the spec's own routing table lists:
 * reference material, a requirements/spec document, evidence of a bug,
 * evidence of real app/device behavior, or existing source content to
 * read/extract/build on. A real attachment is genuinely routed into one
 * of these before this app acts on it (see [classifyAttachmentRole]) -
 * nothing outside this fixed list is invented ad hoc, same "fixed marking
 * set, no orphan/no ad-hoc addition" convention
 * [com.brain.offlineai.ui.process.ProcessMarking] already follows.
 */
enum class UseCaseRole(val label: String) {
    REFERENCE("Reference"),
    REQUIREMENTS("Requirements"),
    BUG_EVIDENCE("Bug Evidence"),
    BEHAVIOR_EVIDENCE("Behavior Evidence"),
    SOURCE("Source")
}

/**
 * One real, already-decided routing outcome for one real attachment -
 * [reason] is always filled in (never blank) so the routing is genuinely
 * visible to the user before generation runs (spec §8 - "route before
 * acting"), not a silent internal decision.
 */
data class AttachmentRoute(
    val attachmentId: String,
    val fileName: String,
    val kind: AttachmentKind,
    val role: UseCaseRole,
    val reason: String
)

/**
 * Real, deterministic routing - no real or simulated model call decides
 * this. Same no-fabrication reasoning
 * [com.brain.offlineai.ui.normalize.InputNormalizer] and
 * [com.brain.offlineai.ui.tasks.TaskSplitter] already document for their
 * own text-only checks applies here: asking the model itself to "decide"
 * what an attachment is for, before that attachment has even reached a
 * real generation call, would be an ungrounded guess dressed up as a
 * routing decision. Two real signals only:
 *  1. the attachment's own real [AttachmentKind] (Phase 10's real
 *     MIME-type/extension classification) - gives each kind a sensible
 *     default role,
 *  2. a fixed, small set of case-insensitive keyword phrases in the
 *     user's own message text - overrides the default only when the
 *     user's own words genuinely say what the attachment is for.
 * A keyword hit always wins over the kind default (it's the more specific
 * real signal). When more than one role's keywords appear in the same
 * message, a fixed, documented precedence applies (bug > behavior >
 * requirements > reference > source) rather than a random pick - the same
 * "conservative, documented, no guessing" posture
 * [com.brain.offlineai.ui.normalize.InputNormalizer]'s own conflict/vague
 * checks already use.
 */
fun classifyAttachmentRole(attachment: AttachmentInfo, messageText: String): AttachmentRoute {
    val lower = messageText.lowercase()
    fun has(vararg phrases: String) = phrases.any { phrase ->
        Regex("""\b${Regex.escape(phrase)}\b""").containsMatchIn(lower)
    }

    val isBugEvidence = has("bug", "crash", "crashes", "crashed", "error", "broken", "not working", "doesn't work", "fails", "exception")
    val isBehaviorEvidence = has("behavior", "behaviour", "reproduce", "repro", "when i", "steps to reproduce", "recording")
    val isRequirements = has("spec", "specification", "requirement", "requirements", "should do", "needs to", "must have", "build this")
    val isReference = has("reference", "example", "like this", "match this", "design", "mockup", "look like", "style guide")
    val isSource = has("use this code", "here's the file", "here is the file", "extract", "existing code", "as source", "unzip", "contents of")

    val (role, reason) = when {
        isBugEvidence -> UseCaseRole.BUG_EVIDENCE to "message mentions a bug/error/crash"
        isBehaviorEvidence -> UseCaseRole.BEHAVIOR_EVIDENCE to "message describes reproducing real app/device behavior"
        isRequirements -> UseCaseRole.REQUIREMENTS to "message describes a spec/requirement to build against"
        isReference -> UseCaseRole.REFERENCE to "message asks to match/follow this as a reference"
        isSource -> UseCaseRole.SOURCE to "message asks to use/extract this as source content"
        else -> defaultRoleFor(attachment.kind) to
            "no explicit routing words in the message - using the default role for a ${attachment.kind.name.lowercase()} attachment"
    }

    return AttachmentRoute(attachment.id, attachment.fileName, attachment.kind, role, reason)
}

/** Real, sensible per-kind default when the message text itself gives no explicit routing signal. */
private fun defaultRoleFor(kind: AttachmentKind): UseCaseRole = when (kind) {
    AttachmentKind.IMAGE -> UseCaseRole.REFERENCE
    AttachmentKind.VIDEO -> UseCaseRole.BEHAVIOR_EVIDENCE
    AttachmentKind.ZIP -> UseCaseRole.SOURCE
    AttachmentKind.FILE -> UseCaseRole.SOURCE
}
