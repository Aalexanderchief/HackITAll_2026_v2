package com.agentpilot.android.ui

/**
 * Navigation routes for the app.
 */
object Routes {
    const val AGENT_LIST = "agent_list"
    const val AGENT_DETAIL = "agent_detail/{agentId}"

    fun agentDetail(agentId: String) = "agent_detail/$agentId"
}
