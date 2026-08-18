package com.brain.offlineai.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val sessions = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Rule 3 "delete" - a real, permanent removal, gated behind the screen's own confirm dialog. */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            attachmentRepository.deleteForSession(sessionId)
            artifactRepository.deleteForSession(sessionId)
            repository.deleteSession(sessionId)
        }
    }
}
