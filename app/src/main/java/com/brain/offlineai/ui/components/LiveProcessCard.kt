package com.brain.offlineai.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Live process card - the "N steps" expandable list from the Claude-style
 * UI spec (section 2 "LIVE PROCESS ANIMATION" + "EXPANDABLE STEPS VIEW" +
 * "SUMMARY PANEL"). Every [ProcessStep] passed in must be real (Rule 1/10)
 * - this composable only renders state, it never invents steps.
 *
 * Collapsed: header ("N steps" + chevron) plus only the current
 * running/last step, matching the mockup's compact chat-list row.
 * Expanded (tap header): every step, in order, each with its own
 * running-animation or complete/failed icon.
 * Summary (tap "Summary"): a flat checklist of every completed step's
 * label only - the mockup's bottom "Summary" panel. Kept as an inline
 * toggle rather than a modal bottom sheet for this phase (documented
 * simplification, not a faked interaction - see PROGRESS.md Phase 8 notes).
 */
@Composable
fun LiveProcessCard(steps: List<ProcessStep>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    var expanded by remember { mutableStateOf(steps.size <= 1) }
    var showSummary by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BrainBgCard)
            .padding(12.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${steps.size} step${if (steps.size == 1) "" else "s"}",
                color = BrainTextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse steps" else "Expand steps",
                tint = BrainTextMuted
            )
        }

        Spacer(Modifier.height(6.dp))

        val visibleSteps = if (expanded) steps else steps.takeLast(1)
        visibleSteps.forEach { step ->
            ProcessStepRow(step)
        }

        val completedCount = steps.count { it.status == ProcessStepStatus.COMPLETE }
        if (completedCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (showSummary) "Hide summary" else "Summary",
                color = BrainPurplePrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable { showSummary = !showSummary }
            )
            if (showSummary) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    steps.filter { it.status == ProcessStepStatus.COMPLETE }.forEach { step ->
                        Text(
                            "\u2713 ${step.displayLabel}",
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
private fun ProcessStepRow(step: ProcessStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(step.marking.icon, modifier = Modifier.width(22.dp))
        Text(
            step.displayLabel,
            color = when (step.status) {
                ProcessStepStatus.FAILED -> BrainDangerRed
                ProcessStepStatus.COMPLETE -> BrainTextPrimary
                ProcessStepStatus.RUNNING -> BrainTextSecondary
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        when (step.status) {
            ProcessStepStatus.RUNNING -> RunningDots()
            ProcessStepStatus.COMPLETE -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Complete",
                tint = BrainSuccessGreen,
                modifier = Modifier.size(16.dp)
            )
            ProcessStepStatus.FAILED -> Icon(
                Icons.Filled.Warning,
                contentDescription = "Failed",
                tint = BrainDangerRed,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Three pulsing dots - the running-state animation used next to every
 *  in-progress marking (spec section 2's "..." animated marks). A real,
 *  infinitely-repeating Compose animation, not a static ellipsis. */
@Composable
private fun RunningDots() {
    val transition = rememberInfiniteTransition(label = "process-step-running")
    Row {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot-$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BrainPurplePrimary.copy(alpha = alpha))
            )
        }
    }
}
