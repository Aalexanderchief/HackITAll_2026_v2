package com.agentpilot.shared.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Stub — Person C replaces this with the real SpeechRecognizer impl. */
actual class SpeechRecognizer actual constructor() {
    actual fun recognize(): Flow<String> = emptyFlow()
    actual fun cancel() = Unit
}
