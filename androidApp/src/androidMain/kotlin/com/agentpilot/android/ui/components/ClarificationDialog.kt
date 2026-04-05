package com.agentpilot.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.platform.SpeechMicButton

/**
 * Dialog for responding to agent clarification requests.
 */
@Composable
fun ClarificationDialog(
    clarification: AgentMessage.ClarificationRequest,
    onDismiss: () -> Unit,
    onRespond: (String) -> Unit
) {
    var textResponse by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = null
            )
        },
        title = {
            Text(text = "Agent Needs Clarification")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Question
                Text(
                    text = clarification.question,
                    style = MaterialTheme.typography.bodyLarge
                )

                // Context (if provided)
                if (clarification.context.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = clarification.context,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Multiple choice options OR text input
                clarification.options?.let { options ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            Button(
                                onClick = { onRespond(option) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(option)
                            }
                        }
                    }
                } ?: run {
                    // Free-text input with voice button
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textResponse,
                            onValueChange = { textResponse = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Your response") },
                            placeholder = { Text("Type your answer here...") },
                            minLines = 3,
                            maxLines = 5
                        )

                        SpeechMicButton(
                            onResult = { transcribed -> textResponse = transcribed },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        },
        confirmButton = {
            // Only show confirm button for free-text responses
            if (clarification.options == null) {
                Button(
                    onClick = {
                        if (textResponse.isNotBlank()) {
                            onRespond(textResponse)
                        }
                    },
                    enabled = textResponse.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
