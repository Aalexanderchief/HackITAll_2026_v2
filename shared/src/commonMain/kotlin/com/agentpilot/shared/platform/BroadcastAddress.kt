package com.agentpilot.shared.platform

/**
 * Returns the best broadcast address for UDP device discovery.
 *
 * Android: subnet-directed broadcast from WifiManager (e.g. "192.168.1.255").
 *          Limited broadcast (255.255.255.255) is silently dropped by many WiFi chipsets.
 * Desktop: "255.255.255.255" works fine on standard OS network stacks.
 */
expect fun wifiBroadcastAddress(): String
