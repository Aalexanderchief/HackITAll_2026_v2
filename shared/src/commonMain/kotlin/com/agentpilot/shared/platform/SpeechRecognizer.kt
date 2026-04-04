package com.agentpilot.shared.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Speech-to-text handler with toggle behaviour.
 *
 * Usage:
 *   - Call [toggle] when the microphone button is pressed.
 *     If idle/done/error → starts listening.
 *     If listening → stops and forces a result from speech captured so far.
 *   - Observe [state] to drive the UI:
 *       Idle          → show mic button
 *       Listening     → show waveform + partial text + active mic button
 *       Result(text)  → pre-fill answer field with [text], offer to send
 *       Error(msg)    → show error, allow retry via [toggle]
 *   - Call [reset] to discard the result and return to Idle (e.g. after sending).
 *   - Call [destroy] when the owning composable leaves the composition.
 *
 * Android actual: uses android.speech.SpeechRecognizer with partial results enabled.
 * Must be instantiated on the main thread (safe inside remember {}).
 */
expect class SpeechRecognizer() {
    val state: StateFlow<SpeechState>
    fun toggle()
    fun reset()
    fun destroy()
}
