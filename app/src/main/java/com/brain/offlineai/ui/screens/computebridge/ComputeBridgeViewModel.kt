package com.brain.offlineai.ui.screens.computebridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.computebridge.ComputeMode
import com.brain.offlineai.computebridge.DiscoveredWorker
import com.brain.offlineai.computebridge.PairedWorker
import com.brain.offlineai.computebridge.PairedWorkerStore
import com.brain.offlineai.computebridge.WorkerApiClient
import com.brain.offlineai.computebridge.WorkerDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class ComputeBridgeUiState(
    val mode: ComputeMode = ComputeMode.LOCAL,
    val pairedWorkers: List<PairedWorker> = emptyList(),
    val discoveredWorkers: List<DiscoveredWorker> = emptyList(),
    val pairingCode: String = "",
    val isPairing: Boolean = false,
    val statusMessage: String? = null
)

/**
 * Backs the Compute Bridge screen: mode selector (Local/Remote/Auto), the
 * real list of paired workers (add/remove/enable - never capped to one),
 * a live "nearby on this network" list from NSD, and pairing via the
 * pairing-code JSON payload the Worker app's QR encodes (see
 * docs/protocol.md - scan that QR with any camera/QR app already on the
 * phone, then paste the text here).
 */
class ComputeBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = PairedWorkerStore(application)
    private val discovery = WorkerDiscovery(application)

    private val _uiState = MutableStateFlow(
        ComputeBridgeUiState(mode = store.mode, pairedWorkers = store.list())
    )
    val uiState: StateFlow<ComputeBridgeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            discovery.discover().collect { found ->
                _uiState.value = _uiState.value.copy(discoveredWorkers = found)
            }
        }
    }

    fun setMode(mode: ComputeMode) {
        store.mode = mode
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun onPairingCodeChange(text: String) {
        _uiState.value = _uiState.value.copy(pairingCode = text)
    }

    /** Parses the pasted QR JSON payload (protocol.md's "sa-compute-v1"
     * format), does a real POST /v1/pair against the worker it names, and
     * only stores the worker once that call genuinely succeeds. */
    fun pairFromCode() {
        val raw = _uiState.value.pairingCode.trim()
        if (raw.isEmpty()) return
        _uiState.value = _uiState.value.copy(isPairing = true, statusMessage = null)
        viewModelScope.launch {
            val result = runCatching {
                val json = JSONObject(raw)
                require(json.optString("protocol") == "sa-compute-v1") { "Not a Compute Bridge pairing code" }
                val workerId = json.getString("worker_id")
                val host = json.getString("host")
                val port = json.getInt("port")
                val pairingToken = json.getString("pairing_token")
                val accessToken = WorkerApiClient(PairedWorker(workerId, host, port, pairingToken))
                    .pair(host, port, pairingToken)
                    ?: throw IllegalStateException("Worker rejected the pairing code")
                PairedWorker(workerId = workerId, host = host, port = port, token = accessToken)
            }
            result.onSuccess { worker ->
                store.upsert(worker)
                _uiState.value = _uiState.value.copy(
                    isPairing = false,
                    pairingCode = "",
                    pairedWorkers = store.list(),
                    statusMessage = "Paired with ${worker.workerId}"
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isPairing = false,
                    statusMessage = "Pairing failed: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun removeWorker(workerId: String) {
        store.remove(workerId)
        _uiState.value = _uiState.value.copy(pairedWorkers = store.list())
    }

    fun setWorkerEnabled(workerId: String, enabled: Boolean) {
        store.setEnabled(workerId, enabled)
        _uiState.value = _uiState.value.copy(pairedWorkers = store.list())
    }

    /** Real reachability check against one already-paired worker, used by
     * the screen's per-row "Check" action - never assumes a worker is
     * still online just because it's in the list. */
    fun checkWorker(worker: PairedWorker, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(WorkerApiClient(worker).health())
        }
    }
}
