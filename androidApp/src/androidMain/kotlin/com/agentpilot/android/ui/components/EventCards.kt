package com.agentpilot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentpilot.android.ui.theme.CodeTypography
import com.agentpilot.shared.models.AgentMessage

/**
 * Timeline event card for agent status updates.
 */
@Composable
fun AgentStatusEventCard(
    event: AgentMessage.AgentStatusUpdate,
    modifier: Modifier = Modifier
) {
    EventCardContainer(
        icon = Icons.Default.Info,
        iconTint = MaterialTheme.colorScheme.primary,
        title = "Status Update",
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.status.name.replace('_', ' '),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(event.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { event.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.currentTask,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Timeline event card for LLM requests.
 */
@Composable
fun LlmRequestEventCard(
    event: AgentMessage.LlmRequestCapture,
    modifier: Modifier = Modifier
) {
    EventCardContainer(
        icon = Icons.Default.Send,
        iconTint = Color(0xFF9C27B0), // Purple
        title = "LLM Request",
        subtitle = event.model,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = event.prompt,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5
            )
        }
    }
}

/**
 * Timeline event card for LLM response chunks (streaming).
 */
@Composable
fun LlmResponseEventCard(
    requestId: String,
    responseText: String,
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    EventCardContainer(
        icon = if (isComplete) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
        iconTint = if (isComplete) Color(0xFF4CAF50) else Color(0xFFFF9800),
        title = "LLM Response",
        subtitle = if (isComplete) "Complete" else "Streaming...",
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = responseText,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 10
            )
        }
    }
}

/**
 * Timeline event card for clarification requests.
 */
@Composable
fun ClarificationRequestEventCard(
    event: AgentMessage.ClarificationRequest,
    onRespond: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EventCardContainer(
        icon = Icons.Default.QuestionMark,
        iconTint = Color(0xFFFF9800), // Orange
        title = "Clarification Needed",
        modifier = modifier
    ) {
        Column {
            Text(
                text = event.question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (event.context.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = event.context,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            event.options?.let { options ->
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { option ->
                    OutlinedButton(
                        onClick = { onRespond?.invoke(option) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(option)
                    }
                }
            }
        }
    }
}

/**
 * Timeline event card for code change proposals.
 */
@Composable
fun CodeChangeProposalEventCard(
    event: AgentMessage.CodeChangeProposal,
    onReview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EventCardContainer(
        icon = Icons.Default.Code,
        iconTint = Color(0xFF2196F3), // Blue
        title = "Code Change Proposal",
        subtitle = event.filePath,
        modifier = modifier
    ) {
        Column {
            Text(
                text = event.explanation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            CompactDiffView(
                diff = event.diff,
                modifier = Modifier.fillMaxWidth()
            )

            if (onReview != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Review Code Change")
                }
            }
        }
    }
}

/**
 * Base container for timeline event cards.
 */
@Composable
private fun EventCardContainer(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconTint.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                content()
            }
        }
    }
}
