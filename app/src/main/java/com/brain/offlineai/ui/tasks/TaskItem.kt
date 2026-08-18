package com.brain.offlineai.ui.tasks

/**
 * Phase 12 (Multi-task handling engine, Claude-style UI spec section 6) -
 * a real task's own lifecycle. PENDING is the genuine starting state for
 * every task after [com.brain.offlineai.ui.tasks.TaskSplitter] has broken
 * a message down but before its own turn to run - tasks are executed
 * strictly one at a time (spec's "Sequential Execution" requirement, see
 * [com.brain.offlineai.ui.screens.chat.ChatViewModel.runMultiTaskMessage]),
 * so at most one task is ever RUNNING at once. Nothing here is a timer or
 * a fake progress estimate (Rule 10/17) - a status only ever changes when
 * that task's own real [com.brain.offlineai.engine.BrainEngine.generate]
 * call genuinely starts or genuinely finishes (successfully or with a real
 * error).
 */
enum class TaskStatus { PENDING, RUNNING, COMPLETE, FAILED }

/**
 * One real task extracted from a single user message.
 *
 * [index] is the task's 1-based position, matching the order the user
 * actually wrote it in (never reordered).
 * [description] is the real, unmodified text of that task - never
 * paraphrased or summarized by a model, since [TaskSplitter] is a plain
 * text split, not an LLM call (see that file's doc for why).
 * [resultMessageId] is null until the task genuinely starts - once it
 * does, it points at the real bot [com.brain.offlineai.ui.screens.chat.ChatMessage]
 * id that task's own full response streams into (the same
 * `streamRealResponse` flow every single-task message already uses), so
 * a completed task's full real answer is always just the message right
 * below the breakdown card - nothing is duplicated or summarized again.
 */
data class TaskItem(
    val index: Int,
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val resultMessageId: Long? = null
)
