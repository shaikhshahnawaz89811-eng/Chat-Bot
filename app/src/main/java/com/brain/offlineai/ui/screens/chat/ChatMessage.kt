package com.brain.offlineai.ui.screens.chat

import com.brain.offlineai.data.artifacts.ArtifactInfo
import com.brain.offlineai.data.attachments.AttachmentInfo
import com.brain.offlineai.ui.multimodal.AttachmentRoute
import com.brain.offlineai.ui.process.ProcessStep
import com.brain.offlineai.ui.tasks.TaskItem

/** How a bot message should currently render. Mirrors the 4 chat-interface
 *  states shown in the mockup (screens 1-4): plain text, live thinking
 *  checklist, live coding block, and generating-response waveform.
 *  CODE_DONE is the completed counterpart of CODING (Rule 8 Part A -
 *  CODING's natural counterpart was missing: it never had a "finished"
 *  state to hand off to, so it stayed shown forever even after a real
 *  generation had fully completed).
 *  PROCESS (Phase 8, new Claude-style UI spec) is the general-purpose
 *  live-process-card state - it carries a real [ChatMessage.processSteps]
 *  list rendered via LiveProcessCard, using the full [ProcessMarking] set
 *  instead of the old THINKING-only checklist.
 *  TASK_LIST (Phase 12, multi-task handling engine, spec section 6) is the
 *  real breakdown-checklist state - it carries a real [ChatMessage.tasks]
 *  list rendered via TaskListCard. THINKING/CODING/CODE_DONE/PROCESS are
 *  unchanged and still used exactly as before (Document-Editing Convention
 *  - nothing existing removed or replaced). */
enum class BotMessageState { TEXT, THINKING, CODING, CODE_DONE, GENERATING, SYSTEM_NOTE, PROCESS, TASK_LIST }

data class ChatMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: String,
    val state: BotMessageState = BotMessageState.TEXT,
    val thinkingSteps: List<ThinkingStep> = emptyList(),
    val codeLines: List<String> = emptyList(),
    val generationProgress: Int = 0,
    val processSteps: List<ProcessStep> = emptyList(),
    // Phase 10 (File/ZIP/Image/Video upload flow) - real attachments that
    // were actually copied to app-private storage and genuinely sent with
    // this message. Empty for every message from every earlier phase
    // (Document-Editing Convention - additive field, nothing existing
    // changed).
    val attachments: List<AttachmentInfo> = emptyList(),
    // Phase 11 (Artifact card + ZIP/file output + download flow) - real
    // files this app actually generated from this message's own genuinely
    // completed response (fenced code blocks written to real bytes on
    // disk). Empty for every message from every earlier phase and for any
    // plain-prose reply with no fenced block (Document-Editing Convention -
    // additive field, nothing existing changed).
    val artifacts: List<ArtifactInfo> = emptyList(),
    // Real, small "N created" checklist shown above the artifact card(s) -
    // reuses the same [ProcessStep]/[ProcessMarking] set Phase 8 already
    // introduced (CREATING per real file written, PACKAGING when more than
    // one) instead of inventing a second marking system.
    val artifactSteps: List<ProcessStep> = emptyList(),
    // Phase 12 (Multi-task handling engine, spec section 6) - the real,
    // ordered breakdown for a message TaskSplitter genuinely split into
    // more than one task, with each task's own live status. Empty for
    // every message from every earlier phase and for any ordinary
    // single-instruction message (Document-Editing Convention - additive
    // field, nothing existing changed).
    val tasks: List<TaskItem> = emptyList(),
    // Phase 14 (Multimodal input use-case routing, spec section 8) - the
    // real, already-decided role each of this message's own [attachments]
    // was routed to before generation acted on it (empty when there are no
    // attachments, and for every message from every earlier phase -
    // Document-Editing Convention, additive field only).
    val attachmentRoutes: List<AttachmentRoute> = emptyList()
)

data class ThinkingStep(val label: String, val done: Boolean)
