package com.openscad.core

/**
 * Lexical token categories produced by [ScadLexer].
 *
 * These are deliberately syntax-highlighting oriented (they map 1:1 onto spans the
 * editor wants to color) while still being precise enough for the Customizer parser,
 * which re-reads raw lines with its own line-oriented rules.
 */
enum class TokenType {
    WHITESPACE,
    NEWLINE,

    /** `// ...` to end of line. */
    COMMENT_LINE,

    /** `/* ... */` that is not a Customizer group header. */
    COMMENT_BLOCK,

    /** `/* [Group Name] */` — an OpenSCAD Customizer section header. */
    CUSTOMIZER_DIRECTIVE,

    /** A double-quoted string literal, including the quotes. */
    STRING,

    /** Numeric literal: decimal, fraction, exponent, or hex. */
    NUMBER,

    /** Language keyword such as `module`, `if`, `true`. */
    KEYWORD,

    /** Built-in module or function such as `cube`, `difference`, `sin`. */
    BUILTIN,

    /** Anything else that starts with a letter, `_`, or `$`. */
    IDENTIFIER,

    /** Operator such as `=`, `+`, `==`. */
    OPERATOR,

    /** Bracketing/punctuation such as `(`, `[`, `;`. */
    PUNCTUATION,

    UNKNOWN,
}

/**
 * A single lexed token.
 *
 * [start] and [end] are half-open character offsets into the source. [line] and
 * [column] are 1-based and point at the token's first character.
 */
data class Token(
    val type: TokenType,
    val text: String,
    val start: Int,
    val end: Int,
    val line: Int,
    val column: Int,
) {
    val length: Int get() = end - start
}
