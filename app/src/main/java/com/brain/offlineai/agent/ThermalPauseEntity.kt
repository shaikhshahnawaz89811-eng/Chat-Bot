package com.brain.offlineai.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 23 (Appendix - Mobile Thermal Management, integrated with Phase
 * 19's Task State per the Appendix's own requirement: "a long-running
 * task genuinely resumes after a thermal pause instead of being lost").
 * One real, persisted row per generation genuinely paused mid-answer
 * because the real device thermal status reached SEVERE+ (see
 * [com.brain.offlineai.engine.thermal.ThermalPolicy]).
 *
 * Deliberately its own Room database ([ThermalPauseDatabase], own file) -
 * same one-concern-one-table reasoning [AgentTaskEntity]'s own doc
 * already gives for every other per-concern database in this project
 * (Rule 3): this row's job (resume a genuinely in-progress *generation*,
 * given the real prompt-so-far) is different data than [AgentTaskEntity]'s
 * job (resume a paused *clarification question*, given an attachment to
 * re-target) - folding the two into one table would mean half of each
 * row's columns are always meaningless for the other kind.
 *
 * [continuationPrompt] is the real original prompt plus everything
 * genuinely generated before the pause - the same real resume value
 * [streamRealResponse]'s own `continuationPrompt` var already builds for
 * its in-memory `pendingContinuation` chunk-cap-resume case; this is that
 * same real string, just persisted so it survives process death instead
 * of only living in an in-memory var. Which model/context/thread count to
 * reload is deliberately NOT duplicated into this row - resume re-reads
 * [com.brain.offlineai.engine.ModelFileManager.getLastInstalledModel] and
 * the live [com.brain.offlineai.data.settings.ModelSettingsRepository]
 * instead (Rule 4 - one real source of truth for "which model/settings",
 * not a second, staler copy that could silently drift from what Models/
 * Model Settings now actually show).
 */
@Entity(tableName = "thermal_pauses")
data class ThermalPauseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val continuationPrompt: String,
    val pausedAtStatus: Int,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
