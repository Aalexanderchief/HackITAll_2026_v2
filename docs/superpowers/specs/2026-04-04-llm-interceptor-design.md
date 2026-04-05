# LLM Action Interceptor — Design Spec
_Date: 2026-04-04 | Author: Person A_

## Overview

Intercept AI Assistant action triggers inside IntelliJ, capture editor context at the moment of invocation, and emit structured events through an internal bus that fans out to the existing WebSocket server on port 27042.

No internal or unstable AI APIs are used. All extension points are stable IntelliJ Platform APIs.

## New Files

| File | Responsibility |
|------|---------------|
| `AgentEventBus.kt` | Singleton `SharedFlow<AgentEvent>` — decouples event sources from WebSocket |
| `AgentEvent.kt` | Sealed class hierarchy for all event types |
| `LlmActionInterceptor.kt` | `AnActionListener` that captures AI action triggers + editor context |

## Modified Files

| File | Change |
|------|--------|
| `plugin.xml` | Register `LlmActionInterceptor` as `AnActionListener` application listener |
| `WebSocketServer.kt` | Collect from `AgentEventBus` instead of being called directly |
| `AgentPilotToolWindow.kt` | Test button emits into `AgentEventBus` instead of calling `broadcast()` directly |

## AgentEvent Sealed Class

```kotlin
sealed class AgentEvent {
    data class McpToolCall(val endpoint: String, val content: JsonElement) : AgentEvent()
    data class LlmRequest(
        val actionId: String,
        val filePath: String,
        val selectedText: String?,
        val caretLine: Int,
        val language: String?
    ) : AgentEvent()
    data class IdeSignal(val type: String, val detail: String) : AgentEvent()
}
```

## AgentEventBus

```kotlin
object AgentEventBus {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    fun emit(event: AgentEvent) {
        _events.tryEmit(event)
    }
}
```

`tryEmit` is used (non-suspending) so callers on the EDT never block. Buffer of 64 absorbs bursts.

## LlmActionInterceptor

Implements `AnActionListener`. Filters on action IDs known to trigger AI Assistant:

| Action ID | Trigger |
|-----------|---------|
| `AiAssistantAction` | Main AI Assistant shortcut |
| `InlineCompletionAction` | Inline code completion |
| `com.intellij.ml.llm.*` | Any AI LLM action (prefix match) |
| `junie.*` | Junie agent actions (prefix match) |
| `copilot.*` | GitHub Copilot actions (prefix match) |

On `beforeActionPerformed`: snapshot `Editor` state (file path, selected text, caret line, language), emit `AgentEvent.LlmRequest` into `AgentEventBus`.

Unknown action IDs that don't match are ignored entirely — no overhead on non-AI actions.

## WebSocketServer Changes

Add a `startCollecting()` function called from `start()`:

```kotlin
fun start() {
    if (running.getAndSet(true)) return
    // ... existing server setup ...
    scope.launch { collectEvents() }
}

private suspend fun collectEvents() {
    AgentEventBus.events.collect { event ->
        when (event) {
            is AgentEvent.McpToolCall -> broadcast(event.endpoint, event.content)
            is AgentEvent.LlmRequest  -> broadcast("llm/request", Json.encodeToJsonElement(event))
            is AgentEvent.IdeSignal   -> broadcast("ide/signal", Json.encodeToJsonElement(event))
        }
    }
}
```

## Wire Format to Mobile

```json
{
  "type": "mcp-notification",
  "payload": {
    "endpoint": "llm/request",
    "content": {
      "actionId": "AiAssistantAction",
      "filePath": "src/NetworkClient.kt",
      "selectedText": "fun connect() {",
      "caretLine": 42,
      "language": "kotlin"
    },
    "timestamp": "2026-04-04T18:00:00Z"
  }
}
```

## plugin.xml Addition

```xml
<listener class="com.agentpilot.plugin.LlmActionInterceptor"
          topic="com.intellij.openapi.actionSystem.ex.AnActionListener"/>
```

## Stability Contract

- `AnActionListener` — stable since IntelliJ 13, no deprecation warnings
- `Editor`, `PsiFile`, `VirtualFile` — stable platform APIs
- Zero dependency on `com.intellij.ml.llm` or any AI-internal packages
- Graceful on unknown action IDs — filter silently drops them

## Out of Scope

- Raw LLM prompt text (requires internal APIs)
- Token counts / model name (not available without AI plugin APIs)
- Response interception (separate task)
