package com.agentpilot.shared.data

import com.agentpilot.shared.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

/**
 * Mock data provider for development and testing.
 * Simulates real-time agent events without requiring WebSocket connection.
 */
object MockDataProvider {

    /**
     * Provides a Flow of AgentMessage events simulating a complete agent workflow.
     * Useful for testing UI components before WebSocket integration.
     */
    fun mockAgentEventStream(): Flow<AgentMessage> = flow {
        // Initial handshake
        emit(
            AgentMessage.ConnectionHandshake(
                version = "1.0.0",
                capabilities = listOf("clarification", "code_review", "llm_streaming")
            )
        )
        delay(500)

        // Agent starts working
        emit(
            AgentMessage.AgentStatusUpdate(
                agentId = "agent-001",
                status = AgentStatus.RUNNING,
                progress = 0.0f,
                currentTask = "Analyzing codebase structure"
            )
        )
        delay(2000)

        // LLM request captured
        emit(
            AgentMessage.LlmRequestCapture(
                requestId = "req-001",
                model = "claude-sonnet-4-5",
                prompt = "Analyze the Kotlin Multiplatform project structure and identify networking components",
                timestamp = Clock.System.now()
            )
        )
        delay(500)

        // Stream LLM response chunks
        val responseTokens = listOf(
            "The", " project", " uses", " K", "tor", " 3", ".", "4", " for",
            " networking", ".", " Current", " implementation", " is", " in",
            " common", "Main", "."
        )
        responseTokens.forEachIndexed { index, token ->
            emit(
                AgentMessage.LlmResponseChunk(
                    requestId = "req-001",
                    token = token,
                    isComplete = index == responseTokens.lastIndex
                )
            )
            delay(100)
        }

        // Agent continues
        emit(
            AgentMessage.AgentStatusUpdate(
                agentId = "agent-001",
                status = AgentStatus.RUNNING,
                progress = 0.3f,
                currentTask = "Planning refactoring strategy"
            )
        )
        delay(2000)

        // Agent needs clarification
        emit(
            AgentMessage.AgentStatusUpdate(
                agentId = "agent-001",
                status = AgentStatus.WAITING_FOR_INPUT,
                progress = 0.4f,
                currentTask = "Waiting for architectural decision"
            )
        )
        delay(500)

        emit(
            AgentMessage.ClarificationRequest(
                id = "clarif-001",
                question = "Should we use Ktor client plugins for authentication or implement custom interceptors?",
                context = "The project needs OAuth2 token handling. Ktor provides built-in auth plugin, but custom interceptors offer more control.",
                options = listOf(
                    "Use Ktor Auth plugin (recommended)",
                    "Custom interceptors",
                    "Hybrid approach"
                )
            )
        )

        // Simulate user response after 5 seconds
        delay(5000)
        emit(
            AgentMessage.ClarificationResponse(
                id = "clarif-001",
                answer = "Use Ktor Auth plugin (recommended)",
                source = InputSource.TEXT
            )
        )

        // Agent resumes
        emit(
            AgentMessage.AgentStatusUpdate(
                agentId = "agent-001",
                status = AgentStatus.RUNNING,
                progress = 0.6f,
                currentTask = "Implementing Ktor Auth plugin integration"
            )
        )
        delay(3000)

        // Code change proposal
        emit(
            AgentMessage.AgentStatusUpdate(
                agentId = "agent-001",
                status = AgentStatus.WAITING_FOR_REVIEW,
                progress = 0.8f,
                currentTask = "Awaiting code review approval"
            )
        )
        delay(500)

        emit(
            AgentMessage.CodeChangeProposal(
                id = "code-001",
                filePath = "shared/src/commonMain/kotlin/com/agentpilot/shared/network/HttpClientFactory.kt",
                diff = """
--- a/shared/src/commonMain/kotlin/com/agentpilot/shared/network/HttpClientFactory.kt
+++ b/shared/src/commonMain/kotlin/com/agentpilot/shared/network/HttpClientFactory.kt
@@ -1,10 +1,15 @@
 package com.agentpilot.shared.network

 import io.ktor.client.*
+import io.ktor.client.plugins.auth.*
+import io.ktor.client.plugins.auth.providers.*
 import io.ktor.client.plugins.contentnegotiation.*
 import io.ktor.serialization.kotlinx.json.*

 fun createHttpClient(): HttpClient = HttpClient {
+    install(Auth) {
+        bearer {
+            loadTokens { /* Token provider logic */ }
+        }
+    }
     install(ContentNegotiation) {
         json()
     }
 }
                """.trimIndent(),
                explanation = "Added Ktor Auth plugin with Bearer token support. This provides automatic token refresh and retry logic without custom interceptors."
            )
        )

        // Simulate review approval
        delay(4000)
        emit(
            AgentMessage.CodeChangeVerdict(
                id = "code-001",
                action = ChangeAction.ACCEPT,
                alternative = null
            )
        )

        // Agent completes
        emit(
            AgentMessage.AgentStatusUpdate(
                agentId = "agent-001",
                status = AgentStatus.COMPLETED,
                progress = 1.0f,
                currentTask = "Refactoring completed successfully"
            )
        )
    }

    /**
     * Provides static sample data for multiple agents in various states.
     * Useful for testing list views.
     */
    fun sampleAgentStates(): List<AgentMessage.AgentStatusUpdate> = listOf(
        AgentMessage.AgentStatusUpdate(
            agentId = "agent-001",
            status = AgentStatus.RUNNING,
            progress = 0.65f,
            currentTask = "Refactoring networking layer to Ktor 3"
        ),
        AgentMessage.AgentStatusUpdate(
            agentId = "agent-002",
            status = AgentStatus.WAITING_FOR_INPUT,
            progress = 0.4f,
            currentTask = "Awaiting clarification on database schema"
        ),
        AgentMessage.AgentStatusUpdate(
            agentId = "agent-003",
            status = AgentStatus.WAITING_FOR_REVIEW,
            progress = 0.9f,
            currentTask = "Code review pending for authentication module"
        ),
        AgentMessage.AgentStatusUpdate(
            agentId = "agent-004",
            status = AgentStatus.COMPLETED,
            progress = 1.0f,
            currentTask = "Successfully added dark mode support"
        ),
        AgentMessage.AgentStatusUpdate(
            agentId = "agent-005",
            status = AgentStatus.IDLE,
            progress = 0.0f,
            currentTask = "Ready to accept new tasks"
        )
    )

    /**
     * Sample clarification requests for testing clarification UI.
     */
    fun sampleClarificationRequests(): List<AgentMessage.ClarificationRequest> = listOf(
        AgentMessage.ClarificationRequest(
            id = "clarif-001",
            question = "Should we use Ktor client plugins for authentication or implement custom interceptors?",
            context = "The project needs OAuth2 token handling. Ktor provides built-in auth plugin, but custom interceptors offer more control.",
            options = listOf(
                "Use Ktor Auth plugin (recommended)",
                "Custom interceptors",
                "Hybrid approach"
            )
        ),
        AgentMessage.ClarificationRequest(
            id = "clarif-002",
            question = "What should be the minimum Android SDK version?",
            context = "Current target is API 26 (Android 8.0). Lowering to API 24 would support older devices but require additional compatibility handling.",
            options = null  // Free-text response
        )
    )

    /**
     * Sample code change proposals for testing review UI.
     */
    fun sampleCodeProposals(): List<AgentMessage.CodeChangeProposal> = listOf(
        AgentMessage.CodeChangeProposal(
            id = "code-001",
            filePath = "shared/src/commonMain/kotlin/com/agentpilot/shared/network/HttpClientFactory.kt",
            diff = """
--- a/shared/src/commonMain/kotlin/com/agentpilot/shared/network/HttpClientFactory.kt
+++ b/shared/src/commonMain/kotlin/com/agentpilot/shared/network/HttpClientFactory.kt
@@ -1,6 +1,10 @@
 package com.agentpilot.shared.network

 import io.ktor.client.*
+import io.ktor.client.plugins.auth.*
+import io.ktor.client.plugins.auth.providers.*

 fun createHttpClient(): HttpClient = HttpClient {
+    install(Auth) {
+        bearer { loadTokens { /* Token provider */ } }
+    }
 }
            """.trimIndent(),
            explanation = "Added Ktor Auth plugin with Bearer token support for automatic authentication handling."
        )
    )
}
