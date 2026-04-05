# IntelliJ Plugin Skeleton — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working IntelliJ plugin with an embedded Ktor WebSocket server on port 27042 and a tool window showing connection status, QR code, and live event log.

**Architecture:** A standalone `intellijPlugin/` Gradle module using the IntelliJ Platform Gradle Plugin. A `ProjectManagerListener` starts/stops the Ktor server automatically. The tool window is a Swing panel registered via `plugin.xml`.

**Tech Stack:** Kotlin, IntelliJ Platform Gradle Plugin 2.x, Ktor 3.4.0 (Netty + WebSockets), ZXing (QR), kotlinx.serialization

---

## File Map

| File | Responsibility |
|------|---------------|
| `intellijPlugin/build.gradle.kts` | Module config, IntelliJ Platform + Ktor + ZXing deps |
| `settings.gradle.kts` | Add `:intellijPlugin` to includes |
| `gradle/libs.versions.toml` | Add server-side Ktor + ZXing versions |
| `intellijPlugin/src/main/resources/META-INF/plugin.xml` | Plugin descriptor, tool window + listener registration |
| `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/WebSocketServer.kt` | Ktor server, session map, broadcast API |
| `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotPlugin.kt` | ProjectManagerListener, server lifecycle |
| `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotToolWindow.kt` | Swing UI: status dot, QR code, event log, test button |

---

## Task 1: Register the module and add dependencies

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `intellijPlugin/build.gradle.kts`

- [ ] **Step 1: Add `:intellijPlugin` to settings.gradle.kts**

```kotlin
// settings.gradle.kts — replace the include line
include(":shared", ":androidApp", ":intellijPlugin")
```

- [ ] **Step 2: Add server-side Ktor and ZXing to libs.versions.toml**

Add under `[versions]`:
```toml
zxing = "3.5.3"
intellij-platform = "2.3.0"
```

Add under `[libraries]`:
```toml
# Ktor server (plugin only)
ktor-server-netty        = { module = "io.ktor:ktor-server-netty",                   version.ref = "ktor" }
ktor-server-websockets   = { module = "io.ktor:ktor-server-websockets",              version.ref = "ktor" }
ktor-server-content-neg  = { module = "io.ktor:ktor-server-content-negotiation",     version.ref = "ktor" }

# QR code
zxing-core   = { module = "com.google.zxing:core",     version.ref = "zxing" }
zxing-javase = { module = "com.google.zxing:javase",   version.ref = "zxing" }
```

Add under `[plugins]`:
```toml
intellij-platform = { id = "org.jetbrains.intellij.platform", version.ref = "intellij-platform" }
```

- [ ] **Step 3: Create intellijPlugin/build.gradle.kts**

```kotlin
plugins {
    kotlin("jvm") version "2.3.0"
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
}

kotlin {
    jvmToolchain(21)  // IntelliJ Platform requires JVM 21
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")
        bundledPlugin("com.intellij.java")
        instrumentationTools()
    }

    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.neg)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.agentpilot.plugin"
        name = "AgentPilot"
        version = "0.1.0"
    }
    buildSearchableOptions = false  // speeds up build significantly
}
```

- [ ] **Step 4: Verify Gradle syncs without errors**

```powershell
.\gradlew :intellijPlugin:dependencies
```

Expected: dependency tree prints, no resolution errors.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml intellijPlugin/build.gradle.kts
git commit -m "feat: add intellijPlugin module with Ktor and ZXing deps"
```

---

## Task 2: plugin.xml descriptor

**Files:**
- Create: `intellijPlugin/src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create the directory structure**

```bash
mkdir -p intellijPlugin/src/main/resources/META-INF
```

- [ ] **Step 2: Create plugin.xml**

```xml
<!-- intellijPlugin/src/main/resources/META-INF/plugin.xml -->
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
    </applicationListeners>
</idea-plugin>
```

- [ ] **Step 3: Create a placeholder icon so the plugin loads**

Create `intellijPlugin/src/main/resources/icons/agentpilot.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">
  <circle cx="8" cy="8" r="7" fill="#6B57FF"/>
</svg>
```

- [ ] **Step 4: Commit**

```bash
git add intellijPlugin/src/main/resources/
git commit -m "feat: add plugin.xml descriptor and icon"
```

---

## Task 3: WebSocketServer

**Files:**
- Create: `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/WebSocketServer.kt`

- [ ] **Step 1: Create WebSocketServer.kt**

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
import kotlinx.serialization.Serializable
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
```

- [ ] **Step 2: Verify it compiles**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add intellijPlugin/src/main/kotlin/com/agentpilot/plugin/WebSocketServer.kt
git commit -m "feat: add Ktor WebSocket server with broadcast API on port 27042"
```

---

## Task 4: AgentPilotPlugin — lifecycle listener

**Files:**
- Create: `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotPlugin.kt`

- [ ] **Step 1: Create AgentPilotPlugin.kt**

```kotlin
package com.agentpilot.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class AgentPilotPlugin : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        WebSocketServer.start()
    }

    override fun projectClosing(project: Project) {
        WebSocketServer.stop()
    }
}
```

- [ ] **Step 2: Verify it compiles**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotPlugin.kt
git commit -m "feat: start/stop WebSocket server on project open/close"
```

---

## Task 5: AgentPilotToolWindow — UI

**Files:**
- Create: `intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotToolWindow.kt`

- [ ] **Step 1: Create AgentPilotToolWindow.kt**

```kotlin
package com.agentpilot.plugin

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.serialization.json.JsonObject
import java.awt.*
import java.awt.image.BufferedImage
import java.net.Inet4Address
import java.net.NetworkInterface
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.*

class AgentPilotToolWindow : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = buildPanel()
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun buildPanel(): JPanel {
        val wsUrl = "ws://${localIp()}:27042"

        // Status dot
        val statusDot = JLabel("●").apply {
            foreground = if (WebSocketServer.isRunning) Color(0x4CAF50) else Color(0xF44336)
            font = font.deriveFont(20f)
        }
        val urlLabel = JLabel(wsUrl).apply {
            font = font.deriveFont(12f)
        }

        // QR code
        val qrImage = generateQr(wsUrl, 180)
        val qrLabel = JLabel(ImageIcon(qrImage))

        // Top panel
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
            add(statusDot)
            add(urlLabel)
        }

        // Event log
        val logArea = JTextArea(12, 40).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        }
        val logScroll = JScrollPane(logArea)

        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        fun appendLog(text: String) {
            SwingUtilities.invokeLater {
                logArea.append("[${LocalTime.now().format(formatter)}] $text\n")
                logArea.caretPosition = logArea.document.length
            }
        }

        // Test button
        val testButton = JButton("Send test event").apply {
            addActionListener {
                WebSocketServer.broadcast("test/ping", JsonObject(emptyMap()))
                appendLog("test/ping → broadcast to ${WebSocketServer.isRunning.let { if (it) "running" else "stopped" }}")
            }
        }

        val bottomPanel = JPanel(BorderLayout(4, 4)).apply {
            add(logScroll, BorderLayout.CENTER)
            add(testButton, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(topPanel, BorderLayout.NORTH)
            add(qrLabel, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }
    }

    private fun generateQr(content: String, size: Int): BufferedImage {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        return MatrixToImageWriter.toBufferedImage(matrix)
    }

    private fun localIp(): String =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress ?: "localhost"
}
```

- [ ] **Step 2: Verify it compiles**

```powershell
.\gradlew :intellijPlugin:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the plugin in a sandboxed IntelliJ to verify UI**

```powershell
.\gradlew :intellijPlugin:runIde
```

Expected: A second IntelliJ instance opens. The **AgentPilot** tool window appears on the right. It shows a green dot, the WS URL, a QR code, an empty log, and a "Send test event" button.

- [ ] **Step 4: Manual smoke test**
  - Click "Send test event" — log should show `[HH:mm:ss] test/ping → broadcast to running`
  - Open `ws://localhost:27042` in a WebSocket client (e.g. `wscat -c ws://localhost:27042`) and click the button again — the JSON notification should appear in the client

- [ ] **Step 5: Commit**

```bash
git add intellijPlugin/src/main/kotlin/com/agentpilot/plugin/AgentPilotToolWindow.kt
git commit -m "feat: tool window with status, QR code, event log, and test broadcast"
```

---

## Task 6: Final build verification

- [ ] **Step 1: Build the plugin distribution**

```powershell
.\gradlew :intellijPlugin:buildPlugin
```

Expected: `BUILD SUCCESSFUL`. ZIP produced at `intellijPlugin/build/distributions/AgentPilot-0.1.0.zip`.

- [ ] **Step 2: Share WebSocketServer with teammates**

Notify Person B and C that `WebSocketServer.broadcast(endpoint, content)` is ready. They can call it from anywhere in the plugin once their sidecar integration is wired. The method is thread-safe.

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat: IntelliJ plugin skeleton complete — WS server + tool window"
git push
```
