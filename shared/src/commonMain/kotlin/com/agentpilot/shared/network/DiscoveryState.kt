package com.agentpilot.shared.network

sealed class DiscoveryState {
    object Idle : DiscoveryState()
    object Searching : DiscoveryState()
    object NotFound : DiscoveryState()
    data class Error(val cause: Throwable) : DiscoveryState()
}
