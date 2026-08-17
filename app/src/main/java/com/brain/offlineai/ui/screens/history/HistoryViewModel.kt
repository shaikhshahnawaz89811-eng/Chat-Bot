package com.brain.offlineai.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val sessions = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Rule 3 "delete" - a real, permanent removal, gated behind the screen's own confirm dialog. */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }
}
