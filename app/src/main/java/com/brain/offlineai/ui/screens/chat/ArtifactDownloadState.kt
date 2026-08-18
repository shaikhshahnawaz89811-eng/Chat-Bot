package com.brain.offlineai.ui.screens.chat

import android.net.Uri
import com.brain.offlineai.data.artifacts.ArtifactDownloadTarget

/**
 * Phase 11 - the real, current download state for one artifact card (or
 * the "download all" ZIP), keyed by artifact id in
 * [ChatViewModel.artifactDownloads]. Mirrors the spec's own Download
 * Progress -> Download Complete sequence, driven only by real
 * [com.brain.offlineai.data.artifacts.ArtifactExportProgress] events - no
 * step here is a fixed-duration fake.
 */
sealed class ArtifactDownloadUiState {
    object Idle : ArtifactDownloadUiState()
    data class Exporting(val bytesCopied: Long, val totalBytes: Long, val target: ArtifactDownloadTarget) : ArtifactDownloadUiState()
    data class Complete(val uri: Uri, val target: ArtifactDownloadTarget) : ArtifactDownloadUiState()
    data class Failed(val reason: String) : ArtifactDownloadUiState()
}
