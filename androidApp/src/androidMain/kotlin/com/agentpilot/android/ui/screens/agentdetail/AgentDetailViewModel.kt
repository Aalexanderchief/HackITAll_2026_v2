package com.agentpilot.android.ui.screens.agentdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentpilot.shared.data.MockDataProvider
import com.agentpilot.shared.models.AgentMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Agent Detail screen.
 * Manages timeline events for a specific agent.
 */
class AgentDetailViewModel(
    private val agentId: String
) : ViewModel() {

    // List of all events for this agent
    private val _events = MutableStateFlow<List<AgentMessage>>(emptyList())

    // Current agent status
    private val _agentStatus = MutableStateFlow<AgentMessage.AgentStatusUpdate?>(null)
    val agentStatus: StateFlow<AgentMessage.AgentStatusUpdate?> = _agentStatus.asStateFlow()

    // Map to aggregate LLM response chunks by request ID
    private val _llmResponses = MutableStateFlow<Map<String, String>>(emptyMap())

    // Combined timeline events (status updates, requests, responses, etc.)
    val timelineEvents: StateFlow<List<TimelineEvent>> = combine(
        _events,
        _llmResponses
    ) { events, responses ->
        buildTimelineEvents(events, responses)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Pending clarification request
    private val _pendingClarification = MutableStateFlow<AgentMessage.ClarificationRequest?>(null)
    val pendingClarification: StateFlow<AgentMessage.ClarificationRequest?> = _pendingClarification.asStateFlow()

    // Pending code review
    private val _pendingCodeReview = MutableStateFlow<AgentMessage.CodeChangeProposal?>(null)
    val pendingCodeReview: StateFlow<AgentMessage.CodeChangeProposal?> = _pendingCodeReview.asStateFlow()

    init {
        // Load mock events for demonstration
        loadMockEvents()
    }

    /**
     * Load mock event stream for this agent.
     */
    private fun loadMockEvents() {
        viewModelScope.launch {
            MockDataProvider.mockAgentEventStream()
                .collect { message ->
                    processMessage(message)
                }
        }
    }

    /**
     * Process incoming agent messages.
     */
    private fun processMessage(message: AgentMessage) {
        when (message) {
            is AgentMessage.AgentStatusUpdate -> {
                if (message.agentId == agentId) {
                    _agentStatus.value = message
                    _events.update { it + message }
                }
            }

            is AgentMessage.LlmRequestCapture -> {
                _events.update { it + message }
            }

            is AgentMessage.LlmResponseChunk -> {
                // Aggregate response chunks
                _llmResponses.update { current ->
                    val existingResponse = current[message.requestId] ?: ""
                    current + (message.requestId to existingResponse + message.token)
                }
                _events.update { it + message }
            }

            is AgentMessage.ClarificationRequest -> {
                _pendingClarification.value = message
                _events.update { it + message }
            }

            is AgentMessage.ClarificationResponse -> {
                _pendingClarification.value = null
                _events.update { it + message }
            }

            is AgentMessage.CodeChangeProposal -> {
                _pendingCodeReview.value = message
                _events.update { it + message }
            }

            is AgentMessage.CodeChangeVerdict -> {
                _pendingCodeReview.value = null
                _events.update { it + message }
            }

            is AgentMessage.ConnectionHandshake -> {
                _events.update { it + message }
            }

            else -> {
                // Unhandled message type — ignore silently
            }
        }
    }

    /**
     * Build timeline events from raw messages.
     */
    private fun buildTimelineEvents(
        events: List<AgentMessage>,
        llmResponses: Map<String, String>
    ): List<TimelineEvent> {
        val timeline = mutableListOf<TimelineEvent>()

        events.forEach { event ->
            when (event) {
                is AgentMessage.AgentStatusUpdate -> {
                    if (event.agentId == agentId) {
                        timeline.add(TimelineEvent.StatusUpdate(event))
                    }
                }

                is AgentMessage.LlmRequestCapture -> {
                    timeline.add(TimelineEvent.LlmRequest(event))
                }

                is AgentMessage.LlmResponseChunk -> {
                    // Only show complete responses in timeline
                    if (event.isComplete) {
                        val fullResponse = llmResponses[event.requestId] ?: ""
                        timeline.add(
                            TimelineEvent.LlmResponse(
                                requestId = event.requestId,
                                response = fullResponse,
                                isComplete = true
                            )
                        )
                    }
                }

                is AgentMessage.ClarificationRequest -> {
                    timeline.add(TimelineEvent.ClarificationRequest(event))
                }

                is AgentMessage.CodeChangeProposal -> {
                    timeline.add(TimelineEvent.CodeProposal(event))
                }

                else -> {
                    // Ignore other message types in timeline for now
                }
            }
        }

        return timeline
    }

    /**
     * Send clarification response.
     */
    fun sendClarificationResponse(answer: String) {
        val clarification = _pendingClarification.value ?: return
        val response = AgentMessage.ClarificationResponse(
            id = clarification.id,
            answer = answer,
            source = com.agentpilot.shared.models.InputSource.TEXT
        )
        processMessage(response)
    }

    /**
     * Open code review dialog.
     */
    fun openCodeReview() {
        // Code review will be handled in Phase 4
    }
}

/**
 * Sealed class representing different types of timeline events.
 */
sealed class TimelineEvent {
    data class StatusUpdate(val event: AgentMessage.AgentStatusUpdate) : TimelineEvent()
    data class LlmRequest(val event: AgentMessage.LlmRequestCapture) : TimelineEvent()
    data class LlmResponse(val requestId: String, val response: String, val isComplete: Boolean) : TimelineEvent()
    data class ClarificationRequest(val event: AgentMessage.ClarificationRequest) : TimelineEvent()
    data class CodeProposal(val event: AgentMessage.CodeChangeProposal) : TimelineEvent()
}
