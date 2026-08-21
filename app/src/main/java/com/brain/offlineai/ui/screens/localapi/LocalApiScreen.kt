package com.brain.offlineai.ui.screens.localapi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.server.LocalApiServerManager
import com.brain.offlineai.server.NetworkUtils
import com.brain.offlineai.server.PublicTunnelManager
import com.brain.offlineai.server.ServerState
import com.brain.offlineai.server.TunnelState
import com.brain.offlineai.ui.screens.apikeys.copyKeyToClipboard
import com.brain.offlineai.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Screen 10 from the mockup ("Connection Status - Local API Server
 * Status"). Every field is real: Status/Uptime/Requests Served come from
 * [LocalApiServerManager] via [LocalApiViewModel], and Start/Stop actually
 * starts/stops [com.brain.offlineai.server.LocalApiForegroundService] -
 * nothing here is a static mockup of the screen.
 */
@Composable
fun LocalApiScreen(
    viewModel: LocalApiViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.serverState.collectAsState()
    val requestsServed by viewModel.requestsServed.collectAsState()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Granted or not, the server still starts - see buildNotification() comment. */
        viewModel.startServer()
    }

    fun onStartClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startServer()
        }
    }

    // Live-ticking uptime, matches mockup's "Uptime 02:15:47" field -
    // recomputed every second from the real startedAtMillis, not animated.
    var uptimeText by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(state) {
        val running = state as? ServerState.Running
        if (running == null) {
            uptimeText = "00:00:00"
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = (System.currentTimeMillis() - running.startedAtMillis) / 1000
            uptimeText = formatUptime(elapsed)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
            }
            Text("Local API", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BrainBgCard)
        ) {
            StatusRow(state)
            RowDivider()
            EndpointRow(
                endpoint = "http://127.0.0.1:${LocalApiServerManager.PORT}/${LocalApiServerManager.API_VERSION}",
                onCopy = { copyKeyToClipboard(context, "http://127.0.0.1:${LocalApiServerManager.PORT}/${LocalApiServerManager.API_VERSION}") }
            )
            RowDivider()
            // Real LAN address of this phone (NetworkUtils.getLocalIpv4Address(),
            // never a placeholder) - this is what a paired companion app on
            // another phone (e.g. Rani) on the same Wi-Fi/hotspot should use,
            // since the server now binds to 0.0.0.0 as well as loopback.
            val lanIp = remember(state) { NetworkUtils.getLocalIpv4Address() }
            val lanEndpoint = lanIp?.let { "http://$it:${LocalApiServerManager.PORT}/${LocalApiServerManager.API_VERSION}" }
            EndpointRow(
                label = "LAN Endpoint (for other devices)",
                endpoint = lanEndpoint ?: "Not connected to Wi-Fi/hotspot",
                onCopy = { lanEndpoint?.let { copyKeyToClipboard(context, it) } }
            )
            RowDivider()
            // Public Tunnel - real state from PublicTunnelManager, which
            // starts/stops in lockstep with this server (see
            // LocalApiServerManager) and runs cloudflared as Brain's own
            // child process. Only ever shows a URL actually parsed from
            // cloudflared's real stdout - never a placeholder while
            // Starting/Error.
            val tunnelState by PublicTunnelManager.state.collectAsState()
            val tunnelText = when (val t = tunnelState) {
                is TunnelState.Off -> "Off"
                is TunnelState.Starting -> "Starting…"
                is TunnelState.Running -> t.url
                is TunnelState.Error -> t.message
            }
            EndpointRow(
                label = "Public Tunnel (works away from Wi-Fi)",
                endpoint = tunnelText,
                onCopy = { (tunnelState as? TunnelState.Running)?.url?.let { copyKeyToClipboard(context, it) } }
            )
            RowDivider()
            InfoRow("API Version", LocalApiServerManager.API_VERSION)
            RowDivider()
            InfoRow("Uptime", uptimeText)
            RowDivider()
            InfoRow("Requests Served", requestsServed.toString())
        }

        if (state is ServerState.Error) {
            Spacer(Modifier.height(12.dp))
            Text(
                (state as ServerState.Error).message,
                color = BrainDangerRed,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(20.dp))

        if (state is ServerState.Running) {
            Button(
                onClick = { viewModel.stopServer() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrainDangerRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Stop Server")
            }
        } else {
            Button(
                onClick = { onStartClick() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Server")
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Requires an active API key (see API Keys) - the companion Rani " +
                "app authenticates with Authorization: Bearer <key>.",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatusRow(state: ServerState) {
    val (label, color) = when (state) {
        is ServerState.Stopped -> "Stopped" to BrainTextMuted
        is ServerState.Running -> "Running" to BrainSuccessGreen
        is ServerState.Error -> "Error" to BrainDangerRed
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Status", color = BrainTextSecondary, style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(label, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EndpointRow(endpoint: String, onCopy: () -> Unit, label: String = "Endpoint") {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(label, color = BrainTextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(endpoint, color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy endpoint", tint = BrainTextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrainTextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = BrainBorder, thickness = 1.dp)
}

private fun formatUptime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
