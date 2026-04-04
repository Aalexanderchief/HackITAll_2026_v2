package com.agentpilot.plugin

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AgentEventBus {

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    /**
     * Non-suspending emit — safe to call from the EDT or any thread.
     * Returns false only if the buffer is full (64 events), which never
     * happens in normal IDE usage.
     */
    fun emit(event: AgentEvent): Boolean = _events.tryEmit(event)
}
