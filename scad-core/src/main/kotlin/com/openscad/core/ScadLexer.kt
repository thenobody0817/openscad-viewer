package com.openscad.core

/**
 * A hand-written, single-pass lexer for the OpenSCAD language.
 *
 * It is intentionally lossless: concatenating `token.text` over the whole result
 * reproduces the input byte-for-byte, so offsets can be used directly to build
 * highlighting spans over the original text.
 *
 * It is *not* a parser — it does not track nesting or validate syntax. The
 * Customizer has its own line-oriented rules (see [CustomizerParser]) because
 * OpenSCAD's customizer annotations are comment-based, not AST-based.
 */
object ScadLexer {

    private val KEYWORDS = setOf(
        "module", "function", "if", "else", "for", "let", "each",
        "true", "false", "undef", "include", "use", "return", "assert", "echo",
    )

    private val BUILTINS = setOf(
        // 2D primitives
        "circle", "square", "polygon", "text", "projection",
        // 3D primitives
        "cube", "sphere", "cylinder", "polyhedron", "surface",
        // transforms
        "translate", "rotate", "scale", "resize", "mirror", "multmatrix",
        // boolean
        "union", "difference", "intersection",
        // extrusions
        "linear_extrude", "rotate_extrude", "offset", "hull", "minkowski",
        // misc modules
        "color", "render", "children", "group", "import", "assign",
        // math / list functions
        "sin", "cos", "tan", "asin", "acos", "atan", "atan2", "abs", "ceil",
        "floor", "round", "max", "min", "pow", "sqrt", "exp", "ln", "log",
        "sign", "rands", "norm", "cross", "len", "concat", "lookup", "str",
        "chr", "ord", "search", "is_undef", "is_bool", "is_num", "is_string",
        "is_list", "is_function", "version", "parent_module",
    )

    private val TWO_CHAR_OPERATORS = setOf(
        "==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "%=", "^=",
    )

    private const val OPERATOR_CHARS = "+-*/%^=<>!&|?:."

    /** Lexes [source] into a lossless list of tokens. */
    fun lex(source: String): List<Token> {
        val tokens = ArrayList<Token>(source.length / 3 + 16)
        val n = source.length
        var i = 0
        var line = 1
        var column = 1

        fun advance(count: Int) {
            var k = 0
            while (k < count && i < n) {
                if (source[i] == '\n') {
                    line++
                    column = 1
                } else {
                    column++
                }
                i++
                k++
            }
        }

        while (i < n) {
            val startLine = line
            val startCol = column
            val c = source[i]

            when {
                c == '\n' -> {
                    val s = i
                    advance(1)
                    tokens += Token(TokenType.NEWLINE, "\n", s, i, startLine, startCol)
                }

                c == ' ' || c == '\t' || c == '\r' -> {
                    val s = i
                    while (i < n && (source[i] == ' ' || source[i] == '\t' || source[i] == '\r')) advance(1)
                    tokens += Token(TokenType.WHITESPACE, source.substring(s, i), s, i, startLine, startCol)
                }

                c == '/' && i + 1 < n && source[i + 1] == '/' -> {
                    val s = i
                    while (i < n && source[i] != '\n') advance(1)
                    tokens += Token(TokenType.COMMENT_LINE, source.substring(s, i), s, i, startLine, startCol)
                }

                c == '/' && i + 1 < n && source[i + 1] == '*' -> {
                    val s = i
                    advance(2)
                    while (i < n && !(source[i] == '*' && i + 1 < n && source[i + 1] == '/')) advance(1)
                    if (i < n) advance(2)
                    val text = source.substring(s, i)
                    val type = if (isGroupDirective(text)) TokenType.CUSTOMIZER_DIRECTIVE else TokenType.COMMENT_BLOCK
                    tokens += Token(type, text, s, i, startLine, startCol)
                }

                c == '"' -> {
                    val s = i
                    advance(1)
                    while (i < n) {
                        val ch = source[i]
                        when {
                            ch == '\\' && i + 1 < n -> { advance(2); continue }
                            ch == '"' -> { advance(1); break }
                            ch == '\n' -> break
                            else -> advance(1)
                        }
                    }
                    tokens += Token(TokenType.STRING, source.substring(s, i), s, i, startLine, startCol)
                }

                c.isDigit() || (c == '.' && i + 1 < n && source[i + 1].isDigit()) -> {
                    val s = i
                    var k = i
                    if (c == '0' && k + 1 < n && (source[k + 1] == 'x' || source[k + 1] == 'X')) {
                        k += 2
                        while (k < n && (source[k].isDigit() || source[k] in 'a'..'f' || source[k] in 'A'..'F')) k++
                    } else {
                        while (k < n && source[k].isDigit()) k++
                        if (k < n && source[k] == '.') {
                            k++
                            while (k < n && source[k].isDigit()) k++
                        }
                        if (k < n && (source[k] == 'e' || source[k] == 'E')) {
                            var k2 = k + 1
                            if (k2 < n && (source[k2] == '+' || source[k2] == '-')) k2++
                            if (k2 < n && source[k2].isDigit()) {
                                k2++
                                while (k2 < n && source[k2].isDigit()) k2++
                                k = k2
                            }
                        }
                    }
                    advance(k - i)
                    tokens += Token(TokenType.NUMBER, source.substring(s, i), s, i, startLine, startCol)
                }

                isIdentStart(c) -> {
                    val s = i
                    advance(1)
                    while (i < n && isIdentPart(source[i])) advance(1)
                    val text = source.substring(s, i)
                    val type = when (text) {
                        in KEYWORDS -> TokenType.KEYWORD
                        in BUILTINS -> TokenType.BUILTIN
                        else -> TokenType.IDENTIFIER
                    }
                    tokens += Token(type, text, s, i, startLine, startCol)
                }

                c in "(){}[],;" -> {
                    val s = i
                    advance(1)
                    tokens += Token(TokenType.PUNCTUATION, c.toString(), s, i, startLine, startCol)
                }

                c in OPERATOR_CHARS -> {
                    val s = i
                    advance(1)
                    if (i < n) {
                        val two = "" + source[s] + source[i]
                        if (two in TWO_CHAR_OPERATORS) advance(1)
                    }
                    tokens += Token(TokenType.OPERATOR, source.substring(s, i), s, i, startLine, startCol)
                }

                else -> {
                    val s = i
                    advance(1)
                    tokens += Token(TokenType.UNKNOWN, c.toString(), s, i, startLine, startCol)
                }
            }
        }

        return tokens
    }

    /** True when a block comment's payload is a Customizer section header, i.e. `[name]`. */
    fun isGroupDirective(blockCommentText: String): Boolean {
        val inner = blockCommentText
            .removePrefix("/*")
            .removeSuffix("*/")
            .trim()
        return inner.startsWith("[") && inner.endsWith("]")
    }

    private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_' || c == '$'

    private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}
