package com.agentpilot.plugin

import com.intellij.openapi.project.ProjectManager
import java.io.File

object DiffGenerator {

    /**
     * Returns the current on-disk content of [pathInProject] relative to the open project root.
     * Returns empty string if the file doesn't exist (new file case).
     */
    fun readCurrentContent(pathInProject: String): String {
        val basePath = ProjectManager.getInstance().openProjects
            .firstOrNull()?.basePath ?: return ""
        return File(basePath, pathInProject).takeIf { it.exists() }?.readText() ?: ""
    }

    /**
     * Produces a simple unified-style diff between [before] and [after] for [filePath].
     * Uses line-level Myers-style diff: context lines are omitted for brevity on mobile.
     */
    fun generate(filePath: String, before: String, after: String): String {
        if (before == after) return "(no changes)"
        if (before.isEmpty()) {
            // New file — every line is an addition
            return buildString {
                appendLine("--- /dev/null")
                appendLine("+++ b/$filePath")
                appendLine("@@ -0,0 +1,${after.lines().size} @@")
                after.lines().forEach { appendLine("+$it") }
            }
        }

        val beforeLines = before.lines()
        val afterLines  = after.lines()
        val lcs = lcs(beforeLines, afterLines)

        return buildString {
            appendLine("--- a/$filePath")
            appendLine("+++ b/$filePath")

            var bi = 0; var ai = 0; var li = 0
            val chunks = mutableListOf<Triple<List<String>, List<String>, Int>>() // removed, added, atLine

            while (bi < beforeLines.size || ai < afterLines.size) {
                if (li < lcs.size && bi < beforeLines.size && beforeLines[bi] == lcs[li]
                    && ai < afterLines.size && afterLines[ai] == lcs[li]) {
                    bi++; ai++; li++
                } else {
                    val removedStart = bi
                    val removed = mutableListOf<String>()
                    val added   = mutableListOf<String>()
                    while (bi < beforeLines.size && (li >= lcs.size || beforeLines[bi] != lcs[li]))
                        removed.add(beforeLines[bi++])
                    while (ai < afterLines.size && (li >= lcs.size || afterLines[ai] != lcs[li]))
                        added.add(afterLines[ai++])
                    if (removed.isNotEmpty() || added.isNotEmpty())
                        chunks.add(Triple(removed, added, removedStart + 1))
                }
            }

            chunks.forEach { (removed, added, atLine) ->
                appendLine("@@ -$atLine,${removed.size} +$atLine,${added.size} @@")
                removed.forEach { appendLine("-$it") }
                added.forEach   { appendLine("+$it") }
            }
        }.trimEnd()
    }

    /** Longest Common Subsequence on string lists. */
    private fun lcs(a: List<String>, b: List<String>): List<String> {
        val m = a.size; val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) for (j in 1..n)
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1] + 1
                       else maxOf(dp[i-1][j], dp[i][j-1])
        val result = mutableListOf<String>()
        var i = m; var j = n
        while (i > 0 && j > 0) {
            when {
                a[i-1] == b[j-1] -> { result.add(0, a[i-1]); i--; j-- }
                dp[i-1][j] > dp[i][j-1] -> i--
                else -> j--
            }
        }
        return result
    }
}
