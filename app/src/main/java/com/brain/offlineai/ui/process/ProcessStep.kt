package com.brain.offlineai.ui.process

/**
 * One real, reportable unit of agent work (Rule 1 - every step here must
 * correspond to a real action that actually started/finished; nothing is
 * shown as "done" that didn't genuinely happen, Rule 10/17).
 *
 * [label] optionally overrides [ProcessMarking.runningLabel] /
 * [ProcessMarking.completedLabel] when the generic marking text needs a
 * concrete detail (e.g. "Reading files..." -> "Reading ChatViewModel.kt").
 * Left null, the marking's own generic label is used.
 */
data class ProcessStep(
    val id: Long,
    val marking: ProcessMarking,
    val status: ProcessStepStatus,
    val label: String? = null
) {
    val displayLabel: String
        get() = label ?: when (status) {
            ProcessStepStatus.RUNNING -> marking.runningLabel
            ProcessStepStatus.COMPLETE -> marking.completedLabel
            ProcessStepStatus.FAILED -> "${marking.displayName} failed"
        }
}
