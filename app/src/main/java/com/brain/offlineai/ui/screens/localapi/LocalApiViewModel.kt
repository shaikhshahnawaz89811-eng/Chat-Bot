package com.brain.offlineai.ui.screens.localapi

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.server.LocalApiForegroundService
import com.brain.offlineai.server.LocalApiServerManager
import com.brain.offlineai.server.ServerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Screen 10 from the mockup ("Connection Status - Local API Server
 * Status"). Same shape as ModelsViewModel/ApiKeysViewModel: a thin
 * StateFlow bridge to the real owner of the state
 * ([LocalApiServerManager]), plus start/stop actions that go through the
 * real Android Service lifecycle (not just flipping a boolean).
 */
class LocalApiViewModel(application: Application) : AndroidViewModel(application) {

    val serverState: StateFlow<ServerState> = LocalApiServerManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerState.Stopped)

    val requestsServed: StateFlow<Int> = LocalApiServerManager.requestsServed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startServer() {
        val context = getApplication<Application>()
        ContextCompat.startForegroundService(context, LocalApiForegroundService.startIntent(context))
    }

    fun stopServer() {
        val context = getApplication<Application>()
        context.startService(LocalApiForegroundService.stopIntent(context))
    }
}
