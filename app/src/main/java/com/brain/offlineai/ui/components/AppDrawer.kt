package com.brain.offlineai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brain.offlineai.engine.BrainEngine
import com.brain.offlineai.engine.DeviceMemoryMonitor
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.engine.MemorySnapshot
import com.brain.offlineai.navigation.Screen
import com.brain.offlineai.ui.theme.*

private fun iconFor(screen: Screen): ImageVector = when (screen) {
    Screen.Chat -> Icons.Filled.Chat
    Screen.ApiKeys -> Icons.Filled.VpnKey
    Screen.Models -> Icons.Filled.Layers
    Screen.LocalApi -> Icons.Filled.Dns
    Screen.Analytics -> Icons.Filled.BarChart
    Screen.Settings -> Icons.Filled.Settings
    Screen.About -> Icons.Filled.Info
    else -> Icons.Filled.Circle
}

@Composable
fun AppDrawerContent(
    currentRoute: String,
    onNavigate: (Screen) -> Unit
) {
    ModalDrawerSheet(drawerContainerColor = BrainBgPrimary) {
        Column(modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp)) {
            DrawerHeader()
            Spacer(Modifier.height(16.dp))
            Screen.drawerItems.forEach { screen ->
                DrawerNavRow(
                    screen = screen,
                    selected = currentRoute == screen.route,
                    onClick = { onNavigate(screen) }
                )
            }
            Spacer(Modifier.weight(1f))
            AiEngineStatusCard()
        }
    }
}

@Composable
private fun DrawerHeader() {
    val engineState by BrainEngine.state.collectAsState()
    val runsLine = when (val state = engineState) {
        is EngineState.Loaded -> "Runs ${state.modelName} (GGUF, user-imported)"
        is EngineState.Loading -> "Loading ${state.modelName}..."
        is EngineState.Error -> "No model loaded - import + load a .gguf in Models"
        is EngineState.Unloaded -> "No model loaded - import + load a .gguf in Models"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BrainPurplePrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Psychology, contentDescription = null, tint = BrainPurplePrimary)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("BRAIN", color = BrainTextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Offline AI Engine", color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(12.dp))
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("100% Offline • No Internet", color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(runsLine, color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(BrainSuccessGreen))
            Spacer(Modifier.width(6.dp))
            Text("Secure Local API", color = BrainSuccessGreen, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DrawerNavRow(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) BrainPurplePrimary else Color.Transparent
    val fg = if (selected) Color.White else BrainTextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(iconFor(screen), contentDescription = screen.label, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(screen.label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Wired to real state as of Phase 2 - the Phase 1 version of this card had
 * hardcoded "Online & Ready" / "1.24 GB / 4.00 GB" placeholder text; both
 * numbers now come from BrainEngine's real llama.cpp state and
 * ActivityManager's real memory info, not from a mock.
 */
@Composable
private fun AiEngineStatusCard() {
    val context = LocalContext.current
    val engineState by BrainEngine.state.collectAsState()
    val memoryMonitor = remember { DeviceMemoryMonitor(context) }
    val memory by memoryMonitor.snapshots().collectAsState(initial = MemorySnapshot(0L, 0L))

    val (statusLabel, statusColor) = when (engineState) {
        is EngineState.Loaded -> "Online & Ready" to BrainSuccessGreen
        is EngineState.Loading -> "Loading model..." to BrainWarningAmber
        is EngineState.Error -> "Engine error" to BrainDangerRed
        is EngineState.Unloaded -> "No model loaded" to BrainTextMuted
    }
    val modelName = (engineState as? EngineState.Loaded)?.modelName ?: "No model imported yet"

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BrainBgCard)
            .padding(14.dp)
    ) {
        Text("AI Engine Status", color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(statusLabel, color = statusColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(modelName, color = BrainTextPrimary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "RAM Usage  %.2f GB / %.2f GB".format(memory.usedGb, memory.totalGb),
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        LinearProgressIndicator(
            progress = { memory.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = BrainPurplePrimary,
            trackColor = BrainBorder
        )
    }
}
