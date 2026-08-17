package com.brain.offlineai.ui.screens.apikeys

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.apikeys.ApiKeyEntity
import com.brain.offlineai.ui.theme.*

/** Screen 8 from the mockup ("Key Options - Revoke / Delete / Copy"). */
@Composable
fun KeyOptionsScreen(
    keyId: String,
    viewModel: ApiKeysViewModel = viewModel(),
    onBack: () -> Unit,
    onViewDetails: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDeleted: () -> Unit
) {
    var key by remember { mutableStateOf<ApiKeyEntity?>(null) }
    var showRevokeConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
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
            Text("Key Options", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        if (loaded == null) {
            Text("Key not found.", color = BrainTextMuted, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        OptionRow(Icons.Filled.Visibility, "View Details", BrainTextPrimary) { onViewDetails(loaded.id) }
        Spacer(Modifier.height(10.dp))
        OptionRow(Icons.Filled.ContentCopy, "Copy Key", BrainTextPrimary) {
            copyKeyToClipboard(context, loaded.keyValue)
            onCopy(loaded.id)
        }
        Spacer(Modifier.height(10.dp))
        OptionRow(Icons.Filled.Block, "Revoke Key", BrainWarningAmber) { showRevokeConfirm = true }
        Spacer(Modifier.height(10.dp))
        OptionRow(Icons.Filled.Delete, "Delete Key", BrainDangerRed) { showDeleteConfirm = true }
    }

    if (showRevokeConfirm && loaded != null) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = { Text("Revoke \"${loaded.name}\"?") },
            text = { Text("Revoked keys can no longer authenticate with Brain's local API server. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeKey(loaded.id)
                    showRevokeConfirm = false
                    onBack()
                }) { Text("Revoke", color = BrainWarningAmber) }
            },
            dismissButton = { TextButton(onClick = { showRevokeConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm && loaded != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${loaded.name}\"?") },
            text = { Text("This permanently removes the key from Brain. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteKey(loaded.id) { onDeleted() }
                    showDeleteConfirm = false
                }) { Text("Delete", color = BrainDangerRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
