package com.agentpilot.shared.platform

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of [SpeechRecognizer].
 *
 * Must be created on the main thread — safe when used inside remember {} in a composable.
 * Call [destroy] inside a DisposableEffect onDispose block to release the underlying
 * Android recognizer when the composable leaves the composition.
 *
 * Partial results are enabled: [SpeechState.Listening] updates live as the user speaks.
 * Final result arrives via [SpeechState.Result] either when Android's VAD detects silence
 * or when the user calls [toggle] while listening (stop path).
 */
actual class SpeechRecognizer actual constructor() {

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    actual val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val recognizer = AndroidSpeechRecognizer.createSpeechRecognizer(AppContext.app)

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            _state.value = SpeechState.Listening(partial)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
            _state.value = if (text != null) {
                SpeechState.Result(text)
            } else {
                // No final text — try to salvage the last partial rather than emitting empty.
                val partial = (_state.value as? SpeechState.Listening)?.partial?.takeIf { it.isNotBlank() }
                if (partial != null) SpeechState.Result(partial) else SpeechState.Error("No speech detected")
            }
        }

        override fun onEndOfSpeech() {
            // VAD detected silence — onResults will follow shortly, no state change needed here.
        }

        override fun onError(error: Int) {
            // Only surface errors that are meaningful to the user.
            // ERROR_NO_MATCH after the user pressed stop is expected (partial was already shown).
            val current = _state.value
            if (error == AndroidSpeechRecognizer.ERROR_NO_MATCH && current is SpeechState.Listening) {
                // Promote the partial to a result rather than an error, but only if there is one.
                val partial = current.partial.takeIf { it.isNotBlank() }
                _state.value = if (partial != null) SpeechState.Result(partial) else SpeechState.Error("No speech detected")
                return
            }
            _state.value = SpeechState.Error(errorMessage(error))
        }
    }

    init {
        recognizer.setRecognitionListener(listener)
    }

    /**
     * Starts listening if currently [SpeechState.Idle], [SpeechState.Result], or [SpeechState.Error].
     * Stops listening if currently [SpeechState.Listening] — the final result arrives via the
     * RecognitionListener callback, not immediately.
     */
    actual fun toggle() {
        when (_state.value) {
            is SpeechState.Idle,
            is SpeechState.Result,
            is SpeechState.Error -> startListening()
            is SpeechState.Listening -> recognizer.stopListening()
        }
    }

    /** Cancels any in-progress recognition and resets to [SpeechState.Idle]. */
    actual fun reset() {
        recognizer.cancel()
        _state.value = SpeechState.Idle
    }

    /** Releases the underlying Android recognizer. Call from DisposableEffect onDispose. */
    actual fun destroy() {
        recognizer.cancel()
        recognizer.destroy()
    }

    private fun startListening() {
        _state.value = SpeechState.Listening("")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    private fun errorMessage(code: Int) = when (code) {
        AndroidSpeechRecognizer.ERROR_NO_MATCH       -> "No speech detected"
        AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out"
        AndroidSpeechRecognizer.ERROR_AUDIO          -> "Audio recording error"
        AndroidSpeechRecognizer.ERROR_NETWORK,
        AndroidSpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error"
        AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
        else -> "Recognition error (code $code)"
    }
}
