package com.agentpilot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentpilot.android.ui.theme.*
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus

/**
 * Card component displaying agent status information.
 * Shows agent ID, status badge, progress bar, and current task.
 */
@Composable
fun AgentStatusCard(
    agentUpdate: AgentMessage.AgentStatusUpdate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Agent ID + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = agentUpdate.agentId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                StatusBadge(status = agentUpdate.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(agentUpdate.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { agentUpdate.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = getStatusColor(agentUpdate.status),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current Task
            Text(
                text = agentUpdate.currentTask,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Status badge chip showing agent status with appropriate color.
 */
@Composable
fun StatusBadge(
    status: AgentStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = getStatusColors(status)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Text(
            text = status.name.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Returns the primary color for a given agent status.
 */
@Composable
private fun getStatusColor(status: AgentStatus): Color {
    return when (status) {
        AgentStatus.IDLE -> StatusIdle
        AgentStatus.RUNNING -> StatusRunning
        AgentStatus.WAITING_FOR_INPUT -> StatusWaitingInput
        AgentStatus.WAITING_FOR_REVIEW -> StatusWaitingReview
        AgentStatus.COMPLETED -> StatusCompleted
        AgentStatus.FAILED -> StatusFailed
    }
}

/**
 * Returns background and text colors for status badges.
 */
@Composable
private fun getStatusColors(status: AgentStatus): Pair<Color, Color> {
    val bgColor = when (status) {
        AgentStatus.IDLE -> StatusIdle.copy(alpha = 0.2f)
        AgentStatus.RUNNING -> StatusRunning.copy(alpha = 0.2f)
        AgentStatus.WAITING_FOR_INPUT -> StatusWaitingInput.copy(alpha = 0.2f)
        AgentStatus.WAITING_FOR_REVIEW -> StatusWaitingReview.copy(alpha = 0.2f)
        AgentStatus.COMPLETED -> StatusCompleted.copy(alpha = 0.2f)
        AgentStatus.FAILED -> StatusFailed.copy(alpha = 0.2f)
    }

    val textColor = getStatusColor(status)

    return Pair(bgColor, textColor)
}
