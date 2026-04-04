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
private const val BROADCAST_ADDRESS = "255.255.255.255"
private const val TIMEOUT_MS = 5_000L

/**
 * Discovers the IntelliJ plugin on the local network by broadcasting [code]
 * (e.g. "A3F-92B") over UDP on port 27043.
 *
 * The plugin must listen on that port. If [code] matches its displayed code it
 * replies with its WebSocket URL in plain text (e.g. "ws://192.168.1.5:27042").
 *
 * Returns the WebSocket URL, or throws [kotlinx.coroutines.TimeoutCancellationException]
 * if no device responds within 5 seconds.
 *
 * Uses the kotlinx-io Buffer API (Ktor 3.x) — kotlinx-io is a transitive dep of ktor-network.
 */
suspend fun discoverDevice(code: String): String {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socket = aSocket(selectorManager).udp().bind()

    return socket.use {
        val payload = Buffer().apply { writeString("DISCOVER:$code") }
        socket.send(
            Datagram(
                packet = payload,
                address = InetSocketAddress(BROADCAST_ADDRESS, DISCOVERY_PORT)
            )
        )
        withTimeout(TIMEOUT_MS) {
            socket.receive().packet.readString()
        }
    }
}
