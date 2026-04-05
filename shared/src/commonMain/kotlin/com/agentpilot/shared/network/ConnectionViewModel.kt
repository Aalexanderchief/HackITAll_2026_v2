package com.agentpilot.shared.network

import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus
import com.agentpilot.shared.platform.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ACTIVITY_FEED_LIMIT = 50

/**
 * Single source of truth for all networking state and incoming agent data.
 *
 * Consumes the raw WebSocket stream internally and exposes typed, stateful flows that
 * composables can collect directly via collectAsState() — no routing or manual state
 * management required on the UI side.
 *
 * Lifecycle: call [onCleared] from the host ViewModel's onCleared() or equivalent.
 * Must be held at a scope that survives recomposition (Application singleton or Android ViewModel wrapper).
 */
@OptIn(FlowPreview::class)
class ConnectionViewModel {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val client = WebSocketClient()
    private val notificationService = NotificationService()

    // --- Connection state ---

    /** WebSocket connection lifecycle. Drives the connection status indicator in the UI. */
    val connectionState: StateFlow<ConnectionState> = client.state

    /**
     * UDP discovery phase state. Only active while [connectViaCode] is running.
     * Returns to [DiscoveryState.Idle] once a URL is found or on error.
     */
    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    // --- Derived UI state ---

    /**
     * Latest known status for each agent, keyed by agentId.
     * Updated whenever an [AgentMessage.AgentStatusUpdate] arrives.
     * Suitable for driving an agent list screen — collect as state and render the map values.
     * 
     * Throttled to 200ms to prevent UI jank during rapid progress updates.
     */
    private val _agentStatuses = MutableStateFlow<Map<String, AgentMessage.AgentStatusUpdate>>(emptyMap())
    val agentStatuses: StateFlow<Map<String, AgentMessage.AgentStatusUpdate>> = _agentStatuses
        .sample(200)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * The current pending clarification request from the agent, or null if none.
     * Set when a [AgentMessage.ClarificationRequest] arrives.
     * Cleared automatically when [send] is called with a [AgentMessage.ClarificationResponse].
     * Drives the clarification dialog: show the dialog when non-null, dismiss when null.
     */
    private val _activeClarification = MutableStateFlow<AgentMessage.ClarificationRequest?>(null)
    val activeClarification: StateFlow<AgentMessage.ClarificationRequest?> = _activeClarification.asStateFlow()

    /**
     * The current pending code change proposal, or null if none.
     * Set when a [AgentMessage.CodeChangeProposal] arrives.
     * Cleared automatically when [send] is called with a [AgentMessage.CodeChangeVerdict].
     * Drives the code review bottom sheet: show when non-null, dismiss when null.
     */
    private val _activeCodeReview = MutableStateFlow<AgentMessage.CodeChangeProposal?>(null)
    val activeCodeReview: StateFlow<AgentMessage.CodeChangeProposal?> = _activeCodeReview.asStateFlow()

    /**
     * Chronological feed of the last [ACTIVITY_FEED_LIMIT] messages received.
     * Suitable for the main agent detail screen. New messages are appended at the end.
     * 
     * Excludes LlmResponseChunks to avoid overwhelming the list with streaming tokens.
     */
    private val _activityFeed = MutableStateFlow<List<AgentMessage>>(emptyList())
    val activityFeed: StateFlow<List<AgentMessage>> = _activityFeed.asStateFlow()

    init {
        scope.launch {
            client.messages.collect { message ->
                // Process for state flows
                when (message) {
                    is AgentMessage.AgentStatusUpdate -> {
                        val previousStatus = _agentStatuses.value[message.agentId]?.status
                        _agentStatuses.update { current ->
                            val next = current.toMutableMap()
                            next[message.agentId] = message
                            next
                        }
                        
                        // Only notify on failure if we were actually running/waiting (not just initial sync or manual rejection)
                        if (message.status == AgentStatus.FAILED && 
                            previousStatus != null && 
                            previousStatus != AgentStatus.IDLE && 
                            previousStatus != AgentStatus.FAILED) {
                            
                            notificationService.notify(
                                title = "Agent Failed",
                                message = "Agent ${message.agentId} encountered an error: ${message.currentTask}"
                            )
                            notificationService.vibrate()
                        }
                    }

                    is AgentMessage.ClarificationRequest -> {
                        _activeClarification.value = message
                        notificationService.notify(
                            title = "Input Needed",
                            message = message.question
                        )
                        notificationService.vibrate()
                    }

                    is AgentMessage.CodeChangeProposal -> {
                        _activeCodeReview.value = message
                        notificationService.notify(
                            title = "Review Pending",
                            message = "New code change proposed for ${message.filePath}"
                        )
                        notificationService.vibrate()
                    }

                    else -> Unit
                }
                
                // Add to activity feed only if it's not a streaming token chunk
                if (message !is AgentMessage.LlmResponseChunk) {
                    _activityFeed.update { current ->
                        (current + message).takeLast(ACTIVITY_FEED_LIMIT)
                    }
                }
            }
        }
    }

    // --- Connection entry points ---

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
     * Drives [discoveryState] through Searching → Idle (success) or NotFound / Error.
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
     * Connects directly to a manually entered IP address on the default port 27042.
     */
    fun connectViaIp(ip: String, port: Int = 27042) {
        client.connect("ws://$ip:$port")
    }

    /**
     * Sends [message] to the plugin. No-op if not connected.
     *
     * Automatically clears [activeClarification] when sending a [AgentMessage.ClarificationResponse]
     * and [activeCodeReview] when sending a [AgentMessage.CodeChangeVerdict].
     */
    suspend fun send(message: AgentMessage) {
        client.send(message)
        when (message) {
            is AgentMessage.ClarificationResponse -> _activeClarification.value = null
            is AgentMessage.CodeChangeVerdict -> _activeCodeReview.value = null
            else -> Unit
        }
    }

    fun disconnect() {
        client.disconnect()
        _discoveryState.value = DiscoveryState.Idle
    }

    /** Must be called when the owning ViewModel or screen is destroyed. */
    fun onCleared() {
        client.close()
        scope.cancel()
    }
}
