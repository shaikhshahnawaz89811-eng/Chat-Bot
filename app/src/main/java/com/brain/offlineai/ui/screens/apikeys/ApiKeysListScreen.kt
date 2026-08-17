package com.brain.offlineai.ui.screens.apikeys

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.apikeys.ApiKeyEntity
import com.brain.offlineai.data.apikeys.statusAt
import com.brain.offlineai.ui.theme.*

/**
 * Screen 5 from the mockup ("API Keys (list) - All Keys with Status") -
 * real list backed by ApiKeysViewModel -> ApiKeyRepository -> the
 * SQLCipher-encrypted Room table. Status badges are computed live via
 * statusAt() (see ApiKeyEntity.kt) rather than read from a stored flag.
 */
@Composable
fun ApiKeysListScreen(
    viewModel: ApiKeysViewModel = viewModel(),
    onCreateClick: () -> Unit,
    onViewKey: (String) -> Unit,
    onOptionsKey: (String) -> Unit
) {
    val keys by viewModel.keys.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("API Keys", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create Key")
            }
        }
        Spacer(Modifier.height(20.dp))

        if (keys.isEmpty()) {
            EmptyKeysState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                keys.forEach { key ->
                    ApiKeyRow(key = key, onView = { onViewKey(key.id) }, onOptions = { onOptionsKey(key.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyKeysState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.VpnKey, contentDescription = null, tint = BrainTextMuted, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text("No API keys yet", color = BrainTextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Create a key so the Rani app can authenticate with Brain's local API server.",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ApiKeyRow(key: ApiKeyEntity, onView: () -> Unit, onOptions: () -> Unit) {
    val status = key.statusAt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .clickable(onClick = onView)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(key.name, color = BrainTextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            StatusBadge(status)
        }
        Spacer(Modifier.height(6.dp))
        Text("Created: ${formatDate(key.createdAt)}", color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            key.expiresAt?.let { "Expires: ${formatDate(it)}" } ?: "Expires: Never",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            key.lastUsedAt?.let { "Last used: ${formatRelative(it)}" } ?: "Last used: never",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onView) {
                Icon(Icons.Filled.Visibility, contentDescription = "View details", tint = BrainTextSecondary)
            }
            IconButton(onClick = onOptions) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Key options", tint = BrainTextSecondary)
            }
        }
    }
}
