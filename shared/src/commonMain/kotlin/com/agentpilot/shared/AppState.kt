package com.agentpilot.shared

import com.agentpilot.shared.network.ConnectionViewModel

/**
 * Process-wide singleton holding the shared [ConnectionViewModel].
 *
 * Accessed by [AgentListViewModel] on both Android and Desktop so neither
 * platform needs to pass it down the call stack. On Android this is
 * initialized in [AgentPilotApplication.onCreate] (to set AppContext first);
 * on Desktop it initialises lazily on first access.
 */
object AppState {
    val connectionViewModel = ConnectionViewModel()
}
