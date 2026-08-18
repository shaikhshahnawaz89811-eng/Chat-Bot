package com.brain.offlineai.ui.screens.chat

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.artifacts.ArtifactDownloadTarget
import com.brain.offlineai.data.artifacts.ArtifactInfo
import com.brain.offlineai.data.attachments.UriMetadataResolver
import com.brain.offlineai.ui.components.*
import com.brain.offlineai.ui.theme.BrainBgPrimary
import kotlinx.coroutines.launch

/**
 * UI-only redesign of the existing ChatScreen.
 *
 * Existing ViewModel, engine, attachment picker, artifact download flow and
 * message-state routing are intentionally preserved.
 */
@Composable
fun ChatScreen(
    onMenuClick: () -> Unit,
    openSessionId: String? = null,
    viewModel: ChatViewModel = run {
        val context = LocalContext.current.applicationContext as android.app.Application
        viewModel(
            key = openSessionId ?: "current",
            factory = ChatViewModel.Factory(context, openSessionId)
        )
    }
) {
    val messages by viewModel.messages
    val input by viewModel.inputText
    val isBusy by viewModel.isBusy
    val pendingAttachments by viewModel.pendingAttachments
    val artifactDownloads by viewModel.artifactDownloads
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pendingLegacyDownload by remember { mutableStateOf<ArtifactInfo?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val artifact = pendingLegacyDownload
        pendingLegacyDownload = null
        if (granted && artifact != null) {
            viewModel.onDownloadArtifact(artifact, ArtifactDownloadTarget.SAVE_TO_DEVICE)
        }
    }

    val onDownloadArtifact: (ArtifactInfo, ArtifactDownloadTarget) -> Unit = { artifact, target ->
        if (
            target == ArtifactDownloadTarget.SAVE_TO_DEVICE &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            viewModel.needsLegacyStoragePermission()
        ) {
            pendingLegacyDownload = artifact
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.onDownloadArtifact(artifact, target)
        }
    }

    // Existing real Android file picker. No new/fake upload source is added.
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            val name = UriMetadataResolver.resolveDisplayName(context, uri)
            val mimeType = UriMetadataResolver.resolveMimeType(context, uri)
            viewModel.onAttachmentPicked(uri, name, mimeType)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
    ) {
        // Same existing top bar; only the title is changed to Chat Bot.
        ChatTopBar(
            title = "Chat Bot",
            onMenuClick = onMenuClick
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                messages,
                key = { it.id }
            ) { message ->
                when {
                    message.isUser -> UserBubble(message)

                    message.state == BotMessageState.PROCESS ->
                        BotProcessBubble(message)

                    message.state == BotMessageState.TASK_LIST ->
                        BotTaskListBubble(message)

                    message.state == BotMessageState.THINKING ->
                        BotThinkingBubble(message)

                    message.state == BotMessageState.CODING ->
                        BotCodingBubble(message)

                    message.state == BotMessageState.CODE_DONE ->
                        BotCodeDoneBubble(
                            message,
                            artifactDownloadStates = artifactDownloads,
                            onDownloadArtifact = onDownloadArtifact,
                            onDownloadAllArtifacts = {
                                artifacts ->
                                viewModel.onDownloadAllArtifacts(
                                    message.id,
                                    artifacts
                                )
                            }
                        )

                    message.state == BotMessageState.GENERATING ->
                        BotGeneratingBubble(message)

                    message.state == BotMessageState.SYSTEM_NOTE ->
                        BotSystemNoteBubble(message)

                    else ->
                        BotTextBubble(
                            message,
                            artifactDownloadStates = artifactDownloads,
                            onDownloadArtifact = onDownloadArtifact,
                            onDownloadAllArtifacts = {
                                artifacts ->
                                viewModel.onDownloadAllArtifacts(
                                    message.id,
                                    artifacts
                                )
                            }
                        )
                }
            }
        }

        ChatInputBar(
            value = input,
            onValueChange = viewModel::onInputChange,
            onSend = viewModel::sendMessage,
            isBusy = isBusy,
            pendingAttachments = pendingAttachments,
            onAttachClick = {
                attachmentPickerLauncher.launch(arrayOf("*/*"))
            },
            onRemoveAttachment = viewModel::onRemoveAttachment
        )
    }
}
