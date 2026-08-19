package com.brain.offlineai.agent

/**
 * Phase 23 - real, minimal status set for a [ThermalPauseEntity] row,
 * same shape as [AgentTaskStatus] (that enum's own doc already explains
 * why this project keeps each task kind's status set small and specific
 * rather than one shared enum every kind reuses loosely).
 */
enum class ThermalPauseStatus {
    /** The real device thermal status genuinely reached SEVERE+ mid-answer and this task is genuinely waiting for it to cool back down. */
    PAUSED,
    /** The real device thermal status genuinely dropped back to a safe level and this task's generation genuinely resumed. */
    RESUMED
}
