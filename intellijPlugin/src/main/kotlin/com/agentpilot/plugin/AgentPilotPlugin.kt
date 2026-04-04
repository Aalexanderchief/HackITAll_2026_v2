package com.agentpilot.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class AgentPilotPlugin : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        WebSocketServer.start()
    }

    override fun projectClosing(project: Project) {
        WebSocketServer.stop()
    }
}
