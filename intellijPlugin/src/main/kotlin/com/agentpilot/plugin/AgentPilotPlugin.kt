package com.agentpilot.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class AgentPilotPlugin : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        WebSocketServer.start()
        CopilotLogWatcher.start(WebSocketServer.scope)
        SidecarBridge.start(WebSocketServer.scope, port = 27044) // Claude Desktop
        SidecarBridge.start(WebSocketServer.scope, port = 27045) // Claude Code
    }

    override fun projectClosing(project: Project) {
        WebSocketServer.stop()
        SidecarBridge.stop()
    }
}
