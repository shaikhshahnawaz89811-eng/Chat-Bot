package com.brain.offlineai.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.brain.offlineai.ui.tasks.TaskItem
import com.brain.offlineai.ui.tasks.TaskStatus
import com.brain.offlineai.ui.theme.*

/**
 * Phase 12 (Multi-task handling engine, spec section 6) - the real
 * breakdown checklist for a message [com.brain.offlineai.ui.tasks.TaskSplitter]
 * genuinely split into more than one task. Every [TaskItem.status] shown
 * here is real (Rule 1/10/17) - it only ever reflects
 * [com.brain.offlineai.ui.screens.chat.ChatViewModel.runMultiTaskMessage]'s
 * actual sequential-execution progress, never a fixed animation
 * independent of real work. This is deliberately its own small composable
 * (Rule 21) rather than folded into [LiveProcessCard] - a task has a real
 * PENDING state before it starts (the whole breakdown is known up front),
 * which [com.brain.offlineai.ui.process.ProcessStep] has no equivalent
 * for (a process step is only ever added once it's already running).
 */
@Composable
fun TaskListCard(tasks: List<TaskItem>, modifier: Modifier = Modifier) {
    if (tasks.isEmpty()) return
    val completedCount = tasks.count { it.status == TaskStatus.COMPLETE || it.status == TaskStatus.FAILED }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BrainBgCard)
            .padding(12.dp)
            .animateContentSize()
    ) {
        Text(
            "$completedCount of ${tasks.size} tasks done",
            color = BrainTextSecondary,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(6.dp))
        tasks.forEach { task -> TaskRow(task) }
    }
}

@Composable
private fun TaskRow(task: TaskItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (task.status) {
            TaskStatus.PENDING -> Icon(
                Icons.Filled.RadioButtonUnchecked,
                contentDescription = "Pending",
                tint = BrainTextMuted,
                modifier = Modifier.size(16.dp)
            )
            TaskStatus.RUNNING -> TaskRunningDot()
            TaskStatus.COMPLETE -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Done",
                tint = BrainSuccessGreen,
                modifier = Modifier.size(16.dp)
            )
            TaskStatus.FAILED -> Icon(
                Icons.Filled.Warning,
                contentDescription = "Failed",
                tint = BrainDangerRed,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Task ${task.index}: ${task.description}",
            color = when (task.status) {
                TaskStatus.PENDING -> BrainTextMuted
                TaskStatus.RUNNING -> BrainTextPrimary
                TaskStatus.COMPLETE -> BrainTextSecondary
                TaskStatus.FAILED -> BrainDangerRed
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Same real, infinitely-repeating pulsing animation convention as
 *  [LiveProcessCard]'s own `RunningDots` - a single pulsing dot here
 *  since a task row (unlike a process-step row) already carries its own
 *  index number, so three separate dots would be redundant. */
@Composable
private fun TaskRunningDot() {
    val transition = rememberInfiniteTransition(label = "task-running")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "task-running-alpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(BrainPurplePrimary.copy(alpha = alpha))
    )
}
