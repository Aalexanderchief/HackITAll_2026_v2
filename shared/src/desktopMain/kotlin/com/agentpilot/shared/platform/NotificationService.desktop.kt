package com.agentpilot.shared.platform

import java.awt.*
import java.util.concurrent.Executors

/**
 * JVM/Desktop implementation of NotificationService.
 * Provides Windows-native notifications via PowerShell and a cross-platform TrayIcon fallback.
 */
actual class NotificationService {

    private val executor = Executors.newSingleThreadExecutor()
    private val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)

    actual fun notify(title: String, message: String) {
        if (isWindows) {
            notifyWindows(title, message)
        } else {
            notifyGeneric(title, message)
        }
    }

    actual fun vibrate() {
        // Desktop hardware doesn't vibrate; use a system beep as a haptic-equivalent auditory signal.
        try {
            Toolkit.getDefaultToolkit().beep()
        } catch (e: Exception) {
            System.err.println("[AgentPilot] Desktop beep failed: ${e.message}")
        }
    }

    private fun notifyWindows(title: String, message: String) {
        executor.execute {
            try {
                // Modern Windows notification via PowerShell to avoid legacy TrayIcon quirks
                val escapedTitle = title.replace("\"", "`\"")
                val escapedMessage = message.replace("\"", "`\"")
                val command = "powershell.exe -Command \"& { " +
                        "Add-Type -AssemblyName System.Windows.Forms; " +
                        "Add-Type -AssemblyName System.Drawing; " +
                        "\$notify = New-Object System.Windows.Forms.NotifyIcon; " +
                        "\$notify.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon((Get-Process -Id \$pid).MainModule.FileName); " +
                        "\$notify.BalloonTipTitle = \\\"$escapedTitle\\\"; " +
                        "\$notify.BalloonTipText = \\\"$escapedMessage\\\"; " +
                        "\$notify.Visible = \$true; " +
                        "\$notify.ShowBalloonTip(5000); " +
                        "}\""
                
                Runtime.getRuntime().exec(command).waitFor()
            } catch (e: Exception) {
                System.err.println("[AgentPilot] Windows PowerShell notification failed: ${e.message}")
                notifyGeneric(title, message)
            }
        }
    }

    private fun notifyGeneric(title: String, message: String) {
        if (!SystemTray.isSupported()) {
            System.err.println("[AgentPilot] SystemTray not supported, printing to stderr: $title - $message")
            return
        }

        executor.execute {
            try {
                val tray = SystemTray.getSystemTray()
                val image = Toolkit.getDefaultToolkit().createImage("") // Dummy image
                val trayIcon = TrayIcon(image, "AgentPilot")
                
                trayIcon.isImageAutoSize = true
                tray.add(trayIcon)
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO)
                
                // Remove the icon after a delay so it doesn't clutter the tray
                Thread.sleep(6000)
                tray.remove(trayIcon)
            } catch (e: Exception) {
                System.err.println("[AgentPilot] Generic tray notification failed: ${e.message}")
            }
        }
    }
}
