package com.agentpilot.shared.platform

import android.app.Application

/**
 * Holds a reference to the Application context for use by shared-module Android actuals
 * that need a Context but cannot receive one through the KMP expect constructor.
 *
 * Initialised once in AgentPilotApplication.onCreate() before any other code runs.
 * Safe to read from any thread after that point.
 */
object AppContext {
    lateinit var app: Application
}
