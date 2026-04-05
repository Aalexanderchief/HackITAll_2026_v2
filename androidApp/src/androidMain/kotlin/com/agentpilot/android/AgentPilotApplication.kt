package com.agentpilot.android

import android.app.Application
import com.agentpilot.shared.platform.AppContext

class AgentPilotApplication : Application() {

    companion object {
        lateinit var instance: AgentPilotApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppContext.app = this
    }
}
