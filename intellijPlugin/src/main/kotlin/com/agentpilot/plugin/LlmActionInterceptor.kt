package com.agentpilot.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener

class LlmActionInterceptor : AnActionListener {

    // Action ID prefixes/exact matches that indicate an AI request
    private val aiActionPrefixes = listOf(
        "AiAssistantAction",
        "InlineCompletionAction",
        "com.intellij.ml.llm",
        "junie.",
        "copilot.",
        "com.github.copilot"
    )

    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        val actionId = event.actionManager.getId(action) ?: return
        if (aiActionPrefixes.none { actionId.startsWith(it) }) return

        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val psiFile = event.getData(CommonDataKeys.PSI_FILE)

        val selectedText = editor.selectionModel
            .takeIf { it.hasSelection() }
            ?.selectedText

        val caretLine = editor.caretModel.logicalPosition.line

        AgentEventBus.emit(
            AgentEvent.LlmRequest(
                actionId = actionId,
                filePath = virtualFile?.path ?: "unknown",
                selectedText = selectedText,
                caretLine = caretLine,
                language = psiFile?.language?.id
            )
        )
    }
}
