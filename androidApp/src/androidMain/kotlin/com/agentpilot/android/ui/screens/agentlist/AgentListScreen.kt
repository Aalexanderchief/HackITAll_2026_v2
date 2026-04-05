package com.agentpilot.android.ui.screens.agentlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentpilot.android.ui.components.AgentStatusCard
import com.agentpilot.shared.models.AgentStatus
import com.agentpilot.shared.network.ConnectionState
import com.agentpilot.shared.platform.QrScannerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(
    onAgentClick: (String) -> Unit,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    viewModel: AgentListViewModel = viewModel()
) {
    val agents by viewModel.filteredAgents.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var showQrScanner by remember { mutableStateOf(false) }

    if (showQrScanner) {
        QrScannerScreen(
            onScanned = { url ->
                // url is a ws:// address; strip the scheme and path to get host:port for the VM
                val hostPort = url.removePrefix("ws://").removePrefix("wss://").substringBefore("/")
                viewModel.connectViaIp(hostPort)
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AgentPilot") },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode"
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
            ConnectRow(
                connectionState = connectionState,
                onConnect = { ip -> viewModel.connectViaIp(ip) },
                onDisconnect = { viewModel.disconnect() },
                onScanQr = { showQrScanner = true }
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
    onDisconnect: () -> Unit,
    onScanQr: () -> Unit = {},
) {
    var ip by remember { mutableStateOf("10.0.2.2") }
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
                placeholder = { Text("10.0.2.2 (emulator) or LAN IP") },
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

            IconButton(
                onClick = onScanQr,
                enabled = !isConnected && !isConnecting
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR code"
                )
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
