package com.agentpilot.plugin

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.coroutines.resumeWithException

object SidecarBridge {

    fun start(scope: CoroutineScope, port: Int = 27044) {
        scope.launch(Dispatchers.IO) {
            var backoffMs = 2_000L
            while (isActive) {
                try {
                    System.err.println("[AgentPilot] Connecting to sidecar on port $port...")
                    connectAndReceive(port)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    System.err.println("[AgentPilot] Sidecar bridge lost: ${e.message}, retry in ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    private suspend fun connectAndReceive(port: Int) = suspendCancellableCoroutine<Unit> { cont ->
        val client = HttpClient.newHttpClient()

        val listener = object : WebSocket.Listener {
            private val buffer = StringBuilder()

            override fun onOpen(webSocket: WebSocket) {
                System.err.println("[AgentPilot] Sidecar bridge connected on :$port")
                webSocket.request(1)
            }

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                buffer.append(data)
                if (last) {
                    handleMessage(buffer.toString())
                    buffer.clear()
                }
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                if (cont.isActive) cont.resumeWithException(Exception(error.message ?: "WebSocket error"))
            }

            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
                if (cont.isActive) cont.resumeWithException(Exception("Sidecar closed: $statusCode $reason"))
                return CompletableFuture.completedFuture(null)
            }
        }

        val wsFuture = client.newWebSocketBuilder()
            .buildAsync(URI.create("ws://127.0.0.1:$port"), listener)

        wsFuture.exceptionally { error ->
            if (cont.isActive) cont.resumeWithException(error)
            null
        }

        cont.invokeOnCancellation {
            wsFuture.thenAccept { ws -> ws.abort() }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val obj = Json.parseToJsonElement(text).jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "mcp-notification") return

            val payload = obj["payload"]?.jsonObject ?: return
            val endpoint = payload["endpoint"]?.jsonPrimitive?.content ?: "unknown"
            val content = payload["content"] ?: JsonObject(emptyMap())

            // Forward raw notification to all mobile clients
            WebSocketServer.broadcast(endpoint, content)

            // Also send an agent status card so the list screen shows something
            val taskLabel = endpoint.substringAfterLast("/")
            val detail = when {
                content is JsonObject && content["path"] != null ->
                    "$taskLabel: ${content["path"]!!.jsonPrimitive.content.substringAfterLast("/")}"
                content is JsonObject && content["command"] != null ->
                    "$taskLabel: ${content["command"]!!.jsonPrimitive.content.take(40)}"
                else -> "MCP: $endpoint"
            }
            WebSocketServer.broadcastAgentStatus("mcp-agent", "RUNNING", detail)
        } catch (e: Exception) {
            System.err.println("[AgentPilot] Failed to handle sidecar message: ${e.message}")
        }
    }
}
