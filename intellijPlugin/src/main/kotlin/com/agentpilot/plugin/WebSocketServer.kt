package com.agentpilot.plugin

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val DISCOVERY_PORT = 27043
private const val WS_PORT = 27042

object WebSocketServer {

    private val sessions = ConcurrentHashMap<String, DefaultWebSocketSession>()
    private val eventCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicBoolean(false)

    /**
     * The last clarification request that hasn't been answered yet.
     * Sent to any phone that connects while an approval is pending so that a late-joining
     * phone can still approve/reject the tool call within the 60-second window.
     */
    @Volatile private var pendingClarification: String? = null

    /** Connection token shown in the tool window, e.g. "JB-482-XKQ". */
    val token: String = generateToken()

    val isRunning: Boolean get() = running.get()

    /**
     * Returns the machine's LAN IPv4 address, preferring 10.x/192.168.x ranges
     * (WiFi/Ethernet) over 172.x ranges (Docker bridges, etc.).
     */
    fun localIp(): String {
        val candidates = NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.filter { iface -> !iface.isLoopback && iface.isUp }
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.filter { !it.isLoopbackAddress }
            ?.map { it.hostAddress ?: "" }
            ?.filter { it.isNotEmpty() }
            ?.toList() ?: emptyList()

        return candidates.firstOrNull { it.startsWith("10.") || it.startsWith("192.168.") }
            ?: candidates.firstOrNull { it.startsWith("172.") }
            ?: candidates.firstOrNull()
            ?: "localhost"
    }

    private fun generateToken(): String {
        // Exclude I/O/0/1 to avoid visual ambiguity
        val safeLetters = ('A'..'Z').filter { it != 'I' && it != 'O' }
        val digits = (0..2).map { (0..9).random() }.joinToString("")
        val letters = (0..2).map { safeLetters.random() }.joinToString("")
        return "JB-$digits-$letters"
    }

    fun start() {
        if (running.getAndSet(true)) return
        server = embeddedServer(Netty, port = 27042) {
            install(WebSockets) {
                pingPeriod = kotlin.time.Duration.parse("PT15S")
            }
            install(ContentNegotiation) { json() }
            routing {
                webSocket("/") {
                    val id = java.util.UUID.randomUUID().toString()
                    sessions[id] = this
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            when {
                                text.contains("\"connection_handshake\"") -> {
                                    send(Frame.Text("""{"type":"connection_handshake","version":"1.0","capabilities":["clarification","code-review"]}"""))
                                    // Replay any pending clarification so a late-joining phone
                                    // can still respond within the 60-second approval window.
                                    pendingClarification?.let { send(Frame.Text(it)) }
                                }
                                text.contains("\"clarification_response\"") ->
                                    handleClarificationResponse(text)
                                text.contains("\"code_change_verdict\"") ->
                                    handleCodeChangeVerdict(text)
                            }
                        }
                    } finally {
                        sessions.remove(id)
                    }
                }
            }
        }
        scope.launch {
            try {
                server!!.start(wait = false)
            } catch (e: Exception) {
                running.set(false)
                System.err.println("[AgentPilot] WebSocket server failed to start: ${e.message}")
                e.printStackTrace()
            }
        }
        scope.launch { collectEvents() }
        scope.launch(Dispatchers.IO) { runUdpDiscovery() }
    }

    /**
     * Listens on UDP port 27043 for "DISCOVER:<token>" broadcasts.
     * When the token matches, replies with the WebSocket URL so the client
     * can establish a direct WebSocket connection.
     */
    private fun runUdpDiscovery() {
        try {
            DatagramSocket(DISCOVERY_PORT).use { socket ->
                socket.soTimeout = 1_000   // 1 s — lets us check isActive without blocking forever
                val buf = ByteArray(256)
                while (scope.isActive && running.get()) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val message = String(packet.data, 0, packet.length).trim()
                    if (message == "DISCOVER:$token") {
                        val reply = "ws://${localIp()}:$WS_PORT".toByteArray()
                        socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AgentPilot] UDP discovery error: ${e.message}")
        }
    }

    fun stop() {
        running.set(false)
        server?.stop(500, 1000)
        server = null
        sessions.clear()
        pendingClarification = null
    }

    fun broadcastRaw(json: String) {
        scope.launch {
            sessions.values.toList().forEach { session ->
                runCatching { session.send(Frame.Text(json)) }
            }
        }
    }

    fun broadcast(endpoint: String, content: JsonElement = JsonObject(emptyMap())) {
        val notification = buildJsonObject {
            put("type", "mcp-notification")
            putJsonObject("payload") {
                put("endpoint", endpoint)
                put("content", content)
                put("timestamp", Instant.now().toString())
            }
        }
        val text = Json.encodeToString(notification)
        scope.launch {
            sessions.values.toList().forEach { session ->
                runCatching { session.send(Frame.Text(text)) }
            }
        }
    }

    private suspend fun collectEvents() {
        AgentEventBus.events.collect { event ->
            when (event) {
                is AgentEvent.McpToolCall -> {
                    broadcast(event.endpoint, event.content)
                    broadcastAgentStatus("agent-pilot", "RUNNING", "Tool: ${event.endpoint}")
                }
                is AgentEvent.LlmRequest -> {
                    broadcast("llm/request", Json.encodeToJsonElement(event))
                    val fileName = event.filePath.substringAfterLast("/")
                    broadcastAgentStatus("agent-pilot", "RUNNING", "${event.actionId} @ $fileName:${event.caretLine}")
                }
                is AgentEvent.IdeSignal -> {
                    broadcast("ide/signal", Json.encodeToJsonElement(event))
                    broadcastAgentStatus("agent-pilot", event.type, event.detail)
                }
            }
        }
    }

    fun broadcastClarificationRequest(id: String, question: String, context: String) {
        val msg = buildJsonObject {
            put("type", "clarification_request")
            put("id", id)
            put("question", question)
            put("context", context)
        }
        val text = Json.encodeToString(msg)
        pendingClarification = text   // store so late-connecting phones can still respond
        scope.launch {
            sessions.values.toList().forEach { session ->
                runCatching { session.send(Frame.Text(text)) }
            }
        }
    }

    private fun handleClarificationResponse(text: String) {
        try {
            val obj = Json.parseToJsonElement(text).jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return
            val answer = obj["answer"]?.jsonPrimitive?.content ?: return
            pendingClarification = null   // clear once the phone has responded
            SidecarBridge.resolveApproval(id, answer == "approved")
        } catch (e: Exception) {
            System.err.println("[AgentPilot] Failed to parse clarification response: ${e.message}")
        }
    }

    private fun handleCodeChangeVerdict(text: String) {
        try {
            val obj = Json.parseToJsonElement(text).jsonObject
            val id     = obj["id"]?.jsonPrimitive?.content ?: return
            val action = obj["action"]?.jsonPrimitive?.content ?: return
            SidecarBridge.resolveVerdict(id, action == "ACCEPT")
        } catch (e: Exception) {
            System.err.println("[AgentPilot] Failed to parse code change verdict: ${e.message}")
        }
    }

    fun broadcastCodeChangeProposal(id: String, filePath: String, diff: String, explanation: String) {
        val msg = buildJsonObject {
            put("type", "code_change_proposal")
            put("id", id)
            put("filePath", filePath)
            put("diff", diff)
            put("explanation", explanation)
        }
        val text = Json.encodeToString(msg)
        scope.launch {
            sessions.values.toList().forEach { session ->
                runCatching { session.send(Frame.Text(text)) }
            }
        }
    }

    fun broadcastAgentStatus(agentId: String, status: String, currentTask: String) {
        val progress = (eventCounter.incrementAndGet() % 10) / 10f
        val msg = buildJsonObject {
            put("type", "agent_status_update")
            put("agentId", agentId)
            put("status", status)
            put("progress", progress)
            put("currentTask", currentTask)
        }
        val text = Json.encodeToString(msg)
        scope.launch {
            sessions.values.toList().forEach { session ->
                runCatching { session.send(Frame.Text(text)) }
            }
        }
    }
}
