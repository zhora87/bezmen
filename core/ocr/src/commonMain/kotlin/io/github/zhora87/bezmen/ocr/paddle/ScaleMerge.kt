package io.github.zhora87.bezmen.ocr.paddle

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.OcrLine

/**
 * Joins lines recognised from two detector input sizes. The detector was trained on text of ordinary
 * size: price digits half the tag tall are found in pieces or not at all at the fine size ("48" as
 * "8") and whole at the coarse one, while small print is only readable at the fine size. So a coarse
 * line wins only where it reads more digits than the fine lines under it, or where the fine pass
 * found nothing; everything else keeps the fine result.
 */
object ScaleMerge {
    /** Share of the smaller box that must overlap the other one to count as the same text. */
    private const val INSIDE = 0.6f

    /** A coarse line fills a gap only when confident and mostly digits, i.e. a price. */
    private const val MIN_CONFIDENCE = 0.8f
    private const val MIN_DIGITS = 2

    fun merge(fine: List<OcrLine>, coarse: List<OcrLine>): List<OcrLine> {
        var result = fine
        for (c in coarse) {
            if (c.confidence < MIN_CONFIDENCE || !isNumeric(c.text)) continue
            val under = result.filter { overlaps(it.box, c.box) }
            if (digits(c.text) > under.sumOf { digits(it.text) }) result = result - under.toSet() + c
        }
        return result
    }

    private fun isNumeric(text: String): Boolean {
        val digits = digits(text)
        return digits >= MIN_DIGITS && digits * 2 > text.count { !it.isWhitespace() }
    }

    private fun digits(text: String): Int = text.count(Char::isDigit)

    /** Same text: the boxes share most of the smaller one, whichever pass produced it. */
    private fun overlaps(a: Box, b: Box): Boolean {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (w <= 0f || h <= 0f) return false
        return w * h >= minOf(a.width * a.height, b.width * b.height) * INSIDE
    }
}
