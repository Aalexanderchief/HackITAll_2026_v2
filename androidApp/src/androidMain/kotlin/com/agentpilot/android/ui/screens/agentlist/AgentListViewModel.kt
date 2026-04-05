package com.agentpilot.android.ui.screens.agentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentpilot.android.AgentPilotApplication
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus
import com.agentpilot.shared.models.InputSource
import com.agentpilot.shared.network.ConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AgentListViewModel : ViewModel() {

    private val connectionViewModel = AgentPilotApplication.connectionViewModel

    val connectionState: StateFlow<ConnectionState> = connectionViewModel.connectionState

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

    /**
     * Accepts either a raw IP address ("10.0.0.5") or a discovery token ("JB-482-XKQ").
     * Tokens are sent as UDP broadcasts so the plugin can reply with the WebSocket URL.
     */
    fun connect(input: String) {
        val trimmed = input.trim()
        if (trimmed.startsWith("JB-", ignoreCase = true)) {
            connectionViewModel.connectViaCode(trimmed.uppercase())
        } else {
            connectionViewModel.connectViaIp(trimmed)
        }
    }

    fun disconnect() = connectionViewModel.disconnect()

    fun setFilter(status: AgentStatus?) { _filterStatus.value = status }

    fun clearFilter() { _filterStatus.value = null }

    fun respondToClarification(id: String, approved: Boolean) {
        viewModelScope.launch {
            connectionViewModel.send(
                AgentMessage.ClarificationResponse(
                    id = id,
                    answer = if (approved) "approved" else "rejected",
                    source = InputSource.TEXT
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionViewModel.onCleared()
    }
}
