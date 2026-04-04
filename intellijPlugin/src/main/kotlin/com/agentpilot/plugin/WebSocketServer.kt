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
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get()

    fun start() {
        if (running.getAndSet(true)) return
        server = embeddedServer(Netty, port = 27042) {
            install(WebSockets)
            install(ContentNegotiation) { json() }
            routing {
                webSocket("/") {
                    val id = java.util.UUID.randomUUID().toString()
                    sessions[id] = this
                    try {
                        for (frame in incoming) { /* phone→IDE messages handled in future */ }
                    } finally {
                        sessions.remove(id)
                    }
                }
            }
        }
        scope.launch { server!!.start(wait = false) }
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
}
