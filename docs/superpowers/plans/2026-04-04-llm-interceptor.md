# LLM Action Interceptor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Intercept AI Assistant action triggers in IntelliJ, capture editor context, and fan events out through an internal bus to the existing WebSocket server on port 27042.

**Architecture:** A new `AgentEventBus` singleton holds a `SharedFlow<AgentEvent>`. `LlmActionInterceptor` listens to all IDE actions, filters for AI-related ones, and emits `AgentEvent.LlmRequest` into the bus. `WebSocketServer` collects from the bus and calls `broadcast()`. All existing `broadcast()` call sites are migrated to emit into the bus instead.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`AnActionListener`, `Editor`, `VirtualFile`), kotlinx.coroutines `SharedFlow`, kotlinx.serialization

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentEvent.kt` | Create | Sealed class hierarchy for all event types |
| `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentEventBus.kt` | Create | Singleton SharedFlow — decouples emitters from WebSocket |
| `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/LlmActionInterceptor.kt` | Create | AnActionListener filtering AI actions, emitting LlmRequest |
| `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/WebSocketServer.kt` | Modify | Add `collectEvents()` coroutine started from `start()` |
| `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotToolWindow.kt` | Modify | Test button emits into AgentEventBus instead of direct broadcast |
| `intellijPlugin/src/main/resources/META-INF/plugin.xml` | Modify | Register LlmActionInterceptor as AnActionListener |

---

## Task 1: AgentEvent sealed class

**Files:**
- Create: `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentEvent.kt`

- [ ] **Step 1: Create AgentEvent.kt**

```kotlin
package com.agentpilot.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class AgentEvent {

    @Serializable
    data class McpToolCall(
        val endpoint: String,
        val content: JsonElement = JsonObject(emptyMap())
    ) : AgentEvent()

    @Serializable
    data class LlmRequest(
        val actionId: String,
        val filePath: String,
        val selectedText: String?,
        val caretLine: Int,
        val language: String?
    ) : AgentEvent()

    @Serializable
    data class IdeSignal(
        val type: String,
        val detail: String
    ) : AgentEvent()
}
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentEvent.kt
git commit -m "feat: add AgentEvent sealed class hierarchy"
```

---

## Task 2: AgentEventBus singleton

**Files:**
- Create: `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentEventBus.kt`

- [ ] **Step 1: Create AgentEventBus.kt**

```kotlin
package com.agentpilot.plugin

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AgentEventBus {

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    /**
     * Non-suspending emit — safe to call from the EDT or any thread.
     * Returns false only if the buffer is full (64 events), which never
     * happens in normal IDE usage.
     */
    fun emit(event: AgentEvent): Boolean = _events.tryEmit(event)
}
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentEventBus.kt
git commit -m "feat: add AgentEventBus SharedFlow singleton"
```

---

## Task 3: LlmActionInterceptor

**Files:**
- Create: `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/LlmActionInterceptor.kt`

- [ ] **Step 1: Create LlmActionInterceptor.kt**

```kotlin
package com.agentpilot.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener

class LlmActionInterceptor : AnActionListener {

    // Action ID prefixes/exact matches that indicate an AI request
    private val aiActionPrefixes = listOf(
        "AiAssistantAction",
        "InlineCompletionAction",
        "com.intellij.ml.llm",
        "junie.",
        "copilot.",
        "com.github.copilot"
    )

    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        val actionId = event.actionManager?.getId(action) ?: return
        if (aiActionPrefixes.none { actionId.startsWith(it) }) return

        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val psiFile = event.getData(CommonDataKeys.PSI_FILE)

        val selectedText = editor.selectionModel
            .takeIf { it.hasSelection() }
            ?.selectedText

        val caretLine = editor.caretModel.logicalPosition.line

        AgentEventBus.emit(
            AgentEvent.LlmRequest(
                actionId = actionId,
                filePath = virtualFile?.path ?: "unknown",
                selectedText = selectedText,
                caretLine = caretLine,
                language = psiFile?.language?.id
            )
        )
    }
}
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add intellijPlugin/src/main/kotlin/com/agentpilot/plugin/LlmActionInterceptor.kt
git commit -m "feat: add LlmActionInterceptor for AI action capture"
```

---

## Task 4: Wire WebSocketServer to AgentEventBus

**Files:**
- Modify: `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/WebSocketServer.kt`

- [ ] **Step 1: Add collectEvents() and wire it into start()**

Replace the entire file content with:

```kotlin
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
                is AgentEvent.McpToolCall -> broadcast(event.endpoint, event.content)
                is AgentEvent.LlmRequest  -> broadcast("llm/request",  Json.encodeToJsonElement(AgentEvent.LlmRequest.serializer(), event))
                is AgentEvent.IdeSignal   -> broadcast("ide/signal",   Json.encodeToJsonElement(AgentEvent.IdeSignal.serializer(),  event))
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add intellijPlugin/src/main/kotlin/com/agentpilot/plugin/WebSocketServer.kt
git commit -m "feat: WebSocketServer collects from AgentEventBus"
```

---

## Task 5: Register listener in plugin.xml

**Files:**
- Modify: `intellijPlugin/src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Add LlmActionInterceptor listener**

Replace the file content with:

```xml
<idea-plugin>
    <id>com.agentpilot.plugin</id>
    <name>AgentPilot</name>
    <version>0.1.0</version>
    <description>Mobile command center for AI coding agents</description>

    <depends>com.intellij.modules.platform</depends>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow
            id="AgentPilot"
            anchor="right"
            factoryClass="com.agentpilot.plugin.AgentPilotToolWindow"
            icon="/icons/agentpilot.svg" />
    </extensions>

    <applicationListeners>
        <listener
            class="com.agentpilot.plugin.AgentPilotPlugin"
            topic="com.intellij.openapi.project.ProjectManagerListener" />
        <listener
            class="com.agentpilot.plugin.LlmActionInterceptor"
            topic="com.intellij.openapi.actionSystem.ex.AnActionListener" />
    </applicationListeners>
</idea-plugin>
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add intellijPlugin/src/main/resources/META-INF/plugin.xml
git commit -m "feat: register LlmActionInterceptor in plugin.xml"
```

---

## Task 6: Migrate ToolWindow test button to AgentEventBus

**Files:**
- Modify: `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotToolWindow.kt`

- [x] **Step 1: Update test button to emit into AgentEventBus**

Change lines 61–65 in `AgentPilotToolWindow.kt` from:

```kotlin
val testButton = JButton("Send test event").apply {
    addActionListener {
        WebSocketServer.broadcast("test/ping", JsonObject(emptyMap()))
        appendLog("test/ping → broadcast to ${if (WebSocketServer.isRunning) "running" else "stopped"}")
    }
}
```

To:

```kotlin
val testButton = JButton("Send test event").apply {
    addActionListener {
        AgentEventBus.emit(AgentEvent.McpToolCall("test/ping"))
        appendLog("test/ping → emitted to bus (server: ${if (WebSocketServer.isRunning) "running" else "stopped"})")
    }
}
```

Also remove the now-unused import at line 11:
```kotlin
import kotlinx.serialization.json.JsonObject
```

- [x] **Step 2: Compile**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: Build plugin**

```powershell
.\gradlew :intellijPlugin:buildPlugin
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: Commit and push**

```bash
git add intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotToolWindow.kt
git commit -m "feat: LLM interceptor complete — event bus wired end to end"
git push
```
