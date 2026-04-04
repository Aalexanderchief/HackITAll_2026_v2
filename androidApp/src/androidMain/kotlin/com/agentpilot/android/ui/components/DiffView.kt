package com.agentpilot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentpilot.android.ui.theme.*

/**
 * Component for displaying code diffs with syntax highlighting.
 * Supports simple line-based diff format (+ for additions, - for removals).
 */
@Composable
fun DiffView(
    diff: String,
    modifier: Modifier = Modifier,
    maxLines: Int? = null
) {
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF263238) // Dark code background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (maxLines != null) {
                        Modifier.heightIn(max = (maxLines * 20).dp)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(scrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(12.dp)
        ) {
            diff.lines().forEach { line ->
                DiffLine(line = line)
            }
        }
    }
}

/**
 * Displays a single line of diff with appropriate coloring.
 */
@Composable
private fun DiffLine(line: String) {
    val (backgroundColor, textColor) = when {
        line.startsWith("+") -> Pair(
            DiffAddedBackgroundDark,
            DiffAddedTextDark
        )
        line.startsWith("-") -> Pair(
            DiffRemovedBackgroundDark,
            DiffRemovedTextDark
        )
        else -> Pair(
            Color.Transparent,
            Color(0xFFE0E0E0) // Default light gray for context lines
        )
    }

    Text(
        text = line,
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 2.dp, horizontal = 4.dp),
        style = CodeTypography,
        color = textColor,
        maxLines = 1
    )
}

/**
 * Enhanced diff view with line numbers and better formatting.
 * Use this for the code review sheet in Phase 4.
 */
@Composable
fun EnhancedDiffView(
    diff: String,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true
) {
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF263238)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(12.dp)
        ) {
            diff.lines().forEachIndexed { index, line ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (showLineNumbers) {
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier
                                .width(40.dp)
                                .padding(end = 8.dp),
                            style = CodeTypography.copy(fontSize = 11.sp),
                            color = Color(0xFF757575) // Gray for line numbers
                        )
                    }

                    DiffLine(line = line)
                }
            }
        }
    }
}

/**
 * Compact diff view for inline display in event cards.
 */
@Composable
fun CompactDiffView(
    diff: String,
    modifier: Modifier = Modifier
) {
    DiffView(
        diff = diff,
        modifier = modifier,
        maxLines = 10
    )
}
