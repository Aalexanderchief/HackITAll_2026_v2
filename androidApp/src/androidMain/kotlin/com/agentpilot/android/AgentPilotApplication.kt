package com.agentpilot.android

import android.app.Application
import com.agentpilot.shared.network.ConnectionViewModel
import com.agentpilot.shared.platform.AppContext

class AgentPilotApplication : Application() {

    companion object {
        lateinit var instance: AgentPilotApplication
            private set
        lateinit var connectionViewModel: ConnectionViewModel
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppContext.app = this
        connectionViewModel = ConnectionViewModel()
    }
}
