# IntelliJ Plugin — Design Spec
_Date: 2026-04-04 | Author: Person A_

## Overview

An IntelliJ Platform plugin that embeds a Ktor WebSocket server on `localhost:27042` and broadcasts MCP tool call events to connected mobile clients. Provides a tool window showing connection status, a QR code for pairing, and a live event log.

## Module Structure

New `intellijPlugin/` module in the existing repo, alongside `shared/` and `androidApp/`.

```
intellijPlugin/
├── build.gradle.kts
└── src/main/
    ├── kotlin/com/agentpilot/plugin/
    │   ├── AgentPilotPlugin.kt         ← ProjectManagerListener: starts/stops WS server
    │   ├── WebSocketServer.kt          ← Ktor embeddedServer on :27042, broadcast API
    │   └── AgentPilotToolWindow.kt     ← Tool window UI: status + QR + event log
    └── resources/META-INF/
        └── plugin.xml                  ← Plugin descriptor
```

## Plugin Descriptor (plugin.xml)

- Plugin ID: `com.agentpilot.plugin`
- Registers one `toolWindow` extension (id: `AgentPilot`, anchor: `right`)
- Registers one `projectManagerListener` application service for lifecycle

## Startup Flow

1. `ProjectManagerListener.projectOpened()` fires automatically
2. Starts `WebSocketServer` on `localhost:27042`
3. Tool window initializes: shows green status dot + QR code of `ws://<local-ip>:27042`
4. On project close: `projectClosed()` stops the server, cleans up sessions

## WebSocket Server

- `Ktor embeddedServer(Netty)` with `WebSockets` plugin
- Connected sessions stored in `ConcurrentHashMap<String, DefaultWebSocketSession>`
- `suspend fun broadcast(endpoint: String, content: Any)` serializes and sends to all sessions:

```json
{
  "type": "mcp-notification",
  "payload": {
    "endpoint": "<tool name>",
    "content": {},
    "timestamp": "<ISO-8601>"
  }
}
```

- Sessions cleaned up automatically on disconnect

## Tool Window UI

Built with IntelliJ Platform Swing (standard for plugin tool windows).

**Top panel:**
- Connection status dot (green = server running, red = stopped)
- Label: `ws://<local-ip>:27042`
- QR code image (generated from the WS URL using `zxing`)

**Bottom panel:**
- Scrollable `JTextArea` event log
- Each entry: `[HH:mm:ss] <endpoint>` 
- "Send test event" button → calls `broadcast("test/ping", {})` so Person B/C can test their client before sidecar is wired

## Data Flow

```
[Hour 1-6]  "Send test event" button → WebSocketServer.broadcast()
[Hour 6+]   MCP Proxy Sidecar → AgentPilotPlugin middleware → WebSocketServer.broadcast()
                                                                      ↓
                                                             all connected phones
```

## Dependencies

| Library | Purpose |
|---------|---------|
| `io.ktor:ktor-server-netty` | Embedded HTTP/WS server |
| `io.ktor:ktor-server-websockets` | WebSocket support |
| `io.ktor:ktor-serialization-kotlinx-json` | JSON serialization |
| `com.google.zxing:core` + `javase` | QR code generation |
| IntelliJ Platform SDK | Tool window, project listener |

## Out of Scope (this skeleton)

- MCP Proxy Sidecar middleware wiring (Hour 6+ task)
- DiffManager API integration
- Authentication / pairing PIN
