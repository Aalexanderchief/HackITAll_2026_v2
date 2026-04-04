package com.agentpilot.android

import android.app.Application
import com.agentpilot.shared.platform.AppContext

class AgentPilotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.app = this
    }
}
