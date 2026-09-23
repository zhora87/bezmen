package io.github.zhora87.bezmen.domain

import kotlinx.serialization.Serializable

/** Writing systems an engine can read. Locale packs declare which one their price tags use. */
enum class Script { LATIN, CYRILLIC }

/** Axis-aligned box in normalised image coordinates (0..1), origin top-left. */
@Serializable
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    fun union(other: Box): Box =
        Box(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

    /** Sub-box covering the character range [from, to) of a string of length [total], split proportionally. */
    fun slice(from: Int, to: Int, total: Int): Box {
        if (total <= 0) return this
        val l = left + width * from / total
        val r = left + width * to / total
        return Box(l, top, r, bottom)
    }
}

/** One recognised text line. Engine-agnostic: this is the parser's only view of the image. */
@Serializable
data class OcrLine(val text: String, val box: Box, val confidence: Float)
