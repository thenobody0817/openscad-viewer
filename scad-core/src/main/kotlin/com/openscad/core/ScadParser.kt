package com.openscad.core

/**
 * Recursive-descent parser for the subset of OpenSCAD that matters for viewing
 * printable models: primitives, transforms, booleans, modules, functions, `for`,
 * `if`, `let`, assignments and the usual expression grammar.
 *
 * Anything unrecognised is skipped rather than failing the whole parse, so a
 * partially supported file still renders the parts we understand.
 */
class ScadParser(private val tokens: List<Token>) {

    private var pos = 0

    companion object {
        private val SKIPPED = setOf(
            TokenType.WHITESPACE,
            TokenType.NEWLINE,
            TokenType.COMMENT_LINE,
            TokenType.COMMENT_BLOCK,
            TokenType.CUSTOMIZER_DIRECTIVE,
        )

        fun parse(source: String): Stmt {
            val significant = ScadLexer.lex(source).filter { it.type !in SKIPPED }
            return ScadParser(significant).parseProgram()
        }
    }

    // ---- token helpers -------------------------------------------------------

    private fun atEnd() = pos >= tokens.size

    private fun peek(offset: Int = 0): Token? = tokens.getOrNull(pos + offset)

    private fun text(offset: Int = 0): String? = peek(offset)?.text

    private fun advance(): Token? = tokens.getOrNull(pos++)

    private fun match(value: String): Boolean {
        if (text() == value) {
            pos++
            return true
        }
        return false
    }

    private fun matchAny(vararg values: String): Boolean = values.any { match(it) }

    private fun isName(token: Token?): Boolean = when (token?.type) {
        TokenType.IDENTIFIER, TokenType.BUILTIN -> true
        TokenType.KEYWORD -> token.text == "echo" || token.text == "assert"
        else -> false
    }

    // ---- statements ----------------------------------------------------------

    private fun parseProgram(): Stmt {
        val statements = ArrayList<Stmt>()
        while (!atEnd()) {
            val before = pos
            statements += parseStatement()
            if (pos == before) pos++ // guarantee progress
        }
        return BlockStmt(statements)
    }

    private fun parseStatement(): Stmt {
        val token = peek() ?: return EmptyStmt
        return when (token.text) {
            ";" -> { pos++; EmptyStmt }
            "{" -> parseBlock()
            "module" -> parseModuleDef()
            "function" -> parseFunctionDef()
            "if" -> parseIf()
            "for" -> parseFor()
            "let" -> parseLet()
            "include", "use" -> { skipToSemicolon(); EmptyStmt }
            "%", "#", "!", "*" -> { pos++; parseModuleCall(token.text[0]) }
            else -> parseAssignmentOrCall()
        }
    }

    private fun parseBlock(): Stmt {
        expect("{")
        val statements = ArrayList<Stmt>()
        while (!atEnd() && text() != "}") {
            val before = pos
            statements += parseStatement()
            if (pos == before) pos++
        }
        expect("}")
        return BlockStmt(statements)
    }

    private fun parseModuleDef(): Stmt {
        expect("module")
        val name = advance()?.text ?: return EmptyStmt
        val params = parseParams()
        val body = parseStatement()
        return ModuleDefStmt(name, params, body)
    }

    private fun parseFunctionDef(): Stmt {
        expect("function")
        val name = advance()?.text ?: return EmptyStmt
        val params = parseParams()
        expect("=")
        val expr = parseExpr()
        match(";")
        return FunctionDefStmt(name, params, expr)
    }

    private fun parseParams(): List<Param> {
        expect("(")
        val params = ArrayList<Param>()
        while (!atEnd() && text() != ")") {
            val name = advance()?.text ?: break
            val default = if (match("=")) parseExpr() else null
            params += Param(name, default)
            if (!match(",")) break
        }
        expect(")")
        return params
    }

    private fun parseIf(): Stmt {
        expect("if")
        expect("(")
        val condition = parseExpr()
        expect(")")
        val thenBranch = parseStatement()
        val elseBranch = if (match("else")) parseStatement() else null
        return IfStmt(condition, thenBranch, elseBranch)
    }

    private fun parseFor(): Stmt {
        expect("for")
        expect("(")
        val bindings = parseBindings()
        expect(")")
        return ForStmt(bindings, parseStatement())
    }

    private fun parseLet(): Stmt {
        expect("let")
        expect("(")
        val bindings = parseBindings()
        expect(")")
        return LetStmt(bindings, parseStatement())
    }

    private fun parseBindings(): List<Binding> {
        val bindings = ArrayList<Binding>()
        while (!atEnd() && text() != ")") {
            val name = advance()?.text ?: break
            expect("=")
            bindings += Binding(name, parseExpr())
            if (!match(",")) break
        }
        return bindings
    }

    /** `[for (bindings) (if (cond))? body]`. */
    private fun parseListComp(): Expr {
        match("for")
        expect("(")
        val bindings = parseBindings()
        expect(")")
        val condition = if (text() == "if") {
            pos++
            expect("(")
            val c = parseExpr()
            expect(")")
            c
        } else {
            null
        }
        val body = parseExpr()
        expect("]")
        return ListCompExpr(bindings, condition, body)
    }

    /** `let (bindings) body`. */
    private fun parseLetExpr(): Expr {
        expect("(")
        val bindings = parseBindings()
        expect(")")
        return LetExpr(bindings, parseExpr())
    }

    private fun parseAssignmentOrCall(): Stmt {
        val token = peek() ?: return EmptyStmt

        // `name = expr;`
        if (isName(token) && text(1) == "=") {
            pos++
            expect("=")
            val expr = parseExpr()
            match(";")
            return AssignStmt(token.text, expr)
        }

        // `name(args) ...`
        if (isName(token) && text(1) == "(") {
            return parseModuleCall('\u0000')
        }

        // Unknown construct: skip a token so we make progress.
        pos++
        return EmptyStmt
    }

    private fun parseModuleCall(modifier: Char): Stmt {
        val name = advance()?.text ?: return EmptyStmt
        val args = parseArgs()
        val body = when {
            text() == ";" -> { pos++; null }
            else -> parseStatement()
        }
        return ModuleCallStmt(name, args, body, modifier)
    }

    private fun parseArgs(): List<Arg> {
        expect("(")
        val args = ArrayList<Arg>()
        while (!atEnd() && text() != ")") {
            val named = isName(peek()) && text(1) == "="
            val argName = if (named) { val n = advance()?.text; expect("="); n } else null
            args += Arg(argName, parseExpr())
            if (!match(",")) break
        }
        expect(")")
        return args
    }

    private fun skipToSemicolon() {
        var depth = 0
        while (!atEnd()) {
            val t = text()
            when (t) {
                "<" -> depth++
                ">" -> if (depth > 0) depth--
                ";" -> { pos++; return }
            }
            pos++
        }
    }

    private fun expect(value: String) {
        if (text() == value) pos++ else if (!atEnd()) pos++
    }

    // ---- expressions ---------------------------------------------------------

    fun parseExpr(): Expr = parseTernary()

    private fun parseTernary(): Expr {
        val condition = parseOr()
        if (match("?")) {
            val ifTrue = parseExpr()
            expect(":")
            val ifFalse = parseExpr()
            return TernaryExpr(condition, ifTrue, ifFalse)
        }
        return condition
    }

    private fun parseOr(): Expr {
        var left = parseAnd()
        while (match("||")) left = BinaryExpr("||", left, parseAnd())
        return left
    }

    private fun parseAnd(): Expr {
        var left = parseEquality()
        while (match("&&")) left = BinaryExpr("&&", left, parseEquality())
        return left
    }

    private fun parseEquality(): Expr {
        var left = parseRelational()
        while (true) {
            left = when {
                match("==") -> BinaryExpr("==", left, parseRelational())
                match("!=") -> BinaryExpr("!=", left, parseRelational())
                else -> return left
            }
        }
    }

    private fun parseRelational(): Expr {
        var left = parseAdditive()
        while (true) {
            left = when {
                match("<=") -> BinaryExpr("<=", left, parseAdditive())
                match(">=") -> BinaryExpr(">=", left, parseAdditive())
                match("<") -> BinaryExpr("<", left, parseAdditive())
                match(">") -> BinaryExpr(">", left, parseAdditive())
                else -> return left
            }
        }
    }

    private fun parseAdditive(): Expr {
        var left = parseMultiplicative()
        while (true) {
            left = when {
                match("+") -> BinaryExpr("+", left, parseMultiplicative())
                match("-") -> BinaryExpr("-", left, parseMultiplicative())
                else -> return left
            }
        }
    }

    private fun parseMultiplicative(): Expr {
        var left = parseUnary()
        while (true) {
            left = when {
                match("*") -> BinaryExpr("*", left, parseUnary())
                match("/") -> BinaryExpr("/", left, parseUnary())
                match("%") -> BinaryExpr("%", left, parseUnary())
                else -> return left
            }
        }
    }

    private fun parseUnary(): Expr {
        if (match("-")) return UnaryExpr("-", parseUnary())
        if (match("!")) return UnaryExpr("!", parseUnary())
        if (match("+")) return parseUnary()
        return parsePostfix()
    }

    private fun parsePostfix(): Expr {
        var expr = parsePrimary()
        while (match("[")) {
            val index = parseExpr()
            expect("]")
            expr = IndexExpr(expr, index)
        }
        return expr
    }

    private fun parsePrimary(): Expr {
        val token = peek() ?: return NumberExpr(0.0)
        return when (token.type) {
            TokenType.NUMBER -> {
                pos++
                NumberExpr(parseNumber(token.text))
            }
            TokenType.STRING -> {
                pos++
                StringExpr(unquote(token.text))
            }
            TokenType.KEYWORD -> when (token.text) {
                "true" -> { pos++; BoolExpr(true) }
                "false" -> { pos++; BoolExpr(false) }
                "undef" -> { pos++; UndefExpr }
                "let" -> { pos++; parseLetExpr() }
                else -> skipPrimary()
            }
            TokenType.IDENTIFIER, TokenType.BUILTIN -> {
                pos++
                if (text() == "(") CallExpr(token.text, parseArgs()) else IdentExpr(token.text)
            }
            else -> when (token.text) {
                "(" -> { pos++; val e = parseExpr(); expect(")"); e }
                "[" -> parseBracket()
                else -> skipPrimary()
            }
        }
    }

    private fun skipPrimary(): Expr {
        if (!atEnd()) pos++
        return UndefExpr
    }

    private fun parseBracket(): Expr {
        expect("[")
        if (match("]")) return ListExpr(emptyList())
        if (text() == "for") return parseListComp()

        val first = parseExpr()
        if (match(":")) {
            val second = parseExpr()
            return if (match(":")) {
                val end = parseExpr()
                expect("]")
                RangeExpr(first, second, end)
            } else {
                expect("]")
                RangeExpr(first, null, second)
            }
        }

        val items = ArrayList<Expr>()
        items += first
        while (match(",")) {
            if (text() == "]") break
            items += parseExpr()
        }
        expect("]")
        return ListExpr(items)
    }

    // ---- literals ------------------------------------------------------------

    private fun parseNumber(raw: String): Double = when {
        raw.startsWith("0x") || raw.startsWith("0X") ->
            raw.substring(2).toLongOrNull(16)?.toDouble() ?: 0.0
        else -> raw.toDoubleOrNull() ?: 0.0
    }

    private fun unquote(raw: String): String {
        if (raw.length < 2) return raw
        val inner = raw.substring(1, raw.length - 1)
        return inner
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}
