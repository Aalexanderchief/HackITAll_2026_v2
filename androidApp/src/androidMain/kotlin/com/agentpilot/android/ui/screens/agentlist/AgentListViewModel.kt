package com.agentpilot.android.ui.screens.agentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentpilot.android.AgentPilotApplication
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus
import com.agentpilot.shared.network.ConnectionState
import kotlinx.coroutines.flow.*

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

    fun connectViaIp(ip: String) = connectionViewModel.connectViaIp(ip)

    fun disconnect() = connectionViewModel.disconnect()

    fun setFilter(status: AgentStatus?) { _filterStatus.value = status }

    fun clearFilter() { _filterStatus.value = null }

    override fun onCleared() {
        super.onCleared()
        connectionViewModel.onCleared()
    }
}
