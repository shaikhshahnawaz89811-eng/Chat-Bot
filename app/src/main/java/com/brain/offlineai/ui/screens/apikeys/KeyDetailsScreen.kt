package com.brain.offlineai.ui.screens.apikeys

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.apikeys.ApiKeyEntity
import com.brain.offlineai.data.apikeys.statusAt
import com.brain.offlineai.ui.theme.*

/** Screen 7 from the mockup ("Key Details - Full Key Information"). */
@Composable
fun KeyDetailsScreen(
    keyId: String,
    viewModel: ApiKeysViewModel = viewModel(),
    onBack: () -> Unit,
    onCopy: (String) -> Unit
) {
    var key by remember { mutableStateOf<ApiKeyEntity?>(null) }
    var revealed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(keyId) { key = viewModel.getKey(keyId) }
    val loaded = key

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
            Text("Key Details", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        if (loaded == null) {
            Text("Key not found.", color = BrainTextMuted, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        DetailRow("Name", loaded.name)
        Spacer(Modifier.height(16.dp))
        DetailLabel("Status")
        Spacer(Modifier.height(4.dp))
        StatusBadge(loaded.statusAt())
        Spacer(Modifier.height(16.dp))
        DetailRow("Created", formatDateTime(loaded.createdAt))
        Spacer(Modifier.height(16.dp))
        DetailRow("Expires", loaded.expiresAt?.let { formatDateTime(it) } ?: "Never")
        Spacer(Modifier.height(16.dp))
        DetailRow("Last Used", loaded.lastUsedAt?.let { formatRelative(it) } ?: "Never")
        Spacer(Modifier.height(22.dp))

        DetailLabel("Key (tap to reveal)")
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BrainBgCard)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (revealed) loaded.keyValue else maskKey(loaded.keyValue),
                color = BrainTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) "Hide key" else "Reveal key",
                    tint = BrainTextSecondary
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                copyKeyToClipboard(context, loaded.keyValue)
                onCopy(loaded.id)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Copy Key")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("Done")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        DetailLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(value, color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailLabel(label: String) {
    Text(label, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
}
