package com.brain.offlineai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.brain.offlineai.data.artifacts.ArtifactDownloadTarget
import com.brain.offlineai.data.artifacts.ArtifactInfo
import com.brain.offlineai.data.artifacts.ArtifactKind
import com.brain.offlineai.ui.process.ProcessStep
import com.brain.offlineai.ui.screens.chat.ArtifactDownloadUiState
import com.brain.offlineai.ui.theme.*

private fun iconFor(kind: ArtifactKind): ImageVector = when (kind) {
    ArtifactKind.ZIP -> Icons.Filled.FolderZip
    ArtifactKind.CODE -> Icons.Filled.InsertDriveFile
    ArtifactKind.TEXT -> Icons.Filled.InsertDriveFile
}

/** Real human-readable size, same convention as [formatAttachmentSize]. */
fun formatArtifactSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1024} KB"
    else -> "${bytes / 1_048_576} MB"
}

/**
 * Phase 11 (Artifact card + ZIP/file output + download flow, spec section
 * 5) - real "Process Complete" checklist (only shown when [artifactSteps]
 * is genuinely non-empty - a plain-prose reply with no artifacts renders
 * nothing here) followed by one real card per [artifacts] entry and, for
 * more than one artifact, a real "Download All (ZIP)" action. Every
 * download state shown comes from [downloadStates], itself only ever
 * updated from real [com.brain.offlineai.data.artifacts.ArtifactExportProgress]
 * events in `ChatViewModel` - nothing here fakes a percentage or a
 * "Downloaded Successfully" banner that didn't really happen.
 */
@Composable
fun ArtifactCard(
    artifacts: List<ArtifactInfo>,
    artifactSteps: List<ProcessStep>,
    downloadStates: Map<String, ArtifactDownloadUiState>,
    zipDownloadId: String,
    onDownload: (ArtifactInfo, ArtifactDownloadTarget) -> Unit,
    onDownloadAll: (List<ArtifactInfo>) -> Unit
) {
    if (artifacts.isEmpty()) return

    Column(modifier = Modifier.widthIn(max = 280.dp)) {
        if (artifactSteps.isNotEmpty()) {
            LiveProcessCard(steps = artifactSteps)
            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(BrainBgCard)
                .padding(12.dp)
        ) {
            Text(
                "${artifacts.size} Artifact${if (artifacts.size == 1) "" else "s"}",
                color = BrainTextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(8.dp))

            artifacts.forEachIndexed { index, artifact ->
                ArtifactRow(
                    artifact = artifact,
                    state = downloadStates[artifact.id] ?: ArtifactDownloadUiState.Idle,
                    onDownload = { target -> onDownload(artifact, target) }
                )
                if (index != artifacts.lastIndex) Spacer(Modifier.height(8.dp))
            }

            if (artifacts.size > 1) {
                Spacer(Modifier.height(10.dp))
                ArtifactZipRow(
                    fileCount = artifacts.size,
                    state = downloadStates[zipDownloadId] ?: ArtifactDownloadUiState.Idle,
                    onDownloadAll = { onDownloadAll(artifacts) }
                )
            }
        }
    }
}

@Composable
private fun ArtifactRow(
    artifact: ArtifactInfo,
    state: ArtifactDownloadUiState,
    onDownload: (ArtifactDownloadTarget) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrainBgCardAlt)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(artifact.kind), contentDescription = null, tint = BrainCyanAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(artifact.fileName, color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(formatArtifactSize(artifact.sizeBytes), color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (state !is ArtifactDownloadUiState.Exporting) {
                IconButton(onClick = { showOptions = !showOptions }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Download, contentDescription = "Download ${artifact.fileName}", tint = BrainPurplePrimary, modifier = Modifier.size(18.dp))
                }
            }
        }

        when (state) {
            is ArtifactDownloadUiState.Idle -> {
                if (showOptions) {
                    Spacer(Modifier.height(6.dp))
                    DownloadOptionsRow(
                        onOption = { target -> showOptions = false; onDownload(target) },
                        onCancel = { showOptions = false }
                    )
                }
            }
            is ArtifactDownloadUiState.Exporting -> {
                Spacer(Modifier.height(6.dp))
                val pct = if (state.totalBytes > 0) (state.bytesCopied * 100 / state.totalBytes).toInt() else 0
                Text("Downloading... $pct%", color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (state.totalBytes > 0) state.bytesCopied.toFloat() / state.totalBytes else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = BrainPurplePrimary,
                    trackColor = BrainBorder
                )
            }
            is ArtifactDownloadUiState.Complete -> {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrainSuccessGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Downloaded Successfully", color = BrainSuccessGreen, style = MaterialTheme.typography.bodySmall)
                }
            }
            is ArtifactDownloadUiState.Failed -> {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = BrainDangerRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(state.reason, color = BrainDangerRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ArtifactZipRow(
    fileCount: Int,
    state: ArtifactDownloadUiState,
    onDownloadAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrainBgCardAlt)
            .padding(10.dp)
    ) {
        when (state) {
            is ArtifactDownloadUiState.Idle -> {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onDownloadAll() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FolderZip, contentDescription = null, tint = BrainPurplePrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download All ($fileCount files as ZIP)", color = BrainPurplePrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            is ArtifactDownloadUiState.Exporting -> {
                val pct = if (state.totalBytes > 0) (state.bytesCopied * 100 / state.totalBytes).toInt() else 0
                Text("Zipping & downloading... $pct%", color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (state.totalBytes > 0) state.bytesCopied.toFloat() / state.totalBytes else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = BrainPurplePrimary,
                    trackColor = BrainBorder
                )
            }
            is ArtifactDownloadUiState.Complete -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrainSuccessGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ZIP Downloaded Successfully", color = BrainSuccessGreen, style = MaterialTheme.typography.bodySmall)
                }
            }
            is ArtifactDownloadUiState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = BrainDangerRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(state.reason, color = BrainDangerRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** The spec's real Download Options row: Save to Device / Share / Open in File Manager / Cancel. */
@Composable
private fun DownloadOptionsRow(onOption: (ArtifactDownloadTarget) -> Unit, onCancel: () -> Unit) {
    Column {
        DownloadOptionButton(Icons.Filled.Download, "Save to Device") { onOption(ArtifactDownloadTarget.SAVE_TO_DEVICE) }
        DownloadOptionButton(Icons.Filled.Share, "Share") { onOption(ArtifactDownloadTarget.SHARE) }
        DownloadOptionButton(Icons.Filled.Folder, "Open in File Manager") { onOption(ArtifactDownloadTarget.OPEN_IN_FILE_MANAGER) }
        DownloadOptionButton(Icons.Filled.Close, "Cancel", tint = BrainTextMuted, onClick = onCancel)
    }
}

@Composable
private fun DownloadOptionButton(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = BrainPurplePrimary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}
