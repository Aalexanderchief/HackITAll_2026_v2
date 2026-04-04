# UI API Reference — Networking Layer

This document describes the complete interface between the networking layer (Person C) and the UI layer (Person B). All types live in the `shared` module and are available to every composable in the app. Person B should never import from `com.agentpilot.shared.network` directly beyond `ConnectionViewModel` — everything else is an implementation detail.

---

## Setup

`ConnectionViewModel` is a plain KMP class. It must be held at a scope that **survives recomposition** — screen rotation creates a new composition but must not create a new ViewModel, or the WebSocket connection drops.

The recommended approach for Android is to wrap it in an AndroidX `ViewModel`:

```kotlin
// In androidApp — one-time setup
class AppViewModel : androidx.lifecycle.ViewModel() {
    val connection = ConnectionViewModel()
    override fun onCleared() = connection.onCleared()
}
```

Then in your root composable or Activity:

```kotlin
val appViewModel: AppViewModel = viewModel()
val vm = appViewModel.connection
```

Pass `vm` down the composable tree. Do not instantiate `ConnectionViewModel()` inside a composable directly.

---

## Imports

```kotlin
import com.agentpilot.shared.network.ConnectionViewModel
import com.agentpilot.shared.network.ConnectionState
import com.agentpilot.shared.network.DiscoveryState
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus
import com.agentpilot.shared.models.InputSource
import com.agentpilot.shared.models.ChangeAction

// Android-only (QR scanning screen):
import com.agentpilot.shared.platform.QrScannerScreen
```

---

## Observable State Flows

All flows are `StateFlow` — they always hold a current value and never miss an emission. Collect them with `collectAsState()` in any composable.

### `connectionState: StateFlow<ConnectionState>`

The WebSocket connection lifecycle. Use this to drive a status indicator visible throughout the app.

```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting   : ConnectionState()
    data class Connected(val peerVersion: String) : ConnectionState()
    data class Failed(val cause: Throwable)       : ConnectionState()
}
```

`Failed` triggers automatic reconnection with exponential backoff — it is a transient state, not a terminal one. `Disconnected` is the terminal state after `disconnect()` is called manually.

```kotlin
@Composable
fun ConnectionBadge(vm: ConnectionViewModel) {
    val state by vm.connectionState.collectAsState()
    val (label, color) = when (state) {
        is ConnectionState.Disconnected -> "Disconnected" to Color.Gray
        is ConnectionState.Connecting   -> "Connecting…"  to Color.Yellow
        is ConnectionState.Connected    -> "Connected"    to Color.Green
        is ConnectionState.Failed       -> "Retrying…"    to Color.Red
    }
    Text(label, color = color)
}
```

---

### `discoveryState: StateFlow<DiscoveryState>`

Active only during code-based pairing (`connectViaCode`). Returns to `Idle` once the device is found or fails.

```kotlin
sealed class DiscoveryState {
    object Idle      : DiscoveryState()
    object Searching : DiscoveryState()   // broadcast sent, waiting for reply
    object NotFound  : DiscoveryState()   // 5-second timeout elapsed, no reply
    data class Error(val cause: Throwable) : DiscoveryState()
}
```

```kotlin
@Composable
fun DiscoveryFeedback(vm: ConnectionViewModel) {
    val discovery by vm.discoveryState.collectAsState()
    when (discovery) {
        DiscoveryState.Idle      -> Unit
        DiscoveryState.Searching -> CircularProgressIndicator()
        DiscoveryState.NotFound  -> Text("No device found. Check the code and try again.")
        is DiscoveryState.Error  -> Text("Error: ${(discovery as DiscoveryState.Error).cause.message}")
    }
}
```

---

### `agentStatuses: StateFlow<Map<String, AgentMessage.AgentStatusUpdate>>`

The latest known status for every agent, keyed by `agentId`. Each time a new status update arrives for an agent, its map entry is replaced. The map grows as new agents are seen — it is never cleared during a session.

```kotlin
data class AgentStatusUpdate(
    val agentId:     String,
    val status:      AgentStatus,   // IDLE | RUNNING | WAITING_FOR_INPUT | WAITING_FOR_REVIEW | COMPLETED | FAILED
    val progress:    Float,         // 0.0 to 1.0
    val currentTask: String,
)
```

```kotlin
@Composable
fun AgentListScreen(vm: ConnectionViewModel) {
    val statuses by vm.agentStatuses.collectAsState()
    LazyColumn {
        items(statuses.values.toList()) { agent ->
            AgentRow(agent)
        }
    }
}

@Composable
fun AgentRow(agent: AgentMessage.AgentStatusUpdate) {
    Column {
        Text(agent.agentId)
        Text(agent.currentTask)
        Text(agent.status.name)
        LinearProgressIndicator(progress = agent.progress)
    }
}
```

---

### `activeClarification: StateFlow<AgentMessage.ClarificationRequest?>`

The current pending clarification request, or `null` if the agent is not waiting for input. Show the clarification UI when this is non-null. After calling `send(ClarificationResponse(...))`, this resets to `null` automatically — no manual dismissal needed.

```kotlin
data class ClarificationRequest(
    val id:      String,
    val question: String,
    val context:  String,
    val options:  List<String>?,   // null = free text input; non-null = show as choice buttons
)
```

```kotlin
@Composable
fun AgentDetailScreen(vm: ConnectionViewModel, coroutineScope: CoroutineScope) {
    val clarification by vm.activeClarification.collectAsState()

    clarification?.let { request ->
        ClarificationDialog(
            request = request,
            onSubmit = { answer, source ->
                coroutineScope.launch {
                    vm.send(
                        AgentMessage.ClarificationResponse(
                            id = request.id,
                            answer = answer,
                            source = source,
                        )
                    )
                    // dialog dismisses automatically — activeClarification becomes null
                }
            }
        )
    }
}

@Composable
fun ClarificationDialog(
    request: AgentMessage.ClarificationRequest,
    onSubmit: (answer: String, source: InputSource) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},    // not dismissable without answering
        title   = { Text(request.question) },
        text    = {
            Column {
                Text(request.context)
                if (request.options != null) {
                    // Multiple choice
                    request.options.forEach { option ->
                        Button(onClick = { onSubmit(option, InputSource.TEXT) }) {
                            Text(option)
                        }
                    }
                } else {
                    // Free text
                    TextField(value = text, onValueChange = { text = it })
                }
            }
        },
        confirmButton = {
            if (request.options == null) {
                Button(onClick = { onSubmit(text, InputSource.TEXT) }) { Text("Send") }
            }
        }
    )
}
```

---

### `activeCodeReview: StateFlow<AgentMessage.CodeChangeProposal?>`

The current pending code change proposal, or `null` if none. Show the code review UI when non-null. After calling `send(CodeChangeVerdict(...))`, this resets to `null` automatically.

```kotlin
data class CodeChangeProposal(
    val id:          String,
    val filePath:    String,
    val diff:        String,        // unified diff string
    val explanation: String,
)
```

```kotlin
@Composable
fun CodeReviewSheet(vm: ConnectionViewModel, coroutineScope: CoroutineScope) {
    val proposal by vm.activeCodeReview.collectAsState()
    var alternativeText by remember { mutableStateOf("") }

    proposal?.let { p ->
        ModalBottomSheet(onDismissRequest = {}) {  // not dismissable without a verdict
            Text(p.filePath, style = MaterialTheme.typography.titleMedium)
            Text(p.explanation)
            Text(p.diff, fontFamily = FontFamily.Monospace)

            Row {
                Button(onClick = {
                    coroutineScope.launch {
                        vm.send(AgentMessage.CodeChangeVerdict(id = p.id, action = ChangeAction.ACCEPT))
                        // sheet dismisses automatically
                    }
                }) { Text("Accept") }

                Button(onClick = {
                    coroutineScope.launch {
                        vm.send(AgentMessage.CodeChangeVerdict(id = p.id, action = ChangeAction.REJECT))
                    }
                }) { Text("Reject") }
            }

            // Optional: modify with instructions
            TextField(value = alternativeText, onValueChange = { alternativeText = it }, label = { Text("Modify instructions") })
            Button(
                enabled = alternativeText.isNotBlank(),
                onClick = {
                    coroutineScope.launch {
                        vm.send(AgentMessage.CodeChangeVerdict(id = p.id, action = ChangeAction.MODIFY, alternative = alternativeText))
                    }
                }
            ) { Text("Modify") }
        }
    }
}
```

`ChangeAction` values: `ACCEPT`, `REJECT`, `MODIFY`. When `MODIFY`, populate `alternative` with the instruction text. For `ACCEPT` and `REJECT`, `alternative` should be `null`.

---

### `activityFeed: StateFlow<List<AgentMessage>>`

A chronological list of the last 50 messages received over the WebSocket, all types included. New messages are appended at the end. Use this for the main activity feed on the agent detail screen.

```kotlin
@Composable
fun ActivityFeed(vm: ConnectionViewModel) {
    val feed by vm.activityFeed.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(feed.size) {
        if (feed.isNotEmpty()) listState.animateScrollToItem(feed.lastIndex)
    }

    LazyColumn(state = listState) {
        items(feed) { message ->
            ActivityRow(message)
        }
    }
}

@Composable
fun ActivityRow(message: AgentMessage) {
    when (message) {
        is AgentMessage.AgentStatusUpdate   -> Text("Agent ${message.agentId}: ${message.status} — ${message.currentTask}")
        is AgentMessage.LlmRequestCapture   -> Text("→ Prompt sent to ${message.model}")
        is AgentMessage.LlmResponseChunk    -> Text("← ${message.token}", color = Color.Gray)
        is AgentMessage.ClarificationRequest -> Text("? ${message.question}")
        is AgentMessage.ClarificationResponse-> Text("✓ Answered: ${message.answer}")
        is AgentMessage.CodeChangeProposal  -> Text("Δ Diff proposed for ${message.filePath}")
        is AgentMessage.CodeChangeVerdict   -> Text("Verdict: ${message.action} on ${message.id}")
        is AgentMessage.ConnectionHandshake -> Text("Connected (v${message.version})")
    }
}
```

---

## Connection Entry Points

These are called from connection/pairing screens, not from agent screens.

```kotlin
vm.connectViaQr(url)            // url comes from QrScannerScreen.onScanned — do not modify it
vm.connectViaCode("A3F-92B")    // triggers UDP broadcast; watch discoveryState for progress
vm.connectViaIp("192.168.1.5")  // direct IP, port 27042 assumed
vm.disconnect()                 // manual disconnect; connectionState → Disconnected
```

---

## QR Scanner Composable (Android only)

`QrScannerScreen` lives in `androidMain`. It requests camera permission automatically on first launch and calls `onScanned` exactly once when a valid WebSocket URL (`ws://` or `wss://`) is found.

```kotlin
// Import in androidMain source only:
import com.agentpilot.shared.platform.QrScannerScreen

@Composable
fun PairingScreen(vm: ConnectionViewModel, onDone: () -> Unit) {
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        QrScannerScreen(
            onScanned = { url ->
                vm.connectViaQr(url)
                showScanner = false
                onDone()
            },
            onDismiss = { showScanner = false }
        )
    } else {
        // Show pairing options: QR button, code entry field, IP entry field
    }
}
```

`QrScannerScreen` takes the full screen. Wrap it in a navigation destination or a full-screen dialog.

`CAMERA` permission must be declared in `androidApp/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
```

---

## Sending Messages

`send()` is a **suspend function**. Call it inside a `CoroutineScope` — the composable's `rememberCoroutineScope()` is the right scope for button click handlers.

```kotlin
val scope = rememberCoroutineScope()

Button(onClick = {
    scope.launch {
        vm.send(
            AgentMessage.ClarificationResponse(
                id = request.id,
                answer = "Use the plugin approach",
                source = InputSource.TEXT,    // or InputSource.VOICE if answer came from STT
            )
        )
    }
})
```

`InputSource` values: `TEXT` (typed or selected), `VOICE` (transcribed by the STT recognizer — Person C provides this).

`send()` is a no-op if the WebSocket is not in `Connected` state — no need to guard it manually.

---

## Enum Reference

```kotlin
enum class AgentStatus {
    IDLE, RUNNING, WAITING_FOR_INPUT, WAITING_FOR_REVIEW, COMPLETED, FAILED
}

enum class InputSource { TEXT, VOICE }

enum class ChangeAction { ACCEPT, REJECT, MODIFY }
```

`WAITING_FOR_INPUT` means a `ClarificationRequest` is expected or already in `activeClarification`.
`WAITING_FOR_REVIEW` means a `CodeChangeProposal` is expected or already in `activeCodeReview`.

---

## Flow Summary

| Flow | Type | Screen |
|---|---|---|
| `connectionState` | `StateFlow<ConnectionState>` | Global status indicator |
| `discoveryState` | `StateFlow<DiscoveryState>` | Pairing / code-entry screen |
| `agentStatuses` | `StateFlow<Map<String, AgentStatusUpdate>>` | Agent list screen |
| `activeClarification` | `StateFlow<ClarificationRequest?>` | Clarification dialog |
| `activeCodeReview` | `StateFlow<CodeChangeProposal?>` | Code review bottom sheet |
| `activityFeed` | `StateFlow<List<AgentMessage>>` | Agent detail / event feed |
