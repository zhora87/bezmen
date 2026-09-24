package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.OcrLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagContextTest {
    private fun context(vararg boxes: Box): TagContext {
        val pack = TestPacks.uk
        return TagContext.build(boxes.map { OcrLine("x", it, 1f) }, TextNormalizer(pack), Tokenizer(pack), pack)
    }

    @Test
    fun `line stacked right above in the same column is found`() {
        val ctx = context(Box(0.71f, 0.28f, 0.84f, 0.39f), Box(0.71f, 0.37f, 0.82f, 0.52f))

        assertEquals(0, ctx.lineAbove(1))
    }

    @Test
    fun `deeply overlapping box is not the line above`() {
        val ctx = context(Box(0.50f, 0.10f, 0.70f, 0.20f), Box(0.55f, 0.12f, 0.65f, 0.32f))

        assertNull(ctx.lineAbove(1))
    }

    @Test
    fun `line in another column is not the line above`() {
        val ctx = context(Box(0.05f, 0.28f, 0.30f, 0.39f), Box(0.71f, 0.40f, 0.82f, 0.52f))

        assertNull(ctx.lineAbove(1))
    }
}
