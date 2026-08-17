package com.brain.offlineai.ui.screens.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.engine.ImportProgress
import com.brain.offlineai.ui.theme.*

/**
 * Real model-management screen. Phase 2 scope: import a real GGUF file
 * from device storage, load it into llama.cpp, unload it. Phase 5 adds the
 * real entry point (gear icon below) to the fuller "Model Settings" screen
 * from the mockup (screen 11 - context length, temperature, top-p,
 * thread-count sliders) - that screen now exists and is reachable, so this
 * route is no longer the only stop for model-related settings.
 */
@Composable
fun ModelsScreen(viewModel: ModelsViewModel = viewModel(), onOpenSettings: () -> Unit = {}) {
    val engineState by viewModel.engineState.collectAsState()
    val importProgress = viewModel.importProgress
    val installedModel = viewModel.installedModel
    val errorMessage = viewModel.errorMessage

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf"
            viewModel.importModel(uri, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Models", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Tune, contentDescription = "Model Settings", tint = BrainTextSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Import a GGUF model file from your device to run it fully offline.",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))

        EngineStateCard(engineState)
        Spacer(Modifier.height(16.dp))

        if (importProgress is ImportProgress.Copying) {
            ImportProgressCard(importProgress)
            Spacer(Modifier.height(16.dp))
        }

        errorMessage?.let {
            Text(it, color = BrainDangerRed, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }

        if (installedModel == null) {
            Button(
                onClick = { pickerLauncher.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary)
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import .gguf model")
            }
        } else {
            var showDeleteConfirm by remember { mutableStateOf(false) }
            InstalledModelCard(
                name = installedModel.name,
                sizeBytes = installedModel.sizeBytes,
                loaded = engineState is EngineState.Loaded,
                loading = engineState is EngineState.Loading,
                onLoad = viewModel::loadModel,
                onUnload = viewModel::unloadModel,
                onDelete = { showDeleteConfirm = true },
                onReplace = { pickerLauncher.launch(arrayOf("*/*")) }
            )
            // Rule 3 "delete" gap fix: deleting an imported model is a real,
            // irreversible on-disk removal (and unloads the engine if the
            // model is currently loaded) - same confirm-before-delete
            // pattern the API Keys screen already uses for its own Delete
            // Key action, applied here so this destructive action isn't one
            // accidental tap away.
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete \"${installedModel.name}\"?") },
                    text = { Text("This permanently removes the model file from device storage. This can't be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteModel()
                            showDeleteConfirm = false
                        }) { Text("Delete", color = BrainDangerRed) }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                )
            }
        }
    }
}

@Composable
private fun EngineStateCard(state: EngineState) {
    val (label, color) = when (state) {
        is EngineState.Unloaded -> "No model loaded" to BrainTextMuted
        is EngineState.Loading -> "Loading ${state.modelName}..." to BrainPurplePrimary
        is EngineState.Loaded -> "Loaded - ${state.modelName} (ctx ${state.contextSize})" to BrainSuccessGreen
        is EngineState.Error -> "Error" to BrainDangerRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state is EngineState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BrainPurplePrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportProgressCard(progress: ImportProgress.Copying) {
    val fraction = if (progress.totalBytes > 0) (progress.bytesCopied.toFloat() / progress.totalBytes).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(14.dp)
    ) {
        Text("Importing model...", color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = BrainPurplePrimary,
            trackColor = BrainBorder
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${progress.bytesCopied / 1_048_576} MB" +
                if (progress.totalBytes > 0) " / ${progress.totalBytes / 1_048_576} MB" else "",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun InstalledModelCard(
    name: String,
    sizeBytes: Long,
    loaded: Boolean,
    loading: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onReplace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Layers, contentDescription = null, tint = BrainPurplePrimary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
                Text("${sizeBytes / 1_048_576} MB on disk", color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loaded) {
                OutlinedButton(onClick = onUnload) { Text("Unload") }
            } else {
                Button(
                    onClick = onLoad,
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary)
                ) {
                    Text(if (loading) "Loading..." else "Load model")
                }
            }
            OutlinedButton(onClick = onReplace, enabled = !loading) { Text("Replace") }
            IconButton(onClick = onDelete, enabled = !loading) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete model", tint = BrainDangerRed)
            }
        }
    }
}
