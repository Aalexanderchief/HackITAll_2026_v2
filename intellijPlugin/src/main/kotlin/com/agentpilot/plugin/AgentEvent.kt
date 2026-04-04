package com.agentpilot.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class AgentEvent {

    @Serializable
    data class McpToolCall(
        val endpoint: String,
        val content: JsonElement = JsonObject(emptyMap())
    ) : AgentEvent()

    @Serializable
    data class LlmRequest(
        val actionId: String,
        val filePath: String,
        val selectedText: String?,
        val caretLine: Int,
        val language: String?
    ) : AgentEvent()

    @Serializable
    data class IdeSignal(
        val type: String,
        val detail: String
    ) : AgentEvent()
}
