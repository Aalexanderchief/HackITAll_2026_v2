package com.agentpilot.shared.platform

/**
 * State machine for the speech-to-text handler.
 *
 * Idle      — microphone is off, ready to start
 * Listening — recording in progress; [partial] holds the live transcription so far
 * Result    — recognition finished (either by user stopping or Android's VAD); [text] is final
 * Error     — recognition failed; [message] is human-readable
 *
 * Transitions:
 *   Idle / Result / Error  ──toggle()──→  Listening
 *   Listening              ──toggle()──→  (stopListening called; Result follows via callback)
 *   Listening              ──auto VAD──→  Result
 *   any                    ──reset()───→  Idle
 */
sealed class SpeechState {
    object Idle : SpeechState()
    data class Listening(val partial: String) : SpeechState()
    data class Result(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}
