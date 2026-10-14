package com.openscad.viewer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Read-only, syntax-highlighted view of the source.
 *
 * The next step replaces the [Text] with a `BasicTextField` whose
 * `VisualTransformation` returns this same [highlightScad] result — the offset
 * mapping is the identity because the transformation is length-preserving.
 */
@Composable
fun CodePanel(
    source: String,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(source) { highlightScad(source) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(text = annotated, style = MonoTextStyle)
    }
}

@Composable
fun ConsolePanel(
    lines: List<ConsoleLine>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (line in lines) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = symbol(line.level), style = MonoTextStyle, color = colorFor(line.level))
                Text(text = line.text, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun symbol(level: Level): String = when (level) {
    Level.INFO -> "i"
    Level.OK -> "✔"
    Level.WARN -> "!"
    Level.ERROR -> "✖"
}

private fun colorFor(level: Level): Color = when (level) {
    Level.INFO -> Color(0xFF89B4FA)
    Level.OK -> Color(0xFFA6E3A1)
    Level.WARN -> Color(0xFFF9E2AF)
    Level.ERROR -> Color(0xFFF38BA8)
}
