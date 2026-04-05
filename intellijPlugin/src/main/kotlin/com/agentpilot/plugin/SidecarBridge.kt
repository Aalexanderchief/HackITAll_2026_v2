package com.agentpilot.plugin

import com.agentpilot.plugin.platform.NotificationService
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SidecarBridge {

    private var bridgeScope: CoroutineScope? = null
    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val pendingVerdicts   = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val notificationService = NotificationService()

    // Tools that write files — shown as CodeChangeProposal (diff) on mobile
    private val fileWriteTools = setOf(
        "create_new_file_with_text",
        "replace_file_text_by_path",
        "replace_specific_text",
        "replace_current_file_text",
        "reformat_file",
        "reformat_current_file"
    )

    private val acceptLabels = setOf("ok", "run", "yes", "accept", "allow", "execute", "confirm")
    private val rejectLabels = setOf("cancel", "no", "deny", "reject")

    // One entry per pending IntelliJ dialog, in FIFO order matching tool-call order.
    // Replaces the old single capturedOk/capturedCancel which got overwritten when
    // a second dialog appeared before the first verdict arrived.
    private data class DialogButtons(val ok: javax.swing.AbstractButton?, val cancel: javax.swing.AbstractButton?)
    private val dialogButtonQueue = ConcurrentLinkedQueue<DialogButtons>()

    // Count of in-flight approvals — gate for the AWT listener.
    private val pendingDialogCount = AtomicInteger(0)

    private val awtListener = java.awt.event.AWTEventListener { event ->
        if (pendingDialogCount.get() == 0) return@AWTEventListener
        // Only react to WINDOW_OPENED — fires exactly once per dialog, no duplicate captures.
        if (event !is java.awt.event.WindowEvent) return@AWTEventListener
        if (event.id != java.awt.event.WindowEvent.WINDOW_OPENED) return@AWTEventListener
        val window = event.window as? java.awt.Container ?: return@AWTEventListener
        var ok: javax.swing.AbstractButton? = null
        var cancel: javax.swing.AbstractButton? = null
        scanForButtons(window) { btn, label ->
            when {
                label in acceptLabels && ok == null -> ok = btn.also {
                    System.err.println("[AgentPilot] Queued OK button: '${btn.text}'")
                }
                label in rejectLabels && cancel == null -> cancel = btn.also {
                    System.err.println("[AgentPilot] Queued Cancel button: '${btn.text}'")
                }
            }
        }
        if (ok != null || cancel != null) {
            dialogButtonQueue.offer(DialogButtons(ok, cancel))
        }
    }

    private fun scanForButtons(container: java.awt.Container, onFound: (javax.swing.AbstractButton, String) -> Unit) {
        for (c in container.components) {
            if (c is javax.swing.AbstractButton) {
                val label = c.text?.trim()?.lowercase() ?: ""
                if (label.isNotEmpty()) onFound(c, label)
            }
            if (c is java.awt.Container) scanForButtons(c, onFound)
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

    fun stop() {
        java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(awtListener)
        pendingApprovals.values.forEach { it.cancel() }
        pendingApprovals.clear()
        pendingVerdicts.values.forEach { it.cancel() }
        pendingVerdicts.clear()
    }

    /** Called by WebSocketServer when a clarification_response arrives from the mobile app. */
    fun resolveApproval(requestId: String, approved: Boolean) {
        pendingApprovals.remove(requestId)?.complete(approved)
    }

    /** Called by WebSocketServer when a code_change_verdict arrives from mobile. */
    fun resolveVerdict(requestId: String, accepted: Boolean) {
        pendingVerdicts.remove(requestId)?.complete(accepted)
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
            val content  = payload["content"] ?: JsonObject(emptyMap())
            val toolName = endpoint.substringAfterLast("/")

            // Read-only tools — auto-approve, no mobile notification needed
            if (!requiresApproval(toolName)) {
                acceptCurrentDialog()
                WebSocketServer.broadcastAgentStatus("mcp-agent", "RUNNING", toolName)
                return
            }

            if (toolName in fileWriteTools) {
                handleFileWriteTool(scope, toolName, content)
            } else {
                handleCommandTool(scope, toolName, content)
            }
        } catch (e: Exception) {
            System.err.println("[AgentPilot] handleMessage error: ${e.message}")
        }
    }

    // File-write path: IntelliJ executes these immediately (no Brave Mode dialog).
    // We snapshot the content BEFORE the write, show the diff on mobile, and revert
    // on disk if the user rejects — without touching the IntelliJ dialog queue.
    private fun handleFileWriteTool(scope: CoroutineScope, toolName: String, content: JsonElement) {
        val obj = content as? JsonObject
        val pathInProject = obj?.get("pathInProject")?.jsonPrimitive?.content
            ?: obj?.get("path")?.jsonPrimitive?.content
            ?: "unknown"
        val newText = obj?.get("text")?.jsonPrimitive?.content
            ?: obj?.get("newFileText")?.jsonPrimitive?.content
            ?: obj?.get("newText")?.jsonPrimitive?.content
            ?: ""

        val fileName    = pathInProject.substringAfterLast("/")
        val before      = DiffGenerator.readCurrentContent(pathInProject)   // snapshot before write
        val diff        = DiffGenerator.generate(pathInProject, before, newText)
        val explanation = if (before.isEmpty()) "New file: $fileName" else "Modifying: $fileName"

        val requestId = UUID.randomUUID().toString()
        val deferred  = CompletableDeferred<Boolean>()
        pendingVerdicts[requestId] = deferred
        // Do NOT touch pendingDialogCount — there is no IntelliJ dialog for file writes.

        WebSocketServer.broadcastAgentStatus("mcp-agent", "WAITING_FOR_REVIEW", "Review: $fileName")
        WebSocketServer.broadcastCodeChangeProposal(
            id = requestId, filePath = pathInProject, diff = diff, explanation = explanation
        )

        scope.launch {
            val accepted = runCatching {
                withTimeout(120_000L) { deferred.await() }
            }.getOrElse {
                System.err.println("[AgentPilot] Verdict timed out for $requestId")
                true  // timeout → treat as accepted, don't silently revert
            }
            pendingVerdicts.remove(requestId)
            if (accepted) {
                WebSocketServer.broadcastAgentStatus("mcp-agent", "RUNNING", "Accepted: $fileName")
            } else {
                // Revert: write the original content (or delete the file if it was brand-new)
                revertFile(pathInProject, before)
                WebSocketServer.broadcastAgentStatus("mcp-agent", "FAILED", "Rejected: $fileName")
            }
        }
    }

    private fun revertFile(pathInProject: String, originalContent: String) {
        val basePath = com.intellij.openapi.project.ProjectManager.getInstance()
            .openProjects.firstOrNull()?.basePath ?: return
        val file = java.io.File(basePath, pathInProject)
        try {
            if (originalContent.isEmpty()) {
                // File didn't exist before — delete it
                file.delete()
                System.err.println("[AgentPilot] Reverted: deleted new file $pathInProject")
            } else {
                // File existed — restore original content
                file.writeText(originalContent)
                // Refresh IntelliJ's VFS so the editor reflects the revert
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .refreshAndFindFileByIoFile(file)?.refresh(false, false)
                }
                System.err.println("[AgentPilot] Reverted: restored original $pathInProject")
            }
        } catch (e: Exception) {
            System.err.println("[AgentPilot] Revert failed for $pathInProject: ${e.message}")
        }
    }

    // Command path: send ClarificationRequest to mobile (approve / reject)
    private fun handleCommandTool(scope: CoroutineScope, toolName: String, content: JsonElement) {
        val obj = content as? JsonObject
        val detail = obj?.get("command")?.jsonPrimitive?.content?.take(60)
            ?: obj?.get("pathInProject")?.jsonPrimitive?.content?.substringAfterLast("/")
            ?: toolName

        val requestId = UUID.randomUUID().toString()
        val deferred  = CompletableDeferred<Boolean>()
        pendingApprovals[requestId] = deferred
        pendingDialogCount.incrementAndGet()

        // Local desktop notification
        notificationService.notify("Approval Required", "Agent wants to run $toolName")
        notificationService.vibrate()

        WebSocketServer.broadcastAgentStatus("mcp-agent", "WAITING_FOR_INPUT", "$toolName: $detail")
        WebSocketServer.broadcastClarificationRequest(
            id = requestId, question = "Allow: $toolName", context = detail
        )

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
        pendingDialogCount.decrementAndGet()
        // Pop the oldest captured dialog — FIFO order matches tool-call order.
        val buttons = dialogButtonQueue.poll()
        val btn = if (accept) buttons?.ok else buttons?.cancel

        // ModalityState.any() lets this run even while the modal dialog is blocking the EDT
        ApplicationManager.getApplication().invokeLater({
            if (btn != null) {
                System.err.println("[AgentPilot] doClick '${btn.text}' accept=$accept")
                btn.doClick()
            } else {
                // Fallback: Robot key (only works if dialog has OS focus)
                System.err.println("[AgentPilot] No button in queue, sending ${if (accept) "Enter" else "Escape"}")
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
