package com.agentpilot.shared.network

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val peerVersion: String) : ConnectionState()
    data class Failed(val cause: Throwable) : ConnectionState()
}
