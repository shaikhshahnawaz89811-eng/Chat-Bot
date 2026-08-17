package com.brain.offlineai.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.engine.MemorySnapshot
import com.brain.offlineai.server.ServerState
import com.brain.offlineai.ui.theme.*
import java.util.concurrent.TimeUnit

/**
 * Analytics destination (Phase 5 - real usage numbers, not sample charts;
 * see PROGRESS.md Phase 5 scope note). Replaces the Phase-1-4 Placeholder
 * route (Rule 1 - route already existed and was reachable, now has real
 * content instead of "arrives in Phase 5").
 */
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = viewModel()) {
    val engineState by viewModel.engineState.collectAsState()
    val serverState by viewModel.serverState.collectAsState()
    val liveRequests by viewModel.liveRequestsThisSession.collectAsState()
    val memory by viewModel.memorySnapshots.collectAsState(initial = MemorySnapshot(0L, 0L))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Analytics", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Genuine usage on this device since ${daysSinceLabel(viewModel.firstLaunchAtMillis)}.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(modifier = Modifier.weight(1f), label = "Messages Sent", value = viewModel.totalMessagesSent.toString())
            StatCard(modifier = Modifier.weight(1f), label = "Tokens Generated", value = formatCount(viewModel.totalTokensGenerated))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(modifier = Modifier.weight(1f), label = "Local API Requests", value = formatCount(viewModel.totalLocalApiRequests))
            StatCard(modifier = Modifier.weight(1f), label = "This Session", value = liveRequests.toString())
        }
        Spacer(Modifier.height(20.dp))

        SectionCard(title = "Engine Status") {
            val (label, color) = when (engineState) {
                is EngineState.Loaded -> "Model loaded (${(engineState as EngineState.Loaded).modelName})" to BrainSuccessGreen
                is EngineState.Loading -> "Loading model..." to BrainWarningAmber
                is EngineState.Error -> "Error" to BrainDangerRed
                is EngineState.Unloaded -> "No model loaded" to BrainTextMuted
            }
            InfoLine("Status", label, color)
            InfoLine(
                "RAM Usage",
                "%.2f GB / %.2f GB".format(memory.usedGb, memory.totalGb),
                BrainTextPrimary
            )
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "Local API Server") {
            val (label, color) = when (serverState) {
                is ServerState.Running -> "Running" to BrainSuccessGreen
                is ServerState.Error -> "Error" to BrainDangerRed
                is ServerState.Stopped -> "Stopped" to BrainTextMuted
            }
            InfoLine("Status", label, color)
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(14.dp)
    ) {
        Text(value, color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(14.dp)
    ) {
        Text(title, color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun daysSinceLabel(firstLaunchAtMillis: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstLaunchAtMillis)
    return if (days <= 0) "today" else "$days day${if (days != 1L) "s" else ""} ago"
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}
