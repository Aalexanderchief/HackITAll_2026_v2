# Speech-to-Text API Reference

This document describes the complete speech-to-text layer. The primary audience is the UI developer (Person B), who only needs `SpeechMicButton`. The lower sections cover the underlying types for anyone who needs to integrate STT beyond a standard button.

---

## Source Layout

```
shared/src/
  commonMain/kotlin/com/agentpilot/shared/platform/
    SpeechState.kt          — sealed class: all recognition states
    SpeechRecognizer.kt     — expect class: toggle/reset/destroy + state flow
    SpeechMicButton.kt      — self-contained Composable for Person B

  androidMain/kotlin/com/agentpilot/shared/platform/
    AppContext.kt            — Application context holder (internal)
    SpeechRecognizer.android.kt — actual: Android SpeechRecognizer implementation

androidApp/src/androidMain/
  kotlin/com/agentpilot/android/
    AgentPilotApplication.kt — initialises AppContext on startup
  AndroidManifest.xml        — RECORD_AUDIO + CAMERA + INTERNET permissions declared
```

`SpeechState`, `SpeechRecognizer` (expect), and `SpeechMicButton` all live in `commonMain` — they compile for any KMP target. The Android implementation (`SpeechRecognizer.android.kt`) is the only platform-specific file. If an iOS target is added later, only a new `SpeechRecognizer.ios.kt` actual is needed; nothing else changes.

---

## SpeechState

```kotlin
sealed class SpeechState {
    object Idle                          : SpeechState()
    data class Listening(val partial: String) : SpeechState()
    data class Result(val text: String)  : SpeechState()
    data class Error(val message: String): SpeechState()
}
```

| State | Meaning | UI implication |
|---|---|---|
| `Idle` | Microphone is off, ready to start | Show mic button (primary colour) |
| `Listening(partial)` | Recording in progress; `partial` updates live | Show stop button (red), display `partial` as preview text |
| `Result(text)` | Recognition complete; `text` is the final transcription | Pre-fill answer field with `text` |
| `Error(message)` | Recognition failed; `message` is human-readable | Show `message`, allow retry with next tap |

### State machine

```
                 toggle()
Idle     ──────────────────────────────→ Listening("")
Result   ──────────────────────────────→ Listening("")   (new session)
Error    ──────────────────────────────→ Listening("")   (retry)

Listening ─── toggle() ──────────────→ (stopListening sent; Result or Error follows via callback)
Listening ─── Android VAD silence ───→ Result(text)
Listening ─── Android VAD, no speech → Error("No speech detected")

any ─── reset() ─────────────────────→ Idle
```

`Result` and `Error` are never terminal — `toggle()` always starts a new session from either.

---

## SpeechMicButton — Primary API

The only composable Person B needs. Manages the full recognizer lifecycle internally.

```kotlin
@Composable
fun SpeechMicButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

| Parameter | Description |
|---|---|
| `onResult` | Called exactly once per recording session with the final transcribed string. Called on the main thread. After firing, the button resets to `Idle` automatically. |
| `modifier` | Standard Compose modifier applied to the outer `Column`. |

### Behaviour

- **First tap** — starts listening. Button colour changes to `MaterialTheme.colorScheme.error` (red) and icon switches to Stop.
- **Second tap (optional)** — stops listening early. The result is derived from whatever speech was captured so far. If nothing was captured yet, `Error("No speech detected")` is shown.
- **Android auto-stop** — Android's voice activity detector (VAD) stops recognition after natural silence. `onResult` fires automatically without a second tap. For short answers (the common case in this app) the user typically only taps once.
- **Live preview** — while listening, the partial transcription appears above the button. It updates as the user speaks.
- **Error display** — on failure, a one-line error message appears above the button. The button returns to its primary colour. The next tap starts a new session.
- **Lifecycle** — the internal `SpeechRecognizer` is created in `remember {}` (main thread, safe) and destroyed in `DisposableEffect.onDispose`. The caller does not manage any resources.

### Typical usage — inside a clarification dialog

```kotlin
@Composable
fun ClarificationAnswerField() {
    var answerText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column {
        TextField(
            value = answerText,
            onValueChange = { answerText = it },
            label = { Text("Your answer") },
        )

        SpeechMicButton(
            onResult = { transcribed ->
                answerText = transcribed   // pre-fill the field
            },
            modifier = Modifier.padding(top = 12.dp),
        )

        Button(
            enabled = answerText.isNotBlank(),
            onClick = {
                scope.launch {
                    viewModel.send(
                        AgentMessage.ClarificationResponse(
                            id = clarification.id,
                            answer = answerText,
                            source = InputSource.VOICE,   // mark as voice input
                        )
                    )
                }
            }
        ) { Text("Send") }
    }
}
```

The user can also edit `answerText` by typing after dictation — the `TextField` is fully editable. Set `source = InputSource.TEXT` if the user has edited the text after dictation.

---

## SpeechRecognizer — Lower-Level API

Use this only if `SpeechMicButton` does not fit the required UX. All methods must be called on the **main thread**.

```kotlin
expect class SpeechRecognizer() {
    val state: StateFlow<SpeechState>
    fun toggle()
    fun reset()
    fun destroy()
}
```

| Member | Description |
|---|---|
| `state` | `StateFlow<SpeechState>` — current recognition state. Safe to `collectAsState()` in any composable. |
| `toggle()` | Starts listening if `Idle`/`Result`/`Error`. Stops listening if `Listening`. When stopping, the result arrives asynchronously via `state`. |
| `reset()` | Cancels any in-progress recognition immediately (discards partial result) and transitions to `Idle`. Use after consuming a `Result` or dismissing an `Error`. |
| `destroy()` | Releases the underlying Android recognizer. Must be called when the composable that owns this instance leaves the composition. Use `DisposableEffect`. |

### Manual lifecycle management

```kotlin
val recognizer = remember { SpeechRecognizer() }

DisposableEffect(Unit) {
    onDispose { recognizer.destroy() }
}

val state by recognizer.state.collectAsState()

// Deliver result and reset
LaunchedEffect(state) {
    if (state is SpeechState.Result) {
        val text = (state as SpeechState.Result).text
        // use text
        recognizer.reset()
    }
}

Button(onClick = { recognizer.toggle() }) {
    Text(if (state is SpeechState.Listening) "Stop" else "Speak")
}
```

---

## Android implementation notes

### AppContext

`android.speech.SpeechRecognizer.createSpeechRecognizer(context)` requires an Android `Context`. Since the KMP expect constructor is no-arg (no platform types in commonMain), the context is provided via `AppContext.app` — a singleton initialised in `AgentPilotApplication.onCreate()` before any Activity or composable runs.

If `AppContext.app` is accessed before `onCreate()` completes (e.g. during a `ContentProvider` init), it will throw `UninitializedPropertyAccessException`. In normal app flow this cannot happen.

### Threading

The Android `SpeechRecognizer` must be created on the main thread. `remember {}` in a composable always runs on the main thread, so `remember { SpeechRecognizer() }` is the correct and safe pattern.

All `RecognitionListener` callbacks (`onPartialResults`, `onResults`, `onError`, etc.) fire on the main thread. `MutableStateFlow.value` is thread-safe, so emitting from these callbacks is correct.

### Permissions

`RECORD_AUDIO` is declared in `AndroidManifest.xml`. Android's speech recognition service requests runtime permission internally on most API levels. If the permission is denied, `onError` fires with `ERROR_INSUFFICIENT_PERMISSIONS` and `SpeechState.Error("Microphone permission missing")` is emitted — the UI will display this message.

### Error handling

| Android error code | `SpeechState` emitted |
|---|---|
| `ERROR_NO_MATCH` with prior partial | `Result(partial)` — salvages the partial rather than failing |
| `ERROR_NO_MATCH` without prior partial | `Error("No speech detected")` |
| `ERROR_SPEECH_TIMEOUT` | `Error("Listening timed out")` |
| `ERROR_AUDIO` | `Error("Audio recording error")` |
| `ERROR_NETWORK` / `ERROR_NETWORK_TIMEOUT` | `Error("Network error")` |
| `ERROR_INSUFFICIENT_PERMISSIONS` | `Error("Microphone permission missing")` |
| Other | `Error("Recognition error (code N)")` |

`onResults` with a blank/null result also falls back to the last partial, or `Error` if the partial is blank. This means `Result("")` is never emitted.

---

## Integration with ConnectionViewModel

When sending a voice answer to the IntelliJ plugin, pass `InputSource.VOICE` so the plugin knows the response was dictated rather than typed:

```kotlin
viewModel.send(
    AgentMessage.ClarificationResponse(
        id = request.id,
        answer = transcribedText,
        source = InputSource.VOICE,
    )
)
```

If the user edits the transcription before sending, use `InputSource.TEXT` instead. The server uses this field for logging and analytics — it does not affect routing.
