package com.agentpilot.shared.platform

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual fun wifiBroadcastAddress(): String {
    return try {
        val wifi = AppContext.app.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
