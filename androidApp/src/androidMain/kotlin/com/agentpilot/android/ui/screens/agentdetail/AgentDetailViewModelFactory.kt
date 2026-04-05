package com.agentpilot.android.ui.screens.agentdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating AgentDetailViewModel with agentId parameter.
 */
class AgentDetailViewModelFactory(
    private val agentId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgentDetailViewModel::class.java)) {
            return AgentDetailViewModel(agentId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
