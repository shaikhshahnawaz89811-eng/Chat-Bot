package com.brain.offlineai.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.agent.AgentAuditRepository
import com.brain.offlineai.agent.AgentTaskRepository
import com.brain.offlineai.agent.ProjectTypePauseRepository
import com.brain.offlineai.agent.ThermalPauseRepository
import com.brain.offlineai.data.artifacts.ArtifactRepository
import com.brain.offlineai.data.attachments.AttachmentRepository
import com.brain.offlineai.data.history.ChatHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase 7 - real replacement for the stale `PlaceholderScreen("History",
 * arrivingInPhase = 2)` (see ChatHistoryEntities.kt doc for why that text
 * had gone false). Backed by [ChatHistoryRepository] -> real Room table,
 * same `observeX()` Flow pattern `ApiKeysViewModel` already uses for the
 * API Keys list.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatHistoryRepository(application)

    // Phase 10 - a deleted session's real attachment rows/files would
    // otherwise become orphans on disk with no session left to show them.
    // Additive-only correctness fix: deleteSession's own real, permanent
    // removal now also covers attachments, same "delete really means
    // delete" standard the message/session rows already followed.
    private val attachmentRepository = AttachmentRepository(application)

    // Phase 11 - same real cleanup for artifacts: a deleted session's
    // real generated-file rows/files would otherwise also become orphans.
    private val artifactRepository = ArtifactRepository(application)

    // Phase 19 - same real cleanup for any pending agent task (see
    // AgentClarificationGate): a deleted session's paused clarification
    // question would otherwise become an orphan row with no session left
    // to resume it.
    private val agentTaskRepository = AgentTaskRepository(application)

    // Phase 21 - same real cleanup for the tool-call audit trail (see
    // ToolGateway): a deleted session's real audit rows would otherwise
    // become orphans with no session left to reference them.
    private val agentAuditRepository = AgentAuditRepository(application)

    // Phase 23 - same real cleanup for a paused thermal-throttled
    // generation (see ThermalPauseEntity): a deleted session's paused
    // task would otherwise become an orphan row with no session left to
    // resume it, same reasoning agentTaskRepository's own cleanup above
    // already follows.
    private val thermalPauseRepository = ThermalPauseRepository(application)

    // Phase 24 - real cleanup for a deleted session's real project-type-gate row.
    private val projectTypePauseRepository = ProjectTypePauseRepository(application)

    val sessions = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Rule 3 "delete" - a real, permanent removal, gated behind the screen's own confirm dialog. */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            attachmentRepository.deleteForSession(sessionId)
            artifactRepository.deleteForSession(sessionId)
            agentTaskRepository.deleteForSession(sessionId)
            agentAuditRepository.deleteForSession(sessionId)
            thermalPauseRepository.deleteForSession(sessionId)
            projectTypePauseRepository.deleteForSession(sessionId)
            repository.deleteSession(sessionId)
        }
    }
}
