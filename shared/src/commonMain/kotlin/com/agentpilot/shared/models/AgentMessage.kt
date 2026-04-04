package com.agentpilot.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class AgentMessage {

    @Serializable
    @SerialName("agent_status_update")
    data class AgentStatusUpdate(
        val agentId: String,
        val status: AgentStatus,
        val progress: Float,       // 0.0–1.0
        val currentTask: String,
    ) : AgentMessage()

    @Serializable
    @SerialName("llm_request_capture")
    data class LlmRequestCapture(
        val requestId: String,
        val model: String,
        val prompt: String,
        val timestamp: Instant,
    ) : AgentMessage()

    @Serializable
    @SerialName("llm_response_chunk")
    data class LlmResponseChunk(
        val requestId: String,
        val token: String,
        val isComplete: Boolean,
    ) : AgentMessage()

    @Serializable
    @SerialName("clarification_request")
    data class ClarificationRequest(
        val id: String,
        val question: String,
        val context: String,
        val options: List<String>? = null,   // null = free-text, non-null = multiple choice
    ) : AgentMessage()

    @Serializable
    @SerialName("clarification_response")
    data class ClarificationResponse(
        val id: String,
        val answer: String,
        val source: InputSource,
    ) : AgentMessage()

    @Serializable
    @SerialName("code_change_proposal")
    data class CodeChangeProposal(
        val id: String,
        val filePath: String,
        val diff: String,
        val explanation: String,
    ) : AgentMessage()

    @Serializable
    @SerialName("code_change_verdict")
    data class CodeChangeVerdict(
        val id: String,
        val action: ChangeAction,
        val alternative: String? = null,   // populated when action == MODIFY
    ) : AgentMessage()

    @Serializable
    @SerialName("connection_handshake")
    data class ConnectionHandshake(
        val version: String,
        val capabilities: List<String>,
    ) : AgentMessage()

    /** Envelope broadcast by the IntelliJ plugin for every intercepted event. */
    @Serializable
    @SerialName("mcp-notification")
    data class McpNotification(
        val payload: McpPayload
    ) : AgentMessage()
}

@Serializable
data class McpPayload(
    val endpoint: String,
    val content: JsonElement = JsonObject(emptyMap()),
    val timestamp: String,
)

enum class AgentStatus { IDLE, RUNNING, WAITING_FOR_INPUT, WAITING_FOR_REVIEW, COMPLETED, FAILED }
enum class InputSource   { TEXT, VOICE }
enum class ChangeAction  { ACCEPT, REJECT, MODIFY }
