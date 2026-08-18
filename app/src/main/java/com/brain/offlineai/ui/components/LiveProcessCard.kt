package com.brain.offlineai.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brain.offlineai.ui.process.ProcessStep
import com.brain.offlineai.ui.process.ProcessStepStatus
import com.brain.offlineai.ui.theme.*

/**
 * Visual-only upgrade of the EXISTING process component.
 *
 * It renders only the ProcessStep objects supplied by the existing engine.
 * No fake steps or task execution is introduced.
 */
@Composable
fun LiveProcessCard(
    steps: List<ProcessStep>,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty()) return

    var expanded by remember { mutableStateOf(steps.size <= 1) }
    var showSummary by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "process-glow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "process-glow-alpha"
    )

    val running = steps.any { it.status == ProcessStepStatus.RUNNING }
    val failed = steps.any { it.status == ProcessStepStatus.FAILED }
    val complete = steps.isNotEmpty() && steps.all {
        it.status == ProcessStepStatus.COMPLETE
    }

    val accent = when {
        failed -> BrainDangerRed
        complete -> BrainSuccessGreen
        running -> BrainPurplePrimary
        else -> BrainTextMuted
    }

    val borderColor = accent.copy(
        alpha = if (running) glowAlpha else 0.38f
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BrainBgCard)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .animateContentSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when {
                        failed -> "Process needs attention"
                        complete -> "Process completed"
                        running -> "Working"
                        else -> "${steps.size} step${if (steps.size == 1) "" else "s"}"
                    },
                    color = BrainTextPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${steps.count { it.status == ProcessStepStatus.COMPLETE }}/${steps.size} complete",
                    color = BrainTextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess
                else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse steps" else "Expand steps",
                tint = accent
            )
        }

        Spacer(Modifier.height(8.dp))

        // Real progress indicator derived only from existing step statuses.
        LinearProgressIndicator(
            progress = if (steps.isEmpty()) 0f else steps.count {
                it.status == ProcessStepStatus.COMPLETE
            }.toFloat() / steps.size.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = accent,
            trackColor = BrainBorder
        )

        Spacer(Modifier.height(8.dp))

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                steps.forEachIndexed { index, step ->
                    ProcessStepRow(
                        index = index + 1,
                        step = step,
                        accent = accent
                    )
                }
            }
        } else {
            val current = steps.lastOrNull {
                it.status == ProcessStepStatus.RUNNING
            } ?: steps.last()

            ProcessStepRow(
                index = steps.indexOf(current) + 1,
                step = current,
                accent = accent
            )
        }

        val completedCount = steps.count {
            it.status == ProcessStepStatus.COMPLETE
        }

        if (completedCount > 0) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (showSummary) "Hide summary" else "Summary",
                color = BrainPurplePrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable {
                    showSummary = !showSummary
                }
            )

            if (showSummary) {
                Column(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    steps
                        .filter { it.status == ProcessStepStatus.COMPLETE }
                        .forEach {
                            Text(
                                "✓ ${it.displayLabel}",
                                color = BrainSuccessGreen,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun ProcessStepRow(
    index: Int,
    step: ProcessStep,
    accent: Color
) {
    val running = step.status == ProcessStepStatus.RUNNING

    val rowColor = when (step.status) {
        ProcessStepStatus.FAILED -> BrainDangerRed
        ProcessStepStatus.COMPLETE -> BrainSuccessGreen
        ProcessStepStatus.RUNNING -> accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            color = rowColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(26.dp)
        )

        Text(
            text = step.marking.icon,
            modifier = Modifier.width(24.dp)
        )

        Text(
            text = step.displayLabel,
            color = when (step.status) {
                ProcessStepStatus.FAILED -> BrainDangerRed
                ProcessStepStatus.COMPLETE -> BrainTextPrimary
                ProcessStepStatus.RUNNING -> BrainTextPrimary
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )

        when {
            step.status == ProcessStepStatus.RUNNING -> RunningDots()
            step.status == ProcessStepStatus.COMPLETE -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Complete",
                tint = BrainSuccessGreen,
                modifier = Modifier.size(17.dp)
            )
            else -> Icon(
                Icons.Filled.Warning,
                contentDescription = "Failed",
                tint = BrainDangerRed,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun RunningDots() {
    val transition = rememberInfiniteTransition(label = "running-dots")

    Row {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        delayMillis = index * 150
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "running-dot-$index"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        BrainPurplePrimary.copy(alpha = alpha)
                    )
            )
        }
    }
}
