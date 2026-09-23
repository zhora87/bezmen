package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenizerTest {
    private val tokenizer = Tokenizer(TestPacks.ru)
    private val box = Box(0.0f, 0.0f, 1.0f, 0.1f)

    private fun kinds(text: String) = tokenizer.tokenize(0, text, box).map { it.kind }

    private fun texts(text: String) = tokenizer.tokenize(0, text, box).map { it.text }

    @Test
    fun `splits numbers glued to units`() {
        assertEquals(listOf("900", "мл"), texts("900мл"))
        assertEquals(listOf("1.5", "кг"), texts("1.5кг"))
        assertEquals(listOf(TokenKind.NUMBER, TokenKind.WORD), kinds("900мл"))
    }

    @Test
    fun `recognises currency percent and dates`() {
        assertEquals(listOf(TokenKind.NUMBER, TokenKind.CURRENCY), kinds("249.90 ₽"))
        assertEquals(listOf(TokenKind.NUMBER, TokenKind.PERCENT), kinds("2.5%"))
        assertEquals(listOf(TokenKind.DATE), kinds("12.05.2026"))
        assertEquals(listOf(TokenKind.WORD, TokenKind.DATE), kinds("до 01/10/26"))
    }

    @Test
    fun `dotted codes and times are neither numbers nor dates`() {
        assertEquals(listOf(TokenKind.CODE), kinds("1.21.6"))
        assertEquals(listOf(TokenKind.CODE), kinds("19:03"))
        assertEquals(listOf(TokenKind.NUMBER), kinds("128718"))
        assertEquals(listOf(TokenKind.NUMBER), kinds("12.05"))
    }

    @Test
    fun `token boxes follow character positions`() {
        val tokens = tokenizer.tokenize(0, "249 90", box)

        assertEquals(0.0f, tokens[0].box.left, 1e-6f)
        assertEquals(0.5f, tokens[0].box.right, 1e-6f)
        assertEquals(4f / 6f, tokens[1].box.left, 1e-6f)
        assertEquals(1.0f, tokens[1].box.right, 1e-6f)
    }

    @Test
    fun `keeps character offsets for marker matching`() {
        val tokens = tokenizer.tokenize(3, "цена за 1 кг", box)

        assertEquals(listOf(0, 5, 8, 10), tokens.map { it.start })
        assertEquals(3, tokens.first().lineIndex)
    }
}
