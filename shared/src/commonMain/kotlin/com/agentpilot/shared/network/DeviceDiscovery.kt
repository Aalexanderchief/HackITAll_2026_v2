package com.agentpilot.shared.network

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.writeString

private const val DISCOVERY_PORT = 27043
private const val TIMEOUT_MS = 5_000L

/**
 * Discovers the IntelliJ plugin on the local network by broadcasting [code]
 * (e.g. "JB-482-XKQ") over UDP on port 27043.
 *
 * [broadcastAddress] should be the subnet-directed broadcast (e.g. "192.168.1.255")
 * rather than "255.255.255.255" — Android WiFi drivers silently drop limited-broadcast
 * packets on many chipsets. Pass via Android's WifiManager.dhcpInfo on the Android side.
 *
 * The plugin replies with its WebSocket URL (e.g. "ws://192.168.1.5:27042").
 * Throws [kotlinx.coroutines.TimeoutCancellationException] if no reply within 5 seconds.
 */
suspend fun discoverDevice(code: String, broadcastAddress: String): String {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socket = aSocket(selectorManager).udp().bind {
        broadcast = true
    }

    return socket.use {
        val payload = Buffer().apply { writeString("DISCOVER:$code") }
        socket.send(
            Datagram(
                packet = payload,
                address = InetSocketAddress(broadcastAddress, DISCOVERY_PORT)
            )
        )
        withTimeout(TIMEOUT_MS) {
            socket.receive().packet.readString()
        }
    }
}
