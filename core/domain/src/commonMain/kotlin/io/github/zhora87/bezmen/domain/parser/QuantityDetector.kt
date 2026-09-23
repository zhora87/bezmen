package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Dimension
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Quantity

/** Step 3: "900 мл", "0.33 л", "1.5кг", "12 шт", "6 x 200 г", "2 шт по 90 г". */
internal class QuantityDetector(private val pack: LocalePack) {
    fun detect(ctx: TagContext, consumed: Set<TokenId>): List<QuantityCandidate> {
        val out = mutableListOf<QuantityCandidate>()
        for (line in ctx.lines.indices) {
            val tokens = ctx.tokensOf(line, consumed)
            var i = 0
            while (i < tokens.size - 1) {
                val unit = unitAfterNumber(tokens[i], tokens[i + 1])
                if (unit == null) {
                    i++
                } else {
                    out += candidate(ctx, tokens, i, unit)
                    i += 2
                }
            }
        }
        return out
    }

    /** Multipacks first, then mass and volume over count, then confidence. */
    fun choose(candidates: List<QuantityCandidate>): QuantityCandidate? = candidates.sortedWith(
        compareByDescending<QuantityCandidate> { it.isMultipack }
            .thenBy { if (it.quantity.dimension == Dimension.COUNT) 1 else 0 }
            .thenByDescending { it.confidence },
    ).firstOrNull()

    private fun unitAfterNumber(number: Token, word: Token): MeasureUnit? {
        if (number.kind != TokenKind.NUMBER || number.number == null || word.kind != TokenKind.WORD) return null
        return pack.unitFor(word.text)
    }

    private fun candidate(ctx: TagContext, tokens: List<Token>, i: Int, unit: MeasureUnit): QuantityCandidate {
        val number = tokens[i]
        val word = tokens[i + 1]
        var value = number.number ?: 0.0
        var used = listOf(number.id, word.id)
        val multiplier = multiplierBefore(tokens, i)
        if (multiplier != null) {
            value *= multiplier.first
            used = multiplier.second + used
        }
        val glued = number.end == word.start
        val confidence = ctx.confidence(number.lineIndex) * if (glued) GLUED_CONFIDENCE else SPACED_CONFIDENCE
        return QuantityCandidate(Quantity(value, unit), confidence, number.lineIndex, used, multiplier != null)
    }

    /** "6 x [200 г]" gives 6; "2 шт по [90 г]" gives 2. Returns the factor and the tokens it used. */
    private fun multiplierBefore(tokens: List<Token>, i: Int): Pair<Double, List<TokenId>>? {
        val sep = tokens.getOrNull(i - 1)
            ?.takeIf { it.kind == TokenKind.WORD && pack.isMultipack(it.text) }
            ?: return null
        val before = tokens.getOrNull(i - 2) ?: return null
        val count = tokens.getOrNull(i - PIECES_COUNT_OFFSET)
        val countValue = count?.number
        val isPieces = before.kind == TokenKind.WORD && pack.unitFor(before.text)?.dimension == Dimension.COUNT
        val beforeValue = before.number
        return when {
            before.kind == TokenKind.NUMBER && beforeValue != null -> beforeValue to listOf(before.id, sep.id)
            isPieces && count != null && countValue != null -> countValue to listOf(count.id, before.id, sep.id)
            else -> null
        }
    }

    private companion object {
        const val GLUED_CONFIDENCE = 0.95f
        const val SPACED_CONFIDENCE = 0.9f
        const val PIECES_COUNT_OFFSET = 3
    }
}
