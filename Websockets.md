# WebSocket & Networking Protocol — Integration Reference

This document covers the full networking layer implemented in the shared KMP module. It is the authoritative reference for Person A (IntelliJ plugin / server side) and Person B (mobile UI / consumer side).

---

## Ports

| Purpose            | Protocol  | Port  |
|--------------------|-----------|-------|
| Agent event stream | WebSocket | 27042 |
| Device discovery   | UDP       | 27043 |

---

## Source Layout

All networking code lives in `shared/src/`:

```
commonMain/kotlin/com/agentpilot/shared/
  models/
    AgentMessage.kt          — sealed class hierarchy, all message types
  network/
    ConnectionState.kt       — sealed class: WS connection lifecycle states
    DiscoveryState.kt        — sealed class: UDP discovery phase states
    WebSocketClient.kt       — Ktor WS client, reconnection, handshake
    DeviceDiscovery.kt       — UDP broadcast discovery (ktor-network)
    ConnectionViewModel.kt   — orchestrator: merges all connection paths

androidMain/kotlin/com/agentpilot/shared/platform/
  QrScannerScreen.kt         — Composable camera + ML Kit QR scanner
  SpeechRecognizer.android.kt — STT actual implementation (Person C)
```

Everything in `commonMain` is Kotlin Multiplatform — it compiles for Android (JVM) and Native (iOS, desktop) without modification. `androidMain` contains Android-specific code that cannot be abstracted.

---

## Message Format

All WebSocket frames carry **JSON text**. The model is `AgentMessage`, a `@Serializable` sealed class in `shared/models/AgentMessage.kt`. kotlinx.serialization uses the field `"type"` as the polymorphism discriminator (this is the default `classDiscriminator`). Every message sent or received must include this field.

### Complete type catalogue

| `type` value            | Direction          | Purpose |
|-------------------------|--------------------|---------|
| `connection_handshake`  | client→server, then server→client (echo) | Establishes connection |
| `agent_status_update`   | server→client      | Agent progress feed |
| `llm_request_capture`   | server→client      | Prompt being sent to LLM |
| `llm_response_chunk`    | server→client      | Streaming token from LLM |
| `clarification_request` | server→client      | Agent needs input |
| `clarification_response`| client→server      | User's answer |
| `code_change_proposal`  | server→client      | Diff for review |
| `code_change_verdict`   | client→server      | Accept / reject / modify |

### JSON examples

```json
{"type":"connection_handshake","version":"1.0","capabilities":["clarification","code-review"]}

{"type":"agent_status_update","agentId":"junie-1","status":"RUNNING","progress":0.4,"currentTask":"Refactoring Ktor client"}

{"type":"llm_request_capture","requestId":"req-1","model":"claude-sonnet-4-6","prompt":"Refactor this function...","timestamp":"2026-04-04T10:00:00Z"}

{"type":"llm_response_chunk","requestId":"req-1","token":"Here","isComplete":false}

{"type":"clarification_request","id":"clr-1","question":"Use plugin or manual approach?","context":"Adding Ktor iOS support","options":["Plugin","Manual"]}

{"type":"clarification_response","id":"clr-1","answer":"Plugin","source":"TEXT"}

{"type":"code_change_proposal","id":"cp-1","filePath":"src/Network.kt","diff":"--- a/src/Network.kt\n+++ b/src/Network.kt\n...","explanation":"Switched to Ktor 3 WebSocket API"}

{"type":"code_change_verdict","id":"cp-1","action":"ACCEPT","alternative":null}

{"type":"code_change_verdict","id":"cp-2","action":"MODIFY","alternative":"Keep it in commonMain instead"}
```

`options` in `clarification_request` is nullable — `null` means free-text input, a non-null list means multiple choice. `alternative` in `code_change_verdict` is only populated when `action` is `MODIFY`.

`AgentStatus` enum values: `IDLE`, `RUNNING`, `WAITING_FOR_INPUT`, `WAITING_FOR_REVIEW`, `COMPLETED`, `FAILED`.

`InputSource` enum values: `TEXT`, `VOICE`.

`ChangeAction` enum values: `ACCEPT`, `REJECT`, `MODIFY`.

---

## WebSocket Protocol (port 27042)

### Connection & Handshake

The mobile app is the **client**. The IntelliJ plugin is the **server**.

Immediately after the TCP/WebSocket upgrade, the client sends a `connection_handshake`. The server must echo it back unchanged. The client only transitions to `Connected` state after receiving this echo — it blocks on the handshake before entering the normal receive loop. Do not send any other messages before the echo.

```
[Mobile — client]                     [IntelliJ Plugin — server]
        │                                          │
        │──── WebSocket upgrade ─────────────────→ │
        │                                          │
        │──── connection_handshake ───────────────→ │
        │     version: "1.0"                        │
        │     capabilities: ["clarification",       │
        │                    "code-review"]          │
        │                                           │
        │ ←── connection_handshake (echo) ──────────│
        │     (same payload)                        │
        │                                           │
  state → Connected                                 │
        │                                           │
        │ ←── agent_status_update / etc. ───────────│  (normal operation)
        │──── clarification_response / verdict ───→ │
```

### Reconnection

`WebSocketClient` reconnects automatically on unexpected drops using exponential backoff: 1 s → 2 s → 4 s → … → 30 s cap. Backoff resets to 1 s after each successful connection. Calling `disconnect()` (user-initiated) cancels the retry loop and sends a WebSocket close frame with `NORMAL` code. The server can distinguish user disconnects from drops by the close code.

### Connection state machine

`ConnectionState` (in `ConnectionState.kt`, observed via `WebSocketClient.state`):

```
Disconnected ──connect()──→ Connecting ──handshake ok──→ Connected
     ↑                           │                           │
     │                      exception                   WS closes
     │                           ↓                      unexpectedly
     └──disconnect()────── Failed(cause) ←───────────────────┘
                           (retries after backoff)
```

`ConnectionState.Connected` carries `peerVersion: String` (the version from the server's handshake echo), which can be displayed in the UI.

---

## UDP Discovery Protocol (port 27043)

Used when the user enters a short code instead of scanning a QR code. The IntelliJ plugin displays a short alphanumeric code (e.g. `A3F-92B`) and listens on UDP port 27043.

**Discovery packet** (mobile → broadcast `255.255.255.255:27043`), plain UTF-8 text:
```
DISCOVER:A3F-92B
```

**Reply** (plugin → mobile), plain UTF-8 text — only sent if the code matches:
```
ws://192.168.1.5:27042
```

The plugin must reply using the machine's **LAN IP address** (not `127.0.0.1`). The mobile connects to that URL via WebSocket immediately after receiving the reply. The mobile side times out after 5 seconds with `TimeoutCancellationException`, which surfaces as `DiscoveryState.NotFound` in the UI.

`DiscoveryState` (in `DiscoveryState.kt`, observed via `ConnectionViewModel.discoveryState`):

```
Idle ──connectViaCode()──→ Searching ──reply received──→ Idle → (WebSocket connects)
                                   └──timeout──→ NotFound
                                   └──error───→ Error(cause)
```

`DiscoveryState` is independent of `ConnectionState`. Once discovery resolves to a URL, `DiscoveryState` returns to `Idle` and the normal `ConnectionState` progression takes over.

**Implementation note:** `discoverDevice()` in `DeviceDiscovery.kt` uses `ktor-network` (`io.ktor:ktor-network`) with the `kotlinx-io` `Buffer` API (Ktor 3.x). The `kotlinx-io` dependency is transitive — no separate entry in `libs.versions.toml` is needed. `SelectorManager(Dispatchers.IO)` is used; `Dispatchers.IO` is available in commonMain from kotlinx-coroutines 1.7+.

---

## QR Code (port 27042)

The QR code displayed in the IntelliJ tool window must encode the WebSocket URL directly as a plain string:

```
ws://192.168.1.5:27042
```

The mobile app's `QrScannerScreen` (in `androidMain`) decodes the raw string from the QR and passes it to `ConnectionViewModel.connectViaQr(url)`. The scanner validates that the URL starts with `ws://` or `wss://` before forwarding it — anything else is silently ignored. No discovery step occurs; the URL is used directly.

`QrScannerScreen` is a `@Composable` in `androidMain` that takes two callbacks: `onScanned(url: String)` and `onDismiss()`. It requests `CAMERA` permission on first launch. Multiple detections of the same QR across consecutive frames are deduplicated with an `alreadyScanned` flag. The `CAMERA` permission must be declared in `androidApp/src/main/AndroidManifest.xml`.

---

## ConnectionViewModel — Consumer API for Person B

`ConnectionViewModel` (in `commonMain/network/`) is the single entry point for all UI code. Person B's screens should never interact with `WebSocketClient` or `discoverDevice()` directly.

### Instantiation and lifecycle

`ConnectionViewModel` is a plain class (not `androidx.lifecycle.ViewModel`) to stay KMP-compatible. It must be held at a scope that survives recomposition — either in an Android `ViewModel` wrapper or as a singleton on the `Application`. Call `onCleared()` when the host is destroyed; this calls `WebSocketClient.close()` (cancels its internal coroutine scope) and cancels the ViewModel's own scope.

### Observed flows

```kotlin
val connectionState: StateFlow<ConnectionState>  // WS lifecycle
val discoveryState:  StateFlow<DiscoveryState>   // UDP discovery phase
val messages:        SharedFlow<AgentMessage>     // all incoming messages
```

`messages` is a `SharedFlow` with no replay — subscribers only see messages emitted after they start collecting. Person B's screens should collect from this flow in a coroutine tied to the composable lifecycle.

### Connection entry points

```kotlin
viewModel.connectViaQr(url)          // URL from QrScannerScreen.onScanned
viewModel.connectViaCode("A3F-92B")  // user-entered code, triggers UDP discovery
viewModel.connectViaIp("192.168.1.5") // direct IP, assumes port 27042
```

All three paths converge to `WebSocketClient.connect(url)` internally.

### Sending messages from the UI

```kotlin
// In a coroutine (e.g. LaunchedEffect or button handler):
viewModel.send(AgentMessage.ClarificationResponse(id = "clr-1", answer = "Plugin", source = InputSource.TEXT))
viewModel.send(AgentMessage.CodeChangeVerdict(id = "cp-1", action = ChangeAction.ACCEPT, alternative = null))
```

`send()` is a suspend function — call it from a coroutine. It is a no-op if not connected.

---

## Dependency Overview

| Artifact | Source set | Purpose |
|---|---|---|
| `io.ktor:ktor-client-core` | commonMain | HTTP/WS client base |
| `io.ktor:ktor-client-websockets` | commonMain | WebSocket plugin for Ktor client |
| `io.ktor:ktor-client-okhttp` | androidMain | OkHttp engine (auto-selected by `HttpClient()`) |
| `io.ktor:ktor-network` | commonMain | Raw UDP sockets for discovery |
| `io.ktor:ktor-client-content-negotiation` | commonMain | Content negotiation plugin |
| `io.ktor:ktor-serialization-kotlinx-json` | commonMain | JSON serialization for Ktor |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | commonMain | kotlinx.serialization runtime |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | commonMain | Coroutines, Flow, StateFlow |
| `androidx.camera:camera-*` | androidMain | CameraX for QR scanning |
| `com.google.mlkit:barcode-scanning` | androidMain | QR code detection |
| `androidx.activity:activity-compose` | androidMain | Permission launcher in Compose |
| `androidx.lifecycle:lifecycle-runtime-compose` | androidMain | `LocalLifecycleOwner` for CameraX binding |
| `compose.materialIconsExtended` | commonMain | Material icons (e.g. close button in QR screen) |
