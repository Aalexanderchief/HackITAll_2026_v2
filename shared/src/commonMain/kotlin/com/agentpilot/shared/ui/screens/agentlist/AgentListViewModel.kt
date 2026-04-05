package com.agentpilot.shared.ui.screens.agentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentpilot.shared.AppState
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus
import com.agentpilot.shared.models.ChangeAction
import com.agentpilot.shared.models.InputSource
import com.agentpilot.shared.network.ConnectionState
import com.agentpilot.shared.network.DiscoveryState
import com.agentpilot.shared.platform.wifiBroadcastAddress
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AgentListViewModel : ViewModel() {

    private val connectionViewModel = AppState.connectionViewModel

    val connectionState: StateFlow<ConnectionState> = connectionViewModel.connectionState
    val discoveryState: StateFlow<DiscoveryState> = connectionViewModel.discoveryState

    private val _filterStatus = MutableStateFlow<AgentStatus?>(null)
    val filterStatus: StateFlow<AgentStatus?> = _filterStatus.asStateFlow()

    val agents: StateFlow<List<AgentMessage.AgentStatusUpdate>> = connectionViewModel.agentStatuses
        .map { it.values.sortedByDescending { agent -> agent.agentId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredAgents: StateFlow<List<AgentMessage.AgentStatusUpdate>> = combine(
        agents,
        filterStatus
    ) { agentList, filter ->
        if (filter == null) agentList else agentList.filter { it.status == filter }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeClarification: StateFlow<AgentMessage.ClarificationRequest?> =
        connectionViewModel.activeClarification

    val activeCodeReview: StateFlow<AgentMessage.CodeChangeProposal?> =
        connectionViewModel.activeCodeReview

    /**
     * Unified connect: auto-detects JB-token vs plain IP.
     * JB-xxx tokens trigger UDP broadcast discovery; IP strings connect directly.
     */
    fun connect(input: String) {
        val trimmed = input.trim()
        if (trimmed.startsWith("JB-", ignoreCase = true)) {
            connectionViewModel.connectViaCode(trimmed.uppercase(), wifiBroadcastAddress())
        } else {
            connectionViewModel.connectViaIp(trimmed)
        }
    }

    fun disconnect() = connectionViewModel.disconnect()

    fun setFilter(status: AgentStatus?) { _filterStatus.value = status }
    fun clearFilter() { _filterStatus.value = null }

    fun respondToClarification(
        id: String,
        approved: Boolean = true,
        customAnswer: String? = null,
        source: InputSource = InputSource.TEXT
    ) {
        viewModelScope.launch {
            connectionViewModel.send(
                AgentMessage.ClarificationResponse(
                    id = id,
                    answer = customAnswer ?: if (approved) "approved" else "rejected",
                    source = source
                )
            )
        }
    }

    fun submitVerdict(id: String, accepted: Boolean) {
        viewModelScope.launch {
            connectionViewModel.send(
                AgentMessage.CodeChangeVerdict(
                    id = id,
                    action = if (accepted) ChangeAction.ACCEPT else ChangeAction.REJECT,
                    alternative = null
                )
            )
        }
    }
}
