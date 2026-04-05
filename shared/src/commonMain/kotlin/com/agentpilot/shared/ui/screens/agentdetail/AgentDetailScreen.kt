package com.agentpilot.shared.ui.screens.agentdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    agentId: String,
    agentStatus: AgentMessage.AgentStatusUpdate?,
    activityFeed: List<AgentMessage>,
    onBackClick: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activityFeed.size) {
        if (activityFeed.isNotEmpty()) {
            listState.animateScrollToItem(activityFeed.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(agentId, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Status header
            agentStatus?.let { status ->
                AgentStatusHeader(status)
                HorizontalDivider()
            }

            // Activity feed
            if (activityFeed.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No activity yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activityFeed) { message ->
                        ActivityFeedItem(message)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentStatusHeader(status: AgentMessage.AgentStatusUpdate) {
    val statusColor = when (status.status) {
        AgentStatus.RUNNING -> Color(0xFF4CAF50)
        AgentStatus.WAITING_FOR_INPUT, AgentStatus.WAITING_FOR_REVIEW -> Color(0xFFFFC107)
        AgentStatus.FAILED -> Color(0xFFFF5722)
        AgentStatus.COMPLETED -> Color(0xFF2196F3)
        AgentStatus.IDLE -> Color(0xFF9E9E9E)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("●", color = statusColor, style = MaterialTheme.typography.titleLarge)
            Text(
                text = status.status.name.replace('_', ' '),
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(status.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = status.currentTask,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        LinearProgressIndicator(
            progress = { status.progress },
            modifier = Modifier.fillMaxWidth(),
            color = statusColor
        )
    }
}

@Composable
private fun ActivityFeedItem(message: AgentMessage) {
    when (message) {
        is AgentMessage.AgentStatusUpdate -> StatusUpdateItem(message)
        is AgentMessage.ClarificationRequest -> ClarificationRequestItem(message)
        is AgentMessage.ClarificationResponse -> ClarificationResponseItem(message)
        is AgentMessage.CodeChangeProposal -> CodeProposalItem(message)
        is AgentMessage.CodeChangeVerdict -> CodeVerdictItem(message)
        is AgentMessage.LlmRequestCapture -> LlmRequestItem(message)
        else -> Unit
    }
}

@Composable
private fun StatusUpdateItem(msg: AgentMessage.AgentStatusUpdate) {
    val color = when (msg.status) {
        AgentStatus.FAILED -> Color(0xFFFF5722)
        AgentStatus.COMPLETED -> Color(0xFF2196F3)
        AgentStatus.RUNNING -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = "[${msg.status.name}] ${msg.currentTask}",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun ClarificationRequestItem(msg: AgentMessage.ClarificationRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(msg.question, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (msg.context.isNotBlank()) {
                    Text(msg.context, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun ClarificationResponseItem(msg: AgentMessage.ClarificationResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (msg.source == com.agentpilot.shared.models.InputSource.VOICE) {
                    Icon(Icons.Default.Mic, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                }
                Icon(Icons.Default.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                Text(msg.answer, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun CodeProposalItem(msg: AgentMessage.CodeChangeProposal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E).copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(msg.explanation, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = msg.filePath,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Show first few diff lines as preview
            val previewLines = msg.diff.lines().take(8)
            previewLines.forEach { line ->
                val (bg, fg) = when {
                    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF1B5E20) to Color(0xFFB9F6CA)
                    line.startsWith("-") && !line.startsWith("---") -> Color(0xFF7F0000) to Color(0xFFFF8A80)
                    else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = fg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (bg != Color.Transparent) Modifier.background(bg) else Modifier)
                        .padding(horizontal = 4.dp)
                )
            }
            if (msg.diff.lines().size > 8) {
                Text("…${msg.diff.lines().size - 8} more lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CodeVerdictItem(msg: AgentMessage.CodeChangeVerdict) {
    val accepted = msg.action == com.agentpilot.shared.models.ChangeAction.ACCEPT
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (accepted) Color(0xFF1B5E20).copy(alpha = 0.2f)
                                 else Color(0xFF7F0000).copy(alpha = 0.2f)
            )
        ) {
            Text(
                text = if (accepted) "✓ Accepted" else "✗ Rejected",
                style = MaterialTheme.typography.labelMedium,
                color = if (accepted) Color(0xFF4CAF50) else Color(0xFFFF5722),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun LlmRequestItem(msg: AgentMessage.LlmRequestCapture) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Code, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(
            text = "LLM: ${msg.model} — ${msg.prompt.take(60)}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
