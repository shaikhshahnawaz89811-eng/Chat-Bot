package com.brain.offlineai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.brain.offlineai.data.settings.AppSettingsState
import com.brain.offlineai.ui.screens.chat.ChatMessage
import com.brain.offlineai.ui.screens.chat.ThinkingStep
import com.brain.offlineai.ui.theme.*
import kotlin.math.sin

@Composable
fun UserBubble(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(BrainPurpleBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 260.dp)
            ) {
                Text(message.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
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

@Composable
fun BotTextBubble(message: ChatMessage) {
    BotCardShell {
        Text(message.text, color = BrainTextPrimary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(message.timestamp, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BotSystemNoteBubble(message: ChatMessage) {
    BotCardShell(borderColor = BrainWarningAmber.copy(alpha = 0.4f)) {
        Text(message.text, color = BrainTextSecondary, style = MaterialTheme.typography.bodyMedium)
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
            Text("Brain is thinking...", color = BrainTextPrimary, style = MaterialTheme.typography.titleMedium)
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

@Composable
fun BotCodingBubble(message: ChatMessage) {
    BotCardShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = BrainCyanAccent)
            Spacer(Modifier.width(8.dp))
            Text("Coding...", color = BrainTextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BrainBgPrimary)
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
 * Phase 2: shows the real text streaming in from llama.cpp token-by-token,
 * plus a live token counter. There's no fake percent-complete bar anymore -
 * a real generation can legitimately stop anywhere between 1 token and the
 * max-token cap depending on when the model emits its own end-of-turn
 * token, so a fabricated "% done" number would be misleading.
 */
@Composable
fun BotGeneratingBubble(message: ChatMessage) {
    BotCardShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = BrainPurplePrimary)
            Spacer(Modifier.width(8.dp))
            Text("Generating...", color = BrainTextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("${message.generationProgress} tokens", color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (message.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message.text, color = BrainTextPrimary, style = MaterialTheme.typography.bodyLarge)
        } else {
            Spacer(Modifier.height(10.dp))
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
    content: @Composable ColumnScope.() -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        Column(
            modifier = Modifier
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
