package com.agentpilot.android.ui.screens.agentdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentpilot.android.ui.components.*

/**
 * Agent Detail Screen - shows timeline of events for a specific agent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    agentId: String,
    onBackClick: () -> Unit
) {
    // Create ViewModel with agentId
    val viewModel: AgentDetailViewModel = viewModel(
        key = agentId,
        factory = AgentDetailViewModelFactory(agentId)
    )

    val agentStatus by viewModel.agentStatus.collectAsState()
    val timelineEvents by viewModel.timelineEvents.collectAsState()
    val pendingClarification by viewModel.pendingClarification.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agent Details")
                        agentStatus?.let { status ->
                            Text(
                                text = agentId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            // Agent status header
            agentStatus?.let { status ->
                AgentStatusHeader(
                    agentUpdate = status,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Divider()

            // Timeline events
            if (timelineEvents.isEmpty()) {
                EmptyTimeline(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                val listState = rememberLazyListState()

                // Auto-scroll to latest event when new events arrive
                LaunchedEffect(timelineEvents.size) {
                    if (timelineEvents.isNotEmpty()) {
                        listState.animateScrollToItem(timelineEvents.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = timelineEvents,
                        key = { it.hashCode() }
                    ) { event ->
                        when (event) {
                            is TimelineEvent.StatusUpdate -> {
                                AgentStatusEventCard(event = event.event)
                            }

                            is TimelineEvent.LlmRequest -> {
                                LlmRequestEventCard(event = event.event)
                            }

                            is TimelineEvent.LlmResponse -> {
                                LlmResponseEventCard(
                                    requestId = event.requestId,
                                    responseText = event.response,
                                    isComplete = event.isComplete
                                )
                            }

                            is TimelineEvent.ClarificationRequest -> {
                                ClarificationRequestEventCard(
                                    event = event.event,
                                    onRespond = { answer ->
                                        viewModel.sendClarificationResponse(answer)
                                    }
                                )
                            }

                            is TimelineEvent.CodeProposal -> {
                                CodeChangeProposalEventCard(
                                    event = event.event,
                                    onReview = {
                                        viewModel.openCodeReview()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Show clarification dialog if pending
    pendingClarification?.let { clarification ->
        ClarificationDialog(
            clarification = clarification,
            onDismiss = { /* Keep dialog open until response sent */ },
            onRespond = { answer ->
                viewModel.sendClarificationResponse(answer)
            }
        )
    }
}

/**
 * Agent status header card.
 */
@Composable
private fun AgentStatusHeader(
    agentUpdate: com.agentpilot.shared.models.AgentMessage.AgentStatusUpdate,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = agentUpdate.status.name.replace('_', ' '),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${(agentUpdate.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { agentUpdate.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = agentUpdate.currentTask,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Empty state when no events are available.
 */
@Composable
private fun EmptyTimeline(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No events yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Events will appear here as the agent works",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
