package com.agentpilot.android.ui.screens.agentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentpilot.shared.data.MockDataProvider
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Agent List screen.
 * Manages the list of agents and their current status.
 */
class AgentListViewModel : ViewModel() {

    // Map of agent ID to latest status update
    private val _agents = MutableStateFlow<Map<String, AgentMessage.AgentStatusUpdate>>(emptyMap())

    // Public state flow of agent list
    val agents: StateFlow<List<AgentMessage.AgentStatusUpdate>> = _agents
        .map { it.values.sortedByDescending { agent -> agent.agentId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Selected filter status (null = show all)
    private val _filterStatus = MutableStateFlow<AgentStatus?>(null)
    val filterStatus: StateFlow<AgentStatus?> = _filterStatus.asStateFlow()

    // Filtered agent list
    val filteredAgents: StateFlow<List<AgentMessage.AgentStatusUpdate>> = combine(
        agents,
        filterStatus
    ) { agentList, filter ->
        if (filter == null) {
            agentList
        } else {
            agentList.filter { it.status == filter }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Load sample agents on initialization
        loadSampleAgents()

        // Optionally, start listening to mock event stream (for demo)
        // startMockEventStream()
    }

    /**
     * Load static sample agents for testing UI.
     */
    private fun loadSampleAgents() {
        val sampleAgents = MockDataProvider.sampleAgentStates()
        val agentMap = sampleAgents.associateBy { it.agentId }
        _agents.value = agentMap
    }

    /**
     * Start listening to mock event stream (simulates real-time updates).
     * Uncomment the call in init() to enable.
     */
    private fun startMockEventStream() {
        viewModelScope.launch {
            MockDataProvider.mockAgentEventStream()
                .collect { message ->
                    when (message) {
                        is AgentMessage.AgentStatusUpdate -> {
                            updateAgent(message)
                        }
                        else -> {
                            // Ignore other message types for now
                        }
                    }
                }
        }
    }

    /**
     * Update or add an agent status.
     */
    private fun updateAgent(update: AgentMessage.AgentStatusUpdate) {
        _agents.update { currentMap ->
            currentMap + (update.agentId to update)
        }
    }

    /**
     * Set filter by status.
     */
    fun setFilter(status: AgentStatus?) {
        _filterStatus.value = status
    }

    /**
     * Clear all filters.
     */
    fun clearFilter() {
        _filterStatus.value = null
    }
}
