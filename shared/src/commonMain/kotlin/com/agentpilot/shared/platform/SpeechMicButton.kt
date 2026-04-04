package com.agentpilot.shared.platform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Self-contained speech-to-text button.
 *
 * Creates and manages its own [SpeechRecognizer] instance — no setup required from the caller.
 * The recognizer is destroyed automatically when this composable leaves the composition.
 *
 * Behaviour:
 *   - Tap once  → starts listening; button turns red with a Stop icon
 *   - Tap again → stops listening; final result delivered to [onResult]
 *   - Android may also stop automatically after silence; [onResult] is called either way
 *   - [onResult] is called exactly once per recording session with the transcribed string
 *   - After [onResult] fires the button resets to idle automatically
 *
 * While listening, the partial transcription is shown above the button as a live preview.
 * If recognition fails, a brief error message is shown and the button returns to idle.
 *
 * Example usage inside a clarification dialog:
 *
 *   var answerText by remember { mutableStateOf("") }
 *   SpeechMicButton(onResult = { answerText = it })
 *   TextField(value = answerText, onValueChange = { answerText = it })
 */
@Composable
fun SpeechMicButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recognizer = remember { SpeechRecognizer() }

    DisposableEffect(Unit) {
        onDispose { recognizer.destroy() }
    }

    // Deliver the final result to the caller and reset to idle.
    val state by recognizer.state.collectAsState()
    LaunchedEffect(state) {
        if (state is SpeechState.Result) {
            onResult((state as SpeechState.Result).text)
            recognizer.reset()
        }
    }

    val isListening = state is SpeechState.Listening

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        // Live partial transcription shown while recording
        val partial = (state as? SpeechState.Listening)?.partial.orEmpty()
        if (partial.isNotEmpty()) {
            Text(
                text = partial,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Error hint (clears on next tap via toggle → startListening)
        (state as? SpeechState.Error)?.let {
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        FloatingActionButton(
            onClick = { recognizer.toggle() },
            containerColor = if (isListening) MaterialTheme.colorScheme.error
                             else MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop recording" else "Start recording",
            )
        }
    }
}
