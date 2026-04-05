package com.agentpilot.shared.platform

/**
 * Modular service for notifications and haptic feedback.
 *
 * Usage:
 *   - Call [notify] to show a system-level notification with [title] and [message].
 *   - Call [vibrate] to trigger a short vibration/haptic feedback.
 *
 * Android: Uses NotificationManager and Vibrator.
 * JVM/Desktop: Uses TrayIcon for notifications (Windows-native) if possible.
 */
expect class NotificationService() {
    /** Shows a system-level notification. */
    fun notify(title: String, message: String)
    
    /** Triggers haptic feedback/vibration. */
    fun vibrate()
}
