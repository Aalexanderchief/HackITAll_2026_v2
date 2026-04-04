package com.agentpilot.plugin

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

object CopilotLogWatcher {

    private val fetchChatRegex = Regex("""\[fetchChat] Request \S+ at .+ finished with (\d+) status after ([\d.]+)ms""")

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val logFile = File(PathManager.getLogPath(), "idea.log")
            var lastLength = logFile.length()

            while (true) {
                delay(1_000)
                if (!logFile.exists()) continue
                val currentLength = logFile.length()
                if (currentLength <= lastLength) continue

                val newText = logFile.inputStream().use { stream ->
                    stream.skip(lastLength)
                    stream.readBytes().decodeToString()
                }
                lastLength = currentLength

                newText.lines().forEach { line ->
                    if (!line.contains("#copilot")) return@forEach
                    val match = fetchChatRegex.find(line)
                    if (match != null) {
                        val status = match.groupValues[1]
                        val ms = match.groupValues[2].toDoubleOrNull()?.let { "%.1fs".format(it / 1000) } ?: "?"
                        AgentEventBus.emit(
                            AgentEvent.LlmRequest(
                                actionId = "copilot.fetchChat",
                                filePath = "Copilot Chat",
                                selectedText = null,
                                caretLine = 0,
                                language = null
                            )
                        )
                        System.err.println("[AgentPilot] Copilot request intercepted: $status in $ms")
                    }
                }
            }
        }
    }
}
