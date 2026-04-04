package com.agentpilot.shared.platform

import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific speech-to-text.
 * Android actual: android.speech.SpeechRecognizer
 * Implemented by Person C — stub here so shared module compiles.
 */
expect class SpeechRecognizer() {
    /** Emits recognized text fragments as the user speaks. Completes on silence. */
    fun recognize(): Flow<String>
    fun cancel()
}
