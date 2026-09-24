package io.github.zhora87.bezmen.ocr.paddle

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.OcrLine
import kotlin.test.Test
import kotlin.test.assertEquals

class ScaleMergeTest {
    private fun line(text: String, l: Float, t: Float, r: Float, b: Float, c: Float = 0.95f) =
        OcrLine(text, Box(l, t, r, b), c)

    private fun List<OcrLine>.texts() = map { it.text }.toSet()

    @Test
    fun `coarse line with more digits replaces the fine pieces of the same price`() {
        // "48" found only as "8" at the fine size.
        val fine = listOf(
            line("8", 0.81f, 0.30f, 0.92f, 0.70f),
            line("Борошно Аміна 2 кг", 0.10f, 0.80f, 0.60f, 0.86f),
        )
        val coarse = listOf(line("48", 0.72f, 0.29f, 0.93f, 0.71f))

        assertEquals(setOf("48", "Борошно Аміна 2 кг"), ScaleMerge.merge(fine, coarse).texts())
    }

    @Test
    fun `fine lines stay when the coarse one read no more digits`() {
        // Price and kopecks read separately by the fine pass, glued by the coarse one: same digits.
        val fine = listOf(line("94", 0.69f, 0.73f, 0.84f, 0.92f), line("90", 0.81f, 0.74f, 0.89f, 0.85f))
        val coarse = listOf(line("9490", 0.68f, 0.72f, 0.90f, 0.93f))

        assertEquals(setOf("94", "90"), ScaleMerge.merge(fine, coarse).texts())
    }

    @Test
    fun `small print read worse by the coarse pass stays fine`() {
        val fine = listOf(line("грн / 10 шт", 0.70f, 0.60f, 0.90f, 0.66f))
        val coarse = listOf(line("ГО", 0.69f, 0.58f, 0.91f, 0.70f))

        assertEquals(setOf("грн / 10 шт"), ScaleMerge.merge(fine, coarse).texts())
    }

    @Test
    fun `price missed by the fine pass is added from the coarse one`() {
        val fine = listOf(line("90", 0.54f, 0.64f, 0.67f, 0.85f))
        val coarse = listOf(line("24", 0.20f, 0.62f, 0.50f, 1.00f), line("90", 0.53f, 0.63f, 0.68f, 0.86f))

        assertEquals(listOf("90", "24"), ScaleMerge.merge(fine, coarse).map { it.text })
    }

    @Test
    fun `unsure or wordy coarse lines are not added`() {
        val fine = emptyList<OcrLine>()
        val coarse = listOf(line("24", 0.2f, 0.6f, 0.5f, 1f, c = 0.4f), line("Борошно", 0.1f, 0.1f, 0.6f, 0.2f))

        assertEquals(emptyList(), ScaleMerge.merge(fine, coarse))
    }

    @Test
    fun `a fine line that already covers the coarse one keeps the place`() {
        // The fine pass read "9490" in one box; the coarse pass split it into "94" and "90".
        val fine = listOf(line("9490", 0.40f, 0.50f, 0.85f, 0.85f))
        val coarse = listOf(line("94", 0.40f, 0.55f, 0.65f, 0.84f), line("90", 0.66f, 0.52f, 0.80f, 0.70f))

        assertEquals(setOf("9490"), ScaleMerge.merge(fine, coarse).texts())
    }
}
