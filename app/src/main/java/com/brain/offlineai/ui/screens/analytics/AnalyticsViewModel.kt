package com.brain.offlineai.ui.screens.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.analytics.AnalyticsStore
import com.brain.offlineai.engine.BrainEngine
import com.brain.offlineai.engine.DeviceMemoryMonitor
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.server.LocalApiServerManager
import com.brain.offlineai.server.ServerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Real analytics data source (mockup's Analytics destination, Phase 5).
 * Every field here is either a persisted real counter from [AnalyticsStore]
 * or a live real StateFlow this codebase already maintains elsewhere
 * (BrainEngine, LocalApiServerManager, DeviceMemoryMonitor) - PROGRESS.md's
 * own Phase 5 scope note says "real analytics (not sample charts)", so
 * there is deliberately no chart library / seeded dataset here, just the
 * real numbers this app has actually produced.
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val analyticsStore = AnalyticsStore(application)
    private val memoryMonitor = DeviceMemoryMonitor(application)

    val totalMessagesSent: Int get() = analyticsStore.totalMessagesSent
    val totalTokensGenerated: Long get() = analyticsStore.totalTokensGenerated
    val totalLocalApiRequests: Long get() = analyticsStore.totalLocalApiRequests
    val firstLaunchAtMillis: Long get() = analyticsStore.firstLaunchAtMillis

    val engineState: StateFlow<EngineState> = BrainEngine.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineState.Unloaded)

    val serverState: StateFlow<ServerState> = LocalApiServerManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerState.Stopped)

    val liveRequestsThisSession: StateFlow<Int> = LocalApiServerManager.requestsServed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val memorySnapshots = memoryMonitor.snapshots()
}
