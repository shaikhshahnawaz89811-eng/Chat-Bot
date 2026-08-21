package com.brain.offlineai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.brain.offlineai.data.artifacts.ArtifactDownloadTarget
import com.brain.offlineai.data.artifacts.ArtifactInfo
import com.brain.offlineai.data.settings.AppSettingsState
import com.brain.offlineai.ui.screens.chat.ArtifactDownloadUiState
import com.brain.offlineai.ui.screens.chat.ChatMessage
import com.brain.offlineai.ui.screens.chat.ThinkingStep
import com.brain.offlineai.ui.theme.*
import kotlin.math.sin

@Composable
fun UserBubble(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End) {
            // Phase 10 (File/ZIP/Image/Video upload flow) - real attachments
            // actually sent with this message, rendered above the text bubble
            // (empty for every message from every earlier phase - additive only).
            if (message.attachments.isNotEmpty()) {
                // Phase 14 (Multimodal input use-case routing) - real
                // role lookup by attachment id, from this same message's
                // own [ChatMessage.attachmentRoutes] (empty for every
                // message from before this phase, so `role` is simply
                // null then - additive only).
                val roleByAttachmentId = message.attachmentRoutes.associateBy { it.attachmentId }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.attachments.forEach { attachment ->
                        SentAttachmentCard(attachment, role = roleByAttachmentId[attachment.id]?.role)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (message.text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(BrainPurpleBubble)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .widthIn(max = 260.dp)
                ) {
                    Text(message.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                message.timestamp,
                color = BrainTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            )
        }
    }
}

/**
 * Phase 11 (Artifact card + ZIP/file output + download flow) - real
 * download params are additive with safe no-op defaults, so every earlier
 * phase's call sites with just `message` still compile unchanged
 * (Document-Editing Convention). [message.artifacts] is empty for any
 * plain-prose reply, so the artifact card below simply doesn't render for
 * the vast majority of TEXT messages.
 */
@Composable
fun BotTextBubble(
    message: ChatMessage,
    artifactDownloadStates: Map<String, ArtifactDownloadUiState> = emptyMap(),
    onDownloadArtifact: (ArtifactInfo, ArtifactDownloadTarget) -> Unit = { _, _ -> },
    onDownloadAllArtifacts: (List<ArtifactInfo>) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onPreviewArtifact: (ArtifactInfo) -> Unit = {},
    // GitHub Hosting feature - same additive, safe no-op default
    // convention as every other param on this function.
    onPublishArtifact: (ArtifactInfo) -> Unit = {},
    onPublishAllArtifacts: (List<ArtifactInfo>) -> Unit = {}
) {
    BotCardShell {
        Text(message.text, color = BrainTextPrimary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(message.timestamp, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        if (message.artifacts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ArtifactCard(
                artifacts = message.artifacts,
                artifactSteps = message.artifactSteps,
                downloadStates = artifactDownloadStates,
                zipDownloadId = "zip-${message.id}",
                onDownload = onDownloadArtifact,
                onDownloadAll = onDownloadAllArtifacts,
                onCancelDownload = onCancelDownload,
                onPreview = onPreviewArtifact,
                onPublish = onPublishArtifact,
                onPublishAll = onPublishAllArtifacts
            )
        }
    }
}

/** Phase 8 (new Claude-style UI spec) - renders [ChatMessage.processSteps]
 *  via [LiveProcessCard]. Real call site is [com.brain.offlineai.ui.screens.chat.ChatViewModel]
 *  emitting a genuine THINKING step while it checks engine state / loads
 *  settings before a real generation starts - see that file's sendMessage(). */
@Composable
fun BotProcessBubble(
    message: ChatMessage,
    onOpenUrl: (String) -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        LiveProcessCard(
            steps = message.processSteps,
            modifier = Modifier.widthIn(max = 320.dp),
            onOpenUrl = onOpenUrl
        )
    }
}

/** Phase 12 (Multi-task handling engine, new Claude-style UI spec section
 *  6) - renders [ChatMessage.tasks] via [TaskListCard]. Real call site is
 *  [com.brain.offlineai.ui.screens.chat.ChatViewModel.runMultiTaskMessage],
 *  which is only ever invoked when [com.brain.offlineai.ui.tasks.TaskSplitter]
 *  genuinely found 2+ distinct tasks in the user's own message - an
 *  ordinary single-instruction message never reaches this bubble. */
@Composable
fun BotTaskListBubble(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TaskListCard(tasks = message.tasks, modifier = Modifier.widthIn(max = 280.dp))
    }
}

@Composable
fun BotSystemNoteBubble(message: ChatMessage) {
    val lines = message.text.lines()
    val isChunkReport = lines.count { it.contains("Chunk ") } >= 2 || message.text.contains("Context Info Box")
    val scrollState = rememberScrollState()
    LaunchedEffect(message.text) { if (isChunkReport) scrollState.scrollTo(scrollState.maxValue) }
    BotCardShell(borderColor = BrainWarningAmber.copy(alpha = 0.4f)) {
        if (isChunkReport) {
            val title = lines.firstOrNull()?.take(120) ?: "Project context"
            Text(title, color = BrainTextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(scrollState)
                    .background(BrainBgPrimary, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Text(
                    lines.drop(1).joinToString("\n"),
                    color = BrainTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Text(message.text, color = BrainTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(message.timestamp, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BotThinkingBubble(message: ChatMessage) {
    BotCardShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot()
            Spacer(Modifier.width(8.dp))
            Text("Chat Bot is thinking...", color = BrainTextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        message.thinkingSteps.forEach { step -> ThinkingStepRow(step) }
        if (message.thinkingSteps.isNotEmpty() && message.thinkingSteps.last().done.not()) {
            Spacer(Modifier.height(4.dp))
            TypingDots()
        }
    }
}

@Composable
private fun ThinkingStepRow(step: ThinkingStep) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (step.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (step.done) BrainSuccessGreen else BrainTextMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(step.label, color = if (step.done) BrainTextSecondary else BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Bug-fix history: this card used to also render the model's pre-fence
 * prose (as a stop-gap before the two were split into genuinely separate
 * cards). Now that [ChatViewModel.streamRealResponse] emits that prose as
 * its own, separate finished [BotTextBubble]-style card, this one goes
 * back to rendering only the real code it owns - showing the intro here
 * too would just duplicate the other card.
 */
@Composable
fun BotCodingBubble(message: ChatMessage) {
    val scrollState = rememberScrollState()
    LaunchedEffect(message.generationProgress, message.codeLines.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    BotCardShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = BrainCyanAccent)
            Spacer(Modifier.width(8.dp))
            Text(
                text = message.codeFileName?.let { "Coding · $it" } ?: "Coding...",
                color = BrainTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            Text("${message.generationProgress} tokens", color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BrainBgPrimary)
                .heightIn(max = 280.dp)
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            Column {
                message.codeLines.forEach { line ->
                    Text(
                        text = line,
                        color = BrainCyanAccent,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Completed counterpart of [BotCodingBubble] (Rule 8 Part A / Rule 15 -
 * CODING's missing "done" counterpart, as its own small focused
 * composable rather than more branches crammed into BotCodingBubble).
 * Static checkmark instead of the live pulsing dot, and an explicit
 * "Done." text line + timestamp - the real "ho gaya" signal in text that
 * was missing before, matching what BotTextBubble already shows for a
 * plain finished reply.
 */
/** Phase 11 - same additive, safe-default download params as [BotTextBubble] above. */
@Composable
fun BotCodeDoneBubble(
    message: ChatMessage,
    artifactDownloadStates: Map<String, ArtifactDownloadUiState> = emptyMap(),
    onDownloadArtifact: (ArtifactInfo, ArtifactDownloadTarget) -> Unit = { _, _ -> },
    onDownloadAllArtifacts: (List<ArtifactInfo>) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onPreviewArtifact: (ArtifactInfo) -> Unit = {},
    // GitHub Hosting feature - same additive, safe no-op default
    // convention as every other param on this function.
    onPublishArtifact: (ArtifactInfo) -> Unit = {},
    onPublishAllArtifacts: (List<ArtifactInfo>) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(message.codeLines.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    BotCardShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = BrainSuccessGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message.codeFileName?.let { "Code ready · $it" } ?: "Code ready",
                color = BrainTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BrainBgPrimary)
                .heightIn(max = 280.dp)
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            Column {
                message.codeLines.forEach { line ->
                    Text(
                        text = line,
                        color = BrainCyanAccent,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Done.", color = BrainSuccessGreen, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        Text(message.timestamp, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        if (message.artifacts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ArtifactCard(
                artifacts = message.artifacts,
                artifactSteps = message.artifactSteps,
                downloadStates = artifactDownloadStates,
                zipDownloadId = "zip-${message.id}",
                onDownload = onDownloadArtifact,
                onDownloadAll = onDownloadAllArtifacts,
                onCancelDownload = onCancelDownload,
                onPreview = onPreviewArtifact,
                onPublish = onPublishArtifact,
                onPublishAll = onPublishAllArtifacts
            )
        }
    }
}

/**
 * Phase 2: shows the real text streaming in from llama.cpp token-by-token,
 * plus a live token counter. There's no fake percent-complete bar anymore -
 * a real generation can legitimately stop anywhere between 1 token and the
 * max-token cap depending on when the model emits its own end-of-turn
 * token, so a fabricated "% done" number would be misleading.
 */
@Composable
fun BotGeneratingBubble(message: ChatMessage) {
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    LaunchedEffect(message.generationProgress, expanded) {
        if (expanded) scrollState.scrollTo(scrollState.maxValue)
    }
    BotCardShell(
        modifier = Modifier.clickable { expanded = !expanded }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = BrainPurplePrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                if (message.generationProgress == 0) "Starting..." else "Streaming reply...",
                color = BrainTextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            Text("${message.generationProgress} tokens", color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "▲" else "▼", color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        // Keep the live card compact by default. The real streamed text is
        // still available on tap, inside its own bounded scroll area.
        if (expanded && message.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(scrollState)
                    .background(BrainBgPrimary, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(message.text, color = BrainTextPrimary, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            WaveformAnimation()
        }
    }
}

/**
 * Phase 6: gated on [AppSettingsState.animationsEnabled] (General Settings
 * screen 12's real "Animations" toggle). When disabled this renders a real
 * static bar pattern instead of the animated waveform - a genuinely
 * different draw path, not a toggle that's silently ignored (Rule 17).
 */
@Composable
private fun WaveformAnimation() {
    val barCount = 24
    if (!AppSettingsState.animationsEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until barCount) {
                val heightFraction = 0.35f + 0.3f * ((i % 3).toFloat() / 2f)
                val color = if (i % 2 == 0) BrainPinkAccent else BrainCyanAccent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        return
    }

    val infinite = rememberInfiniteTransition(label = "wave")
    val phase by infinite.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "phase"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val heightFraction = (0.25f + 0.75f * ((sin(phase + i * 0.5f) + 1f) / 2f))
            val color = if (i % 2 == 0) BrainPinkAccent else BrainCyanAccent
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/** Phase 6: same real on/off gate as [WaveformAnimation] - a static full-alpha dot when animations are disabled. */
@Composable
private fun PulsingDot(color: Color = BrainPurplePrimary) {
    if (!AppSettingsState.animationsEnabled) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        return
    }
    val infinite = rememberInfiniteTransition(label = "dot")
    val alpha by infinite.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse), label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/** Phase 6: same real on/off gate - static full-alpha dots (no animation) when disabled. */
@Composable
private fun TypingDots() {
    val animate = AppSettingsState.animationsEnabled
    Row {
        repeat(4) { index ->
            if (animate) {
                val infinite = rememberInfiniteTransition(label = "typingDots$index")
                val alpha by infinite.animateFloat(
                    initialValue = 0.2f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(400, delayMillis = index * 120),
                        repeatMode = RepeatMode.Reverse
                    ), label = "dotAlpha"
                )
                Box(
                    modifier = Modifier
                        .padding(end = 3.dp)
                        .size(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(BrainTextMuted.copy(alpha = alpha))
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(end = 3.dp)
                        .size(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(BrainTextMuted.copy(alpha = 0.7f))
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text("Thinking...", color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BotCardShell(
    borderColor: Color = BrainBorder,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        Column(
            modifier = modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(BrainBgCard)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .padding(14.dp)
        ) {
            content()
        }
    }
}
