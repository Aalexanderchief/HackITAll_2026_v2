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
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object WebSocketServer {

    private val sessions = ConcurrentHashMap<String, DefaultWebSocketSession>()
    private val eventCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get()

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
                                text.contains("\"connection_handshake\"") ->
                                    send(Frame.Text("""{"type":"connection_handshake","version":"1.0","capabilities":["clarification","code-review"]}"""))
                                text.contains("\"clarification_response\"") ->
                                    handleClarificationResponse(text)
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
    }

    fun stop() {
        running.set(false)
        server?.stop(500, 1000)
        server = null
        sessions.clear()
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
            SidecarBridge.resolveApproval(id, answer == "approved")
        } catch (e: Exception) {
            System.err.println("[AgentPilot] Failed to parse clarification response: ${e.message}")
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
