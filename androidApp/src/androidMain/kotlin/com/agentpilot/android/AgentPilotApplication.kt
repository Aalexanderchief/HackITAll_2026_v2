package com.agentpilot.android

import android.app.Application
import com.agentpilot.shared.network.ConnectionViewModel
import com.agentpilot.shared.platform.AppContext

class AgentPilotApplication : Application() {

    companion object {
        lateinit var connectionViewModel: ConnectionViewModel
            private set
    }

    override fun onCreate() {
        super.onCreate()
        AppContext.app = this
        connectionViewModel = ConnectionViewModel()
    }
}
