package com.brain.offlineai.ui.screens.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.ui.theme.*

/**
 * Screen 13 from the mockup ("Storage"). Real byte counts from
 * [StorageViewModel] - no chart library, no seeded numbers (same honest
 * scope note Phase 5's Analytics screen already applied to itself).
 */
@Composable
fun StorageScreen(
    viewModel: StorageViewModel = viewModel(),
    onBack: () -> Unit
) {
    val breakdown = viewModel.breakdown
    val appTotal = breakdown.modelsBytes + breakdown.apiKeysDbBytes + breakdown.settingsAndAnalyticsBytes + breakdown.cacheBytes
    val deviceUsedFraction = if (breakdown.deviceTotalBytes > 0) {
        (1f - breakdown.deviceFreeBytes.toFloat() / breakdown.deviceTotalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
            }
            Text("Storage", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        SectionCard(title = "Brain's Storage") {
            StorageRow("Imported Models", breakdown.modelsBytes)
            StorageRow("API Keys Database (encrypted)", breakdown.apiKeysDbBytes)
            StorageRow("Settings & Analytics", breakdown.settingsAndAnalyticsBytes)
            StorageRow("Cache", breakdown.cacheBytes)
            HorizontalDivider(color = BrainBorder, modifier = Modifier.padding(vertical = 8.dp))
            StorageRow("Total Used by Brain", appTotal, emphasize = true)
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "Device Storage") {
            Text(
                "%s free of %s".format(formatBytes(breakdown.deviceFreeBytes), formatBytes(breakdown.deviceTotalBytes)),
                color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { deviceUsedFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = BrainPurplePrimary,
                trackColor = BrainBorder
            )
            Text(
                "Real free/total space on this device's internal storage volume.",
                color = BrainTextMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { viewModel.clearCache() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BrainBgCardAlt, contentColor = BrainTextPrimary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Clear Cache (${formatBytes(breakdown.cacheBytes)})")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Cache is data Android can already reclaim on its own at any time - clearing it here is safe and doesn't touch your imported models, API keys, or settings.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "To remove an imported model, use the Delete option on the Models screen.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
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
private fun StorageRow(label: String, bytes: Long, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (emphasize) BrainTextPrimary else BrainTextMuted,
            style = if (emphasize) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
        )
        Text(
            formatBytes(bytes),
            color = if (emphasize) BrainPurplePrimary else BrainTextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
