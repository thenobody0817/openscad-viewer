package com.openscad.viewer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.openscad.core.ScadLexer
import com.openscad.core.TokenType

/**
 * Token palette for OpenSCAD syntax highlighting.
 *
 * Colors are intentionally independent of the Material scheme: code editors
 * read better with a fixed palette than with a shifting tonal one.
 */
object ScadPalette {
    val comment = Color(0xFF6A9955)
    val directive = Color(0xFF7C9CFF)
    val keyword = Color(0xFFC586C0)
    val builtin = Color(0xFF569CD6)
    val number = Color(0xFFB5CEA8)
    val string = Color(0xFFCE9178)
    val operator = Color(0xFFD4D4D4)
    val plain = Color(0xFFE6E1E5)
}

/**
 * Turns OpenSCAD source into a styled [AnnotatedString] using [ScadLexer].
 *
 * The transformation is length-preserving (token text is emitted verbatim), so
 * the result can also be used from a `VisualTransformation` with an identity
 * offset mapping once the editor becomes editable.
 */
fun highlightScad(source: String): AnnotatedString = buildAnnotatedString {
    for (token in ScadLexer.lex(source)) {
        val color = when (token.type) {
            TokenType.COMMENT_LINE, TokenType.COMMENT_BLOCK -> ScadPalette.comment
            TokenType.CUSTOMIZER_DIRECTIVE -> ScadPalette.directive
            TokenType.KEYWORD -> ScadPalette.keyword
            TokenType.BUILTIN -> ScadPalette.builtin
            TokenType.NUMBER -> ScadPalette.number
            TokenType.STRING -> ScadPalette.string
            TokenType.OPERATOR, TokenType.PUNCTUATION -> ScadPalette.operator
            else -> ScadPalette.plain
        }
        pushStyle(SpanStyle(color = color))
        append(token.text)
        pop()
    }
}
