package com.agentpilot.android.ui.screens.agentlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentpilot.android.ui.components.AgentStatusCard
import com.agentpilot.shared.models.AgentStatus

/**
 * Agent List Screen - displays all active agents with their current status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(
    onAgentClick: (String) -> Unit,
    viewModel: AgentListViewModel = viewModel()
) {
    val agents by viewModel.filteredAgents.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AgentPilot") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            StatusFilterRow(
                selectedStatus = filterStatus,
                onFilterSelected = { status ->
                    if (filterStatus == status) {
                        viewModel.clearFilter()
                    } else {
                        viewModel.setFilter(status)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Agent list
            if (agents.isEmpty()) {
                EmptyAgentList(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = agents,
                        key = { it.agentId }
                    ) { agent ->
                        AgentStatusCard(
                            agentUpdate = agent,
                            onClick = { onAgentClick(agent.agentId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Filter chips for agent status.
 */
@Composable
private fun StatusFilterRow(
    selectedStatus: AgentStatus?,
    onFilterSelected: (AgentStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(AgentStatus.entries) { status ->
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onFilterSelected(status) },
                label = {
                    Text(
                        text = status.name.replace('_', ' '),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

/**
 * Empty state when no agents match the filter.
 */
@Composable
private fun EmptyAgentList(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No agents found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Agents will appear here when they start working",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
