package io.github.zhora87.bezmen.ocr.paddle

import kotlin.test.Test
import kotlin.test.assertEquals

class CtcDecoderTest {
    // dictionary: index 1 = "2", 2 = "4", 3 = "9", 4 = ",", 5 = space (empty entry)
    private val decoder = CtcDecoder(listOf("2", "4", "9", ",", ""))
    private val classes = 6

    private fun step(best: Int, p: Float = 0.9f): FloatArray =
        FloatArray(classes) { if (it == best) p else (1 - p) / (classes - 1) }

    @Test
    fun `collapses repeats and skips blanks`() {
        val steps = listOf(
            step(1), step(1), step(0), step(2), step(0), step(0), step(3), step(3), step(4), step(3), step(0),
        )
        val flat = steps.flatMap { it.asList() }.toFloatArray()

        val decoded = decoder.decode(flat, steps.size, classes)

        assertEquals("249,9", decoded.text)
        assertEquals(0.9f, decoded.confidence, 1e-6f)
    }

    @Test
    fun `repeated character separated by a blank is kept twice`() {
        val steps = listOf(step(3), step(0), step(3))
        val decoded = decoder.decode(steps.flatMap { it.asList() }.toFloatArray(), steps.size, classes)

        assertEquals("99", decoded.text)
    }

    @Test
    fun `empty dictionary entry is a space and edges are trimmed`() {
        val steps = listOf(step(5), step(1), step(5), step(2), step(5))
        val decoded = decoder.decode(steps.flatMap { it.asList() }.toFloatArray(), steps.size, classes)

        assertEquals("2 4", decoded.text)
    }

    @Test
    fun `all blanks give empty text and zero confidence`() {
        val decoded = decoder.decode(step(0) + step(0), 2, classes)

        assertEquals("", decoded.text)
        assertEquals(0f, decoded.confidence)
    }

    @Test
    fun `metadata dictionary is split by newline`() {
        val d = CtcDecoder.fromMetadata("a\nb\n")
        val flat = listOf(1, 2).flatMap { best -> List(4) { if (it == best) 0.8f else 0.05f } }.toFloatArray()

        assertEquals("ab", d.decode(flat, 2, 4).text)
    }

    @Test
    fun `class after the last dictionary entry is the implicit space`() {
        // PaddleOCR appends a space class after the dictionary; the metadata string may have no trailing newline.
        val d = CtcDecoder.fromMetadata("a\nb")
        val flat = listOf(1, 3, 2).flatMap { best -> List(4) { if (it == best) 0.9f else 0.03f } }.toFloatArray()

        assertEquals("a b", d.decode(flat, 3, 4).text)
    }

    @Test
    fun `trailing newline in metadata does not add a second space class`() {
        val d = CtcDecoder.fromMetadata("a\nb\n")
        val flat = listOf(1, 3, 2).flatMap { best -> List(4) { if (it == best) 0.9f else 0.03f } }.toFloatArray()

        assertEquals("a b", d.decode(flat, 3, 4).text)
    }

    @Test
    fun `class count that does not match the dictionary fails loudly`() {
        val d = CtcDecoder.fromMetadata("a\nb")

        kotlin.test.assertFailsWith<IllegalStateException> { d.decode(FloatArray(10), 2, 5) }
    }
}
