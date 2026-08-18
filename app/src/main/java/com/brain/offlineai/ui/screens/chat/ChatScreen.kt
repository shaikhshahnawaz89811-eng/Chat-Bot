package com.brain.offlineai.ui.screens.chat

import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.artifacts.ArtifactDownloadTarget
import com.brain.offlineai.data.artifacts.ArtifactInfo
import com.brain.offlineai.data.attachments.UriMetadataResolver
import com.brain.offlineai.ui.components.*
import com.brain.offlineai.ui.theme.BrainBgPrimary
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onMenuClick: () -> Unit,
    openSessionId: String? = null,
    viewModel: ChatViewModel = run {
        val context = LocalContext.current
        val restoredSessionId = openSessionId ?: CurrentChatSessionStore.get(context)
        val application = context.applicationContext as android.app.Application
        viewModel(
            key = restoredSessionId ?: "current",
            factory = ChatViewModel.Factory(application, restoredSessionId)
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
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

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

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        showAttachmentMenu = false
        uris.forEach { uri ->
            val name = UriMetadataResolver.resolveDisplayName(context, uri)
            val mimeType = UriMetadataResolver.resolveMimeType(context, uri)
            viewModel.onAttachmentPicked(uri, name, mimeType)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        showAttachmentMenu = false
        if (uri != null) {
            val name = UriMetadataResolver.resolveDisplayName(context, uri)
            val mimeType = UriMetadataResolver.resolveMimeType(context, uri)
            viewModel.onAttachmentPicked(uri, name, mimeType)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        showAttachmentMenu = false
        val uri = cameraUri
        if (success && uri != null) {
            val name = UriMetadataResolver.resolveDisplayName(context, uri).ifBlank { "photo.jpg" }
            val mimeType = UriMetadataResolver.resolveMimeType(context, uri) ?: "image/jpeg"
            viewModel.onAttachmentPicked(uri, name, mimeType)
        } else if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        cameraUri = null
    }

    fun launchCamera() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ChatBot_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )
        if (uri != null) {
            cameraUri = uri
            cameraLauncher.launch(uri)
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
                            onDownloadAllArtifacts = { artifacts ->
                                viewModel.onDownloadAllArtifacts(message.id, artifacts)
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
                            onDownloadAllArtifacts = { artifacts ->
                                viewModel.onDownloadAllArtifacts(message.id, artifacts)
                            }
                        )
                }
            }
        }

        if (showAttachmentMenu) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentMenu = false },
                containerColor = com.brain.offlineai.ui.theme.BrainBgCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Add to Chat Bot",
                        color = com.brain.offlineai.ui.theme.BrainTextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            attachmentPickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📄  File")
                    }

                    TextButton(
                        onClick = {
                            photoPickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🖼  Photo")
                    }

                    TextButton(
                        onClick = { launchCamera() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📷  Camera")
                    }

                    Spacer(Modifier.height(16.dp))
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
                showAttachmentMenu = true
            },
            onRemoveAttachment = viewModel::onRemoveAttachment
        )
    }
}
