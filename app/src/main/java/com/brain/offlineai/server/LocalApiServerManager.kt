package com.brain.offlineai.server

import android.content.Context
import com.brain.offlineai.data.analytics.AnalyticsStore
import com.brain.offlineai.data.apikeys.ApiKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Real state of the local server - mirrors [com.brain.offlineai.engine.BrainEngine]'s pattern. */
sealed class ServerState {
    data object Stopped : ServerState()
    data class Running(val port: Int, val startedAtMillis: Long) : ServerState()
    data class Error(val message: String) : ServerState()
}

/**
 * Single process-wide owner of the [LocalApiServer] instance, same
 * one-owner-object shape as [com.brain.offlineai.engine.BrainEngine] for
 * the native engine. [LocalApiForegroundService] is a thin Android Service
 * wrapper around this - this object holds the actual server + the real
 * request counter so the Connection Status screen (mockup screen 10) can
 * observe live state regardless of whether the screen or the service
 * started first.
 */
object LocalApiServerManager {

    /** Matches the endpoint shown in the mockup: http://127.0.0.1:11434/v1 */
    const val PORT = 11434
    const val API_VERSION = "v1"

    private var server: LocalApiServer? = null

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state

    private val _requestsServed = MutableStateFlow(0)
    val requestsServed: StateFlow<Int> = _requestsServed

    val isRunning: Boolean
        get() = _state.value is ServerState.Running

    @Synchronized
    fun start(context: Context) {
        if (isRunning) return
        _requestsServed.value = 0
        val repository = ApiKeyRepository(context.applicationContext)
        // Phase 5: also feed the real, cumulative-across-restarts Analytics
        // counter (AnalyticsStore) alongside the existing live per-session
        // _requestsServed StateFlow above - that flow's own reset-to-0-on-
        // start/stop behavior (Connection Status screen, Phase 4) is
        // unchanged; this is an additional, separate write.
        val analytics = AnalyticsStore(context.applicationContext)
        val newServer = LocalApiServer(PORT, repository) {
            _requestsServed.value = _requestsServed.value + 1
            analytics.incrementLocalApiRequests()
        }
        try {
            newServer.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
            _state.value = ServerState.Running(PORT, System.currentTimeMillis())
            // Public Tunnel is deliberately NOT started here. The local API
            // can be useful on LAN without exposing it publicly. The user
            // explicitly starts/stops the tunnel from the Local API screen.
        } catch (e: Exception) {
            // Real failure surfaced (e.g. port already bound by something
            // else) - not swallowed into a fake "Running" state.
            _state.value = ServerState.Error(e.message ?: "Failed to start the local server on port $PORT.")
        }
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        _state.value = ServerState.Stopped
        _requestsServed.value = 0
        PublicTunnelManager.stop()
    }
}
