package com.openscad.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScadLexerTest {

    private fun lex(src: String) = ScadLexer.lex(src)

    @Test
    fun `is lossless - concat of token text reproduces source`() {
        val src = """
            // comment
            /* [Group] */
            module box(w = 1) { cube([w, 2.5, 0x1F]); }
            s = "a\"b//c";
            v = 1e-3 + .5;
        """.trimIndent()
        assertEquals(src, lex(src).joinToString("") { it.text })
    }

    @Test
    fun `recognises line and block comments and customizer directive`() {
        val tokens = lex("// hi\n/* plain */\n/* [Lid] */")
        val types = tokens.filter { it.type != TokenType.WHITESPACE && it.type != TokenType.NEWLINE }
            .map { it.type }
        assertEquals(
            listOf(
                TokenType.COMMENT_LINE,
                TokenType.COMMENT_BLOCK,
                TokenType.CUSTOMIZER_DIRECTIVE,
            ),
            types,
        )
    }

    @Test
    fun `double slash inside a string is not a comment`() {
        val tokens = lex("x = \"a//b\";")
        assertEquals(1, tokens.count { it.type == TokenType.STRING })
        assertEquals(0, tokens.count { it.type == TokenType.COMMENT_LINE })
        val stringToken = tokens.first { it.type == TokenType.STRING }
        assertEquals("\"a//b\"", stringToken.text)
    }

    @Test
    fun `classifies identifiers keywords and builtins`() {
        val tokens = lex("module cube customThing sin")
        val pairs = tokens.filter { it.type == TokenType.KEYWORD || it.type == TokenType.BUILTIN || it.type == TokenType.IDENTIFIER }
            .map { it.text to it.type }
        assertEquals(
            listOf(
                "module" to TokenType.KEYWORD,
                "cube" to TokenType.BUILTIN,
                "customThing" to TokenType.IDENTIFIER,
                "sin" to TokenType.BUILTIN,
            ),
            pairs,
        )
    }

    @Test
    fun `lexes number forms`() {
        val nums = lex("1 2.5 .5 1e-3 0xFF 10.0E+2")
            .filter { it.type == TokenType.NUMBER }
            .map { it.text }
        assertEquals(listOf("1", "2.5", ".5", "1e-3", "0xFF", "10.0E+2"), nums)
    }

    @Test
    fun `tracks line and column`() {
        val tokens = lex("a\n  bb")
        val bb = tokens.first { it.text == "bb" }
        assertEquals(2, bb.line)
        assertEquals(3, bb.column)
    }

    @Test
    fun `special variables starting with dollar are identifiers`() {
        val token = lex("\$fn = 64;").first { it.text == "\$fn" }
        assertEquals(TokenType.IDENTIFIER, token.type)
    }
}
