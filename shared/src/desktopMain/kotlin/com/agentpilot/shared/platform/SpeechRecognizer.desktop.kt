package com.agentpilot.shared.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class SpeechRecognizer actual constructor() {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    actual val state: StateFlow<SpeechState> = _state.asStateFlow()

    actual fun toggle() {
        _state.value = SpeechState.Error("Voice input is not available on desktop — please type your response.")
    }

    actual fun reset() {
        _state.value = SpeechState.Idle
    }

    actual fun destroy() {}
}
