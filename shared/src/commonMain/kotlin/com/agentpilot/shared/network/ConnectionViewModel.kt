package com.agentpilot.shared.network

import com.agentpilot.shared.models.AgentMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for connection state and incoming agent messages.
 *
 * All three connection paths (QR, code discovery, manual IP) converge here and
 * produce the same [connectionState] and [messages] flows for the UI to observe.
 *
 * Lifecycle: call [onCleared] from the host ViewModel's onCleared() or equivalent.
 */
class ConnectionViewModel {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val client = WebSocketClient()

    /** Reflects the WebSocket connection lifecycle. */
    val connectionState: StateFlow<ConnectionState> = client.state

    /** Stream of all [AgentMessage]s received from the IntelliJ plugin. */
    val messages: SharedFlow<AgentMessage> = client.messages

    /** Reflects the UDP discovery phase (only active during [connectViaCode]). */
    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    /**
     * Connects using a URL decoded from a QR code (e.g. "ws://192.168.1.5:27042").
     * No discovery step — connects immediately.
     */
    fun connectViaQr(url: String) {
        client.connect(url)
    }

    /**
     * Connects by broadcasting [code] (e.g. "A3F-92B") over UDP to discover the
     * IntelliJ plugin on the local network, then connects to the returned URL.
     */
    fun connectViaCode(code: String) {
        scope.launch {
            _discoveryState.value = DiscoveryState.Searching
            try {
                val url = discoverDevice(code)
                _discoveryState.value = DiscoveryState.Idle
                client.connect(url)
            } catch (e: TimeoutCancellationException) {
                _discoveryState.value = DiscoveryState.NotFound
            } catch (e: Exception) {
                _discoveryState.value = DiscoveryState.Error(e)
            }
        }
    }

    /**
     * Connects directly using a manually entered IP address.
     * Builds the WebSocket URL assuming the default port 27042.
     */
    fun connectViaIp(ip: String, port: Int = 27042) {
        client.connect("ws://$ip:$port")
    }

    /** Sends [message] to the connected plugin. No-op if not connected. */
    suspend fun send(message: AgentMessage) {
        client.send(message)
    }

    fun disconnect() {
        client.disconnect()
        _discoveryState.value = DiscoveryState.Idle
    }

    /** Must be called when the owning ViewModel or screen is destroyed. */
    fun onCleared() {
        client.close()   // cancels internal WS scope — do not reconnect after this
        scope.cancel()
    }
}
