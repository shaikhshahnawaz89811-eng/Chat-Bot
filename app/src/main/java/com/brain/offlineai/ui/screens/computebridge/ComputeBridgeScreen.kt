package com.brain.offlineai.ui.screens.computebridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.computebridge.ComputeMode
import com.brain.offlineai.computebridge.DiscoveredWorker
import com.brain.offlineai.computebridge.PairedWorker
import com.brain.offlineai.ui.theme.*

/**
 * "Worker Settings" screen from the architecture doc: Local/Remote/Auto
 * mode, the real list of paired workers (never just one), and pairing a
 * new worker from its QR payload. Every value shown here comes from
 * [ComputeBridgeViewModel] - nothing is a static mockup.
 */
@Composable
fun ComputeBridgeScreen(
    viewModel: ComputeBridgeViewModel = viewModel(),
    onBack: () -> Unit,
    onScanQr: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(BrainBgPrimary)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
            }
            Text("Compute Bridge", color = BrainTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "This app never combines RAM or CPU across devices. Remote/Auto mode " +
                        "sends a prompt to a paired worker phone, which runs the model on its " +
                        "own device and streams the answer back over the local network.",
                    color = BrainTextSecondary, fontSize = 13.sp
                )
            }

            item { ModeSelector(state.mode, onSelect = viewModel::setMode) }

            item {
                Text(
                    "Paired workers (${state.pairedWorkers.size})",
                    color = BrainTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }

            if (state.pairedWorkers.isEmpty()) {
                item {
                    Text(
                        "No worker paired yet. Tap \"Scan QR with camera\" below and point it " +
                            "at the Worker app's QR code, or paste the code manually.",
                        color = BrainTextMuted, fontSize = 13.sp
                    )
                }
            }

            items(state.pairedWorkers, key = { it.workerId }) { worker ->
                PairedWorkerRow(
                    worker = worker,
                    onToggle = { enabled -> viewModel.setWorkerEnabled(worker.workerId, enabled) },
                    onRemove = { viewModel.removeWorker(worker.workerId) }
                )
            }

            item {
                Text(
                    "Pair a new worker",
                    color = BrainTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }

            item {
                OutlinedButton(
                    onClick = onScanQr,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan QR with camera")
                }
            }

            item {
                Text(
                    "or paste it manually",
                    color = BrainTextMuted, fontSize = 12.sp
                )
            }

            item {
                OutlinedTextField(
                    value = state.pairingCode,
                    onValueChange = viewModel::onPairingCodeChange,
                    label = { Text("Paste pairing code (QR content)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                Button(
                    onClick = viewModel::pairFromCode,
                    enabled = state.pairingCode.isNotBlank() && !state.isPairing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isPairing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Pair")
                    }
                }
            }

            state.statusMessage?.let { msg ->
                item { Text(msg, color = BrainTextSecondary, fontSize = 13.sp) }
            }

            item {
                Text(
                    "Nearby on this network (${state.discoveredWorkers.size})",
                    color = BrainTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }

            if (state.discoveredWorkers.isEmpty()) {
                item {
                    Text(
                        "Nothing found yet. Make sure both phones are on the same Wi-Fi or one " +
                            "phone's hotspot, and the Worker app is started.",
                        color = BrainTextMuted, fontSize = 13.sp
                    )
                }
            }

            items(state.discoveredWorkers, key = { it.workerId }) { discovered ->
                DiscoveredWorkerRow(discovered)
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ModeSelector(mode: ComputeMode, onSelect: (ComputeMode) -> Unit) {
    Column {
        Text("Mode", color = BrainTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComputeMode.entries.forEach { candidate ->
                val selected = candidate == mode
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(candidate) },
                    label = { Text(candidate.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        Text(
            when (mode) {
                ComputeMode.LOCAL -> "Always runs on this phone's own model."
                ComputeMode.REMOTE -> "Always tries paired workers first, falls back to this phone if none answer."
                ComputeMode.AUTO -> "Uses a paired worker when this phone's RAM is under pressure or no model is loaded locally; otherwise runs locally."
            },
            color = BrainTextMuted, fontSize = 12.sp
        )
    }
}

@Composable
private fun PairedWorkerRow(worker: PairedWorker, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(worker.label, color = BrainTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${worker.host}:${worker.port}", color = BrainTextMuted, fontSize = 12.sp)
        }
        Switch(checked = worker.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove worker", tint = BrainTextMuted)
        }
    }
}

@Composable
private fun DiscoveredWorkerRow(discovered: DiscoveredWorker) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCardAlt)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(discovered.workerId, color = BrainTextPrimary, fontSize = 14.sp)
            Text("${discovered.host}:${discovered.port}", color = BrainTextMuted, fontSize = 12.sp)
        }
        Text("Scan its QR to pair", color = BrainTextMuted, fontSize = 12.sp)
    }
}
