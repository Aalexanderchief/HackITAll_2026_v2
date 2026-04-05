package com.agentpilot.plugin

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.swing.*

class AgentPilotToolWindow : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = buildPanel()
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun buildPanel(): JPanel {
        val ip    = WebSocketServer.localIp()
        val wsUrl = "ws://$ip:27042"
        val token = WebSocketServer.token

        // --- Status row ---
        val statusDot = JLabel("●").apply {
            foreground = if (WebSocketServer.isRunning) Color(0x4CAF50) else Color(0xF44336)
            font = font.deriveFont(18f)
        }
        val urlLabel = JLabel(wsUrl).apply { font = font.deriveFont(11f) }
        val statusRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(statusDot); add(urlLabel)
        }

        // --- Token row ---
        val tokenLabel = JLabel(token).apply {
            font = Font(Font.MONOSPACED, Font.BOLD, 20)
            foreground = Color(0x1976D2)
        }
        val tokenCopyBtn = JButton("Copy").apply {
            font = font.deriveFont(11f)
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(java.awt.datatransfer.StringSelection(token), null)
            }
        }
        val tokenRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4)).apply {
            add(JLabel("Token:").apply { font = font.deriveFont(Font.BOLD, 12f) })
            add(tokenLabel)
            add(tokenCopyBtn)
        }

        // --- QR code (encodes the WS URL — phones scan this to connect directly) ---
        val qrImage = generateQr(wsUrl, 180)
        val qrLabel = JLabel(ImageIcon(qrImage))
        val qrHint  = JLabel("Scan QR  —or—  enter token on phone").apply {
            font = font.deriveFont(Font.ITALIC, 10f)
            foreground = Color.GRAY
        }
        val qrPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(Box.createVerticalStrut(4))
            qrLabel.alignmentX = Component.CENTER_ALIGNMENT
            qrHint.alignmentX  = Component.CENTER_ALIGNMENT
            add(qrLabel)
            add(qrHint)
            add(Box.createVerticalStrut(4))
        }

        // --- Log area ---
        val logArea = JTextArea(8, 40).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        }
        val logScroll = JScrollPane(logArea)
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
        fun appendLog(text: String) = SwingUtilities.invokeLater {
            logArea.append("[${LocalTime.now().format(fmt)}] $text\n")
            logArea.caretPosition = logArea.document.length
        }

        // --- Control buttons ---
        val pingBtn = JButton("Ping (status)").apply {
            addActionListener {
                WebSocketServer.broadcastAgentStatus("junie", "RUNNING", "Analysing code…")
                appendLog("agent_status_update → RUNNING")
            }
        }
        val clarBtn = JButton("Test Clarification").apply {
            addActionListener {
                val id = UUID.randomUUID().toString().take(8)
                WebSocketServer.broadcastClarificationRequest(
                    id      = id,
                    question = "Should I use suspend functions or callbacks?",
                    context  = "Networking layer in commonMain"
                )
                appendLog("clarification_request → id=$id")
            }
        }
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(pingBtn); add(clarBtn)
        }

        // --- LLM simulation ---
        val promptField = JTextField("Refactor networking to Ktor 3 and add iOS support").apply { columns = 32 }
        val modelCombo  = JComboBox<String>(arrayOf("junie", "ai-assistant", "copilot")).apply { selectedItem = "junie" }
        val sendLlmBtn  = JButton("Send LLM Request →").apply {
            addActionListener {
                val prompt = promptField.text.trim()
                if (prompt.isEmpty()) { appendLog("⚠ Enter a prompt first"); return@addActionListener }
                val rid  = UUID.randomUUID().toString().take(8)
                val json = buildJsonObject {
                    put("type", "llm_request_capture")
                    put("requestId", rid)
                    put("model", modelCombo.selectedItem.toString())
                    put("prompt", prompt)
                    put("timestamp", Instant.now().toString())
                }
                WebSocketServer.broadcastRaw(Json.encodeToString(json))
                appendLog("llm_request_capture → ${prompt.take(40)}")
            }
        }
        val llmRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Prompt:")); add(promptField); add(modelCombo); add(sendLlmBtn)
        }

        val controlPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(btnRow)
            add(Box.createVerticalStrut(4))
            add(llmRow)
            add(Box.createVerticalStrut(4))
            add(logScroll)
        }

        return JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(JPanel(BorderLayout()).apply {
                add(statusRow, BorderLayout.NORTH)
                add(tokenRow,  BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(qrPanel,      BorderLayout.CENTER)
            add(controlPanel, BorderLayout.SOUTH)
        }
    }

    private fun generateQr(content: String, size: Int): BufferedImage {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        return MatrixToImageWriter.toBufferedImage(matrix)
    }
}
