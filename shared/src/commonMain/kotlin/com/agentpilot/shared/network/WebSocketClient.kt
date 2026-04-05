package com.agentpilot.shared.network

import com.agentpilot.shared.models.AgentMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WebSocketClient {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // HttpClient picks the engine from the classpath (OkHttp on Android via androidMain dep)
    private val httpClient = HttpClient {
        install(WebSockets)
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + supervisorJob)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<AgentMessage>()
    val messages: SharedFlow<AgentMessage> = _messages.asSharedFlow()

    private var activeSession: DefaultWebSocketSession? = null
    private var connectJob: Job? = null

    /**
     * Connects to [url] (e.g. "ws://192.168.1.5:27042") and keeps the connection alive
     * with exponential-backoff reconnection on unexpected drops.
     * Safe to call again with a new URL — cancels any existing session first.
     */
    fun connect(url: String) {
        connectJob?.cancel()
        connectJob = scope.launch {
            var backoffMs = 1_000L
            while (true) {
                try {
                    println("[AgentPilot] Connecting to $url ...")
                    _state.value = ConnectionState.Connecting
                    httpClient.webSocket(url) {
                        activeSession = this
                        performHandshake()
                        backoffMs = 1_000L  // reset on successful connect
                        receiveLoop()
                    }
                } catch (e: CancellationException) {
                    // disconnect() was called — exit the retry loop
                    break
                } catch (e: Exception) {
                    activeSession = null
                    println("[AgentPilot] WebSocket connection failed: ${e::class.simpleName}: ${e.message}")
                    _state.value = ConnectionState.Failed(e)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    /**
     * Sends [message] to the connected peer. No-op if not connected.
     */
    suspend fun send(message: AgentMessage) {
        activeSession?.send(Frame.Text(json.encodeToString(message)))
    }

    /**
     * Closes the WebSocket and stops reconnection attempts.
     * The client can be reconnected afterwards via [connect].
     */
    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        scope.launch {
            activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            activeSession = null
        }
        _state.value = ConnectionState.Disconnected
    }

    /**
     * Permanently shuts down the client, cancelling its internal coroutine scope.
     * Call this from the owning component's teardown (e.g. ViewModel.onCleared).
     * Do not call [connect] after this.
     */
    fun close() {
        connectJob?.cancel()
        supervisorJob.cancel()
        _state.value = ConnectionState.Disconnected
    }

    // --- private ---

    private suspend fun DefaultWebSocketSession.performHandshake() {
        val handshake = AgentMessage.ConnectionHandshake(
            version = "1.0",
            capabilities = listOf("clarification", "code-review")
        )
        println("[AgentPilot] Sending handshake...")
        send(Frame.Text(json.encodeToString<AgentMessage>(handshake)))

        // Wait for the server's echo
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val text = frame.readText()
            println("[AgentPilot] Received frame: $text")
            val msg = runCatching { json.decodeFromString<AgentMessage>(text) }.getOrElse {
                println("[AgentPilot] Failed to decode frame: ${it.message}")
                null
            }
            if (msg is AgentMessage.ConnectionHandshake) {
                println("[AgentPilot] Handshake complete, version=${msg.version}")
                _state.value = ConnectionState.Connected(msg.version)
                return
            }
        }
        println("[AgentPilot] Handshake loop ended without success")
    }

    private suspend fun DefaultWebSocketSession.receiveLoop() {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            runCatching { json.decodeFromString<AgentMessage>(frame.readText()) }
                .onSuccess { _messages.emit(it) }
        }
        // Incoming channel closed = server dropped the connection; let the retry loop handle it
        activeSession = null
    }
}
