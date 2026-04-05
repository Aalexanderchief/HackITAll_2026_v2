package com.agentpilot.plugin

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.awt.Robot
import java.awt.event.KeyEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SidecarBridge {

    private var bridgeScope: CoroutineScope? = null
    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    // Captured button refs — populated by AWT listener the moment the dialog appears
    @Volatile private var capturedOk: javax.swing.AbstractButton? = null
    @Volatile private var capturedCancel: javax.swing.AbstractButton? = null
    @Volatile private var awaitingApproval = false

    private val acceptLabels = setOf("ok", "run", "yes", "accept", "allow", "execute", "confirm")
    private val rejectLabels = setOf("cancel", "no", "deny", "reject")

    private val awtListener = java.awt.event.AWTEventListener { event ->
        if (!awaitingApproval) return@AWTEventListener
        val container: java.awt.Container? = when {
            event is java.awt.event.WindowEvent &&
            event.id == java.awt.event.WindowEvent.WINDOW_OPENED -> event.window as? java.awt.Container
            event is java.awt.event.ContainerEvent &&
            event.id == java.awt.event.ContainerEvent.COMPONENT_ADDED -> event.child as? java.awt.Container
            else -> null
        }
        container?.let { scanForButtons(it) }
    }

    private fun scanForButtons(container: java.awt.Container) {
        for (c in container.components) {
            if (c is javax.swing.AbstractButton) {
                val text = c.text?.trim()?.lowercase() ?: ""
                when {
                    text in acceptLabels -> {
                        System.err.println("[AgentPilot] Captured OK button: '${c.text}'")
                        capturedOk = c
                    }
                    text in rejectLabels -> {
                        System.err.println("[AgentPilot] Captured Cancel button: '${c.text}'")
                        capturedCancel = c
                    }
                }
            }
            if (c is java.awt.Container) scanForButtons(c)
        }
    }

    fun start(scope: CoroutineScope, port: Int = 27044) {
        bridgeScope = scope
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
            awtListener,
            java.awt.AWTEvent.WINDOW_EVENT_MASK or java.awt.AWTEvent.CONTAINER_EVENT_MASK
        )
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

    /** Called by WebSocketServer when a clarification_response arrives from the mobile app. */
    fun resolveApproval(requestId: String, approved: Boolean) {
        pendingApprovals.remove(requestId)?.complete(approved)
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

        cont.invokeOnCancellation { wsFuture.thenAccept { ws -> ws.abort() } }
    }

    private fun handleMessage(text: String) {
        val scope = bridgeScope ?: return
        try {
            val obj = Json.parseToJsonElement(text).jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "mcp-notification") return

            val payload = obj["payload"]?.jsonObject ?: return
            val endpoint = payload["endpoint"]?.jsonPrimitive?.content ?: "unknown"
            val content = payload["content"] ?: JsonObject(emptyMap())

            val toolName = endpoint.substringAfterLast("/")
            val detail = when {
                content is JsonObject && content["pathInProject"] != null ->
                    content["pathInProject"]!!.jsonPrimitive.content.substringAfterLast("/")
                content is JsonObject && content["command"] != null ->
                    content["command"]!!.jsonPrimitive.content.take(60)
                content is JsonObject && content["path"] != null ->
                    content["path"]!!.jsonPrimitive.content.substringAfterLast("/")
                else -> endpoint
            }

            // Read-only tools are auto-approved — only destructive tools go to the phone
            if (!requiresApproval(toolName)) {
                acceptCurrentDialog()
                WebSocketServer.broadcastAgentStatus("mcp-agent", "RUNNING", "$toolName: $detail")
                return
            }

            val requestId = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<Boolean>()
            pendingApprovals[requestId] = deferred
            awaitingApproval = true  // AWT listener now captures the approval dialog buttons

            // Notify mobile — status WAITING_FOR_INPUT + clarification dialog
            WebSocketServer.broadcastAgentStatus("mcp-agent", "WAITING_FOR_INPUT", "$toolName: $detail")
            WebSocketServer.broadcastClarificationRequest(
                id = requestId,
                question = "Allow: $toolName",
                context = detail
            )

            // Wait for mobile response; auto-reject after 60s
            scope.launch {
                val approved = runCatching {
                    withTimeout(60_000L) { deferred.await() }
                }.getOrElse {
                    System.err.println("[AgentPilot] Approval timed out for $requestId")
                    false
                }
                pendingApprovals.remove(requestId)

                if (approved) {
                    acceptCurrentDialog()
                    WebSocketServer.broadcastAgentStatus("mcp-agent", "RUNNING", "Approved: $toolName")
                } else {
                    rejectCurrentDialog()
                    WebSocketServer.broadcastAgentStatus("mcp-agent", "FAILED", "Rejected: $toolName")
                }
            }
        } catch (e: Exception) {
            System.err.println("[AgentPilot] Failed to handle sidecar message: ${e.message}")
        }
    }

    private fun requiresApproval(toolName: String): Boolean {
        val safeTools = setOf(
            "get_file_text_by_path",
            "get_open_in_editor_file_text",
            "get_open_in_editor_file_path",
            "get_all_open_file_paths",
            "get_all_open_file_texts",
            "list_files_in_folder",
            "list_directory_tree_in_folder",
            "find_files_by_name_substring",
            "search_in_files_content",
            "get_project_modules",
            "get_project_dependencies",
            "get_project_problems",
            "get_project_vcs_status",
            "get_run_configurations",
            "get_debugger_breakpoints",
            "get_current_file_errors",
            "get_progress_indicators",
            "find_commit_by_message",
            "get_terminal_text",
            "get_selected_in_editor_text"
        )
        return toolName !in safeTools
    }

    private fun acceptCurrentDialog() = clickDialog(accept = true)
    private fun rejectCurrentDialog() = clickDialog(accept = false)

    private fun clickDialog(accept: Boolean) {
        awaitingApproval = false
        val btn = if (accept) capturedOk else capturedCancel
        capturedOk = null
        capturedCancel = null

        // ModalityState.any() lets this run even while the modal dialog is blocking the EDT
        ApplicationManager.getApplication().invokeLater({
            if (btn != null) {
                System.err.println("[AgentPilot] doClick '${btn.text}' accept=$accept")
                btn.doClick()
            } else {
                // Fallback: Robot key (only works if dialog has OS focus)
                System.err.println("[AgentPilot] No button captured, sending ${if (accept) "Enter" else "Escape"}")
                runCatching {
                    val robot = Robot()
                    robot.delay(100)
                    val key = if (accept) KeyEvent.VK_ENTER else KeyEvent.VK_ESCAPE
                    robot.keyPress(key)
                    robot.keyRelease(key)
                }.onFailure { System.err.println("[AgentPilot] Robot fallback failed: ${it.message}") }
            }
        }, com.intellij.openapi.application.ModalityState.any())
    }
}
