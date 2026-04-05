package com.agentpilot.android.ui.screens.agentlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentpilot.android.ui.components.AgentStatusCard
import com.agentpilot.shared.models.AgentStatus
import com.agentpilot.shared.network.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(
    onAgentClick: (String) -> Unit,
    viewModel: AgentListViewModel = viewModel()
) {
    val agents by viewModel.filteredAgents.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val activeClarification by viewModel.activeClarification.collectAsState()
    val activeCodeReview by viewModel.activeCodeReview.collectAsState()

    // Show only one dialog at a time: code review takes priority over command approval.
    // This prevents stacking when an agent runs a command immediately after a file write.
    if (activeCodeReview != null) {
        val proposal = activeCodeReview!!
        CodeReviewSheet(
            proposal = proposal,
            onAccept = { viewModel.submitVerdict(proposal.id, accepted = true) },
            onReject = { viewModel.submitVerdict(proposal.id, accepted = false) }
        )
    } else {
        activeClarification?.let { request ->
            ApprovalDialog(
                toolName = request.question,
                context = request.context,
                onApprove = { viewModel.respondToClarification(request.id, approved = true) },
                onReject  = { viewModel.respondToClarification(request.id, approved = false) }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AgentPilot") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ConnectRow(
                connectionState = connectionState,
                onConnect    = { input -> viewModel.connect(input) },
                onDisconnect = { viewModel.disconnect() }
            )

            HorizontalDivider()

            StatusFilterRow(
                selectedStatus = filterStatus,
                onFilterSelected = { status ->
                    if (filterStatus == status) viewModel.clearFilter()
                    else viewModel.setFilter(status)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

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
                    items(items = agents, key = { it.agentId }) { agent ->
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

@Composable
private fun ConnectRow(
    connectionState: ConnectionState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    var ip by remember { mutableStateOf("") }
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting
    val isFailed = connectionState is ConnectionState.Failed

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dotColor = when (connectionState) {
                is ConnectionState.Connected -> Color(0xFF4CAF50)
                is ConnectionState.Connecting -> Color(0xFFFFC107)
                is ConnectionState.Failed -> Color(0xFFFF5722)
                else -> Color(0xFF9E9E9E)
            }
            Text("●", color = dotColor)

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                placeholder = { Text("IP (10.x.x.x) or token (JB-123-ABC)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = !isConnected && !isConnecting,
                textStyle = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = { if (isConnected) onDisconnect() else onConnect(ip) },
                enabled = !isConnecting && (isConnected || ip.isNotBlank())
            ) {
                Text(if (isConnected) "Disconnect" else if (isConnecting) "..." else "Connect")
            }
        }

        if (isFailed) {
            val errorMsg = (connectionState as ConnectionState.Failed).cause.message ?: "Unknown error"
            Text(
                text = "Error: $errorMsg",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF5722),
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp)
            )
        }
        if (isConnected) {
            Text(
                text = "Connected to ${(connectionState as ConnectionState.Connected).peerVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp)
            )
        }
    }
}

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

@Composable
private fun ApprovalDialog(
    toolName: String,
    context: String,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(toolName, style = MaterialTheme.typography.titleMedium) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Claude Code wants to run:", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(context, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onApprove) { Text("Approve") }
        },
        dismissButton = {
            OutlinedButton(onClick = onReject) { Text("Reject") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeReviewSheet(
    proposal: com.agentpilot.shared.models.AgentMessage.CodeChangeProposal,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // New-file proposals have only additions — no prior content to reject.
    // Modifications have deletions (red lines) and need explicit Accept/Reject.
    val isNewFile = proposal.explanation.startsWith("New file:")

    ModalBottomSheet(
        onDismissRequest = onReject,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = proposal.explanation,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = proposal.filePath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(proposal.diff.lines()) { line ->
                    val (bg, fg) = when {
                        line.startsWith("+") && !line.startsWith("+++") ->
                            Color(0xFF1B5E20) to Color(0xFFB9F6CA)
                        line.startsWith("-") && !line.startsWith("---") ->
                            Color(0xFF7F0000) to Color(0xFFFF8A80)
                        line.startsWith("@@") ->
                            Color(0xFF0D47A1) to Color(0xFFBBDEFB)
                        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = fg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (bg != Color.Transparent) Modifier.background(bg) else Modifier)
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    )
                }
            }

            HorizontalDivider()

            if (isNewFile) {
                // New file: informational only — nothing to reject.
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("OK") }
            } else {
                // Modification: show diff of what changes — user decides.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    ) { Text("Reject") }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    ) { Text("Accept") }
                }
            }
        }
    }
}

@Composable
private fun EmptyAgentList(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No agents found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect to IntelliJ and trigger an AI action",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
