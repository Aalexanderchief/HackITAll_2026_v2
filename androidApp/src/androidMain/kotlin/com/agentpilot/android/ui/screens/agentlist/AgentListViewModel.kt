package com.agentpilot.android.ui.screens.agentlist

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentpilot.android.AgentPilotApplication
import com.agentpilot.shared.models.AgentMessage
import com.agentpilot.shared.models.AgentStatus
import com.agentpilot.shared.models.InputSource
import com.agentpilot.shared.network.ConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AgentListViewModel : ViewModel() {

    private val connectionViewModel = AgentPilotApplication.connectionViewModel

    val connectionState: StateFlow<ConnectionState> = connectionViewModel.connectionState

    private val _filterStatus = MutableStateFlow<AgentStatus?>(null)
    val filterStatus: StateFlow<AgentStatus?> = _filterStatus.asStateFlow()

    val agents: StateFlow<List<AgentMessage.AgentStatusUpdate>> = connectionViewModel.agentStatuses
        .map { it.values.sortedByDescending { agent -> agent.agentId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredAgents: StateFlow<List<AgentMessage.AgentStatusUpdate>> = combine(
        agents,
        filterStatus
    ) { agentList, filter ->
        if (filter == null) agentList else agentList.filter { it.status == filter }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeClarification: StateFlow<AgentMessage.ClarificationRequest?> =
        connectionViewModel.activeClarification

    /**
     * Accepts either a raw IP address ("10.0.0.5") or a discovery token ("JB-482-XKQ").
     * For tokens, computes the subnet-directed broadcast address from the current WiFi
     * connection instead of using 255.255.255.255, which Android silently drops on most
     * WiFi chipsets.
     */
    fun connect(input: String) {
        val trimmed = input.trim()
        if (trimmed.startsWith("JB-", ignoreCase = true)) {
            val broadcastAddress = wifiBroadcastAddress()
            connectionViewModel.connectViaCode(trimmed.uppercase(), broadcastAddress)
        } else {
            connectionViewModel.connectViaIp(trimmed)
        }
    }

    /**
     * Returns the subnet-directed broadcast address for the active WiFi network
     * (e.g. "192.168.1.255"). Falls back to "255.255.255.255" if WiFi info is unavailable.
     *
     * WifiManager.dhcpInfo stores IP and netmask as little-endian ints.
     * broadcast = (ip & mask) | ~mask, then reinterpreted as bytes in little-endian order.
     */
    private fun wifiBroadcastAddress(): String {
        return try {
            val wifi = AgentPilotApplication.instance
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifi.dhcpInfo
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val bytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(broadcast)
                .array()
            InetAddress.getByAddress(bytes).hostAddress ?: "255.255.255.255"
        } catch (e: Exception) {
            "255.255.255.255"
        }
    }

    fun disconnect() = connectionViewModel.disconnect()

    fun setFilter(status: AgentStatus?) { _filterStatus.value = status }

    fun clearFilter() { _filterStatus.value = null }

    fun respondToClarification(id: String, approved: Boolean) {
        viewModelScope.launch {
            connectionViewModel.send(
                AgentMessage.ClarificationResponse(
                    id = id,
                    answer = if (approved) "approved" else "rejected",
                    source = InputSource.TEXT
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionViewModel.onCleared()
    }
}
