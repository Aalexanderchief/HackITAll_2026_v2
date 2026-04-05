package com.agentpilot.android.ui.screens.agentdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentpilot.shared.AppState
import com.agentpilot.shared.models.AgentMessage
import kotlinx.coroutines.flow.*

class AgentDetailViewModel(private val agentId: String) : ViewModel() {

    private val connectionViewModel = AppState.connectionViewModel

    /** Latest status snapshot for this specific agent. */
    val agentStatus: StateFlow<AgentMessage.AgentStatusUpdate?> =
        connectionViewModel.agentStatuses
            .map { it[agentId] }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    /**
     * Activity feed filtered for this agent:
     *   - AgentStatusUpdate: only this agent's entries
     *   - Everything else (clarifications, code proposals, responses): shown globally
     */
    val activityFeed: StateFlow<List<AgentMessage>> =
        connectionViewModel.activityFeed
            .map { messages ->
                messages.filter { msg ->
                    when (msg) {
                        is AgentMessage.AgentStatusUpdate -> msg.agentId == agentId
                        else -> true
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
}
