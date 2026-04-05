package com.agentpilot.shared.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JVM/Desktop implementation of SpeechRecognizer.
 * Currently a no-op as desktop speech recognition is not supported in the hackathon MVP.
 */
actual class SpeechRecognizer actual constructor() {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    actual val state: StateFlow<SpeechState> = _state.asStateFlow()

    actual fun toggle() {
        // No-op for desktop
    }

    actual fun reset() {
        _state.value = SpeechState.Idle
    }

    actual fun destroy() {
        // No-op for desktop
    }
}
