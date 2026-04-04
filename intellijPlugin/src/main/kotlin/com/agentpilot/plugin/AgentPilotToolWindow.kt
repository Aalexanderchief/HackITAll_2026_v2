package com.agentpilot.plugin

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.*
import java.awt.image.BufferedImage
import java.net.Inet4Address
import java.net.NetworkInterface
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.*

class AgentPilotToolWindow : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = buildPanel()
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun buildPanel(): JPanel {
        val wsUrl = "ws://${localIp()}:27042"

        val statusDot = JLabel("●").apply {
            foreground = if (WebSocketServer.isRunning) Color(0x4CAF50) else Color(0xF44336)
            font = font.deriveFont(20f)
        }
        val urlLabel = JLabel(wsUrl).apply {
            font = font.deriveFont(12f)
        }

        val qrImage = generateQr(wsUrl, 180)
        val qrLabel = JLabel(ImageIcon(qrImage))

        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
            add(statusDot)
            add(urlLabel)
        }

        val logArea = JTextArea(12, 40).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        }
        val logScroll = JScrollPane(logArea)

        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        fun appendLog(text: String) {
            SwingUtilities.invokeLater {
                logArea.append("[${LocalTime.now().format(formatter)}] $text\n")
                logArea.caretPosition = logArea.document.length
            }
        }

        val testButton = JButton("Send test event").apply {
            addActionListener {
                AgentEventBus.emit(AgentEvent.McpToolCall("test/ping"))
                appendLog("test/ping → emitted to bus (server: ${if (WebSocketServer.isRunning) "running" else "stopped"})")
            }
        }

        val bottomPanel = JPanel(BorderLayout(4, 4)).apply {
            add(logScroll, BorderLayout.CENTER)
            add(testButton, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(topPanel, BorderLayout.NORTH)
            add(qrLabel, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }
    }

    private fun generateQr(content: String, size: Int): BufferedImage {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        return MatrixToImageWriter.toBufferedImage(matrix)
    }

    private fun localIp(): String =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress ?: "localhost"
}
