package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.Dimension
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Quantity

/** Step 3: "900 мл", "0.33 л", "1.5кг", "12 шт", "6 x 200 г", "2 шт по 90 г". */
internal class QuantityDetector(private val pack: LocalePack) {
    fun detect(ctx: TagContext, consumed: Set<TokenId>): List<QuantityCandidate> =
        ctx.lines.indices.flatMap { line ->
            val tokens = ctx.tokensOf(line, consumed)
            numberUnitPairs(ctx, tokens) + bareUnits(ctx, tokens)
        }

    private fun numberUnitPairs(ctx: TagContext, tokens: List<Token>): List<QuantityCandidate> {
        val out = mutableListOf<QuantityCandidate>()
        var i = 0
        while (i < tokens.size - 1) {
            val unit = unitAfterNumber(tokens, i)
            if (unit != null) candidate(ctx, tokens, i, unit)?.let { out += it }
            i += 1 + (unit?.tokens?.size ?: 0)
        }
        return out
    }

    /** "грн / шт", "грн/кг": a unit right after the currency or a slash with no number means one of it. */
    private fun bareUnits(ctx: TagContext, tokens: List<Token>): List<QuantityCandidate> =
        tokens.indices.mapNotNull { i ->
            val word = tokens[i]
            val previous = tokens.getOrNull(i - 1)
            val before = tokens.getOrNull(i - 2)
            val afterSeparator = if (previous == null) {
                i == 0 && lineAboveEndsWithCurrency(ctx, word.lineIndex)
            } else {
                previous.kind == TokenKind.CURRENCY || (previous.text == "/" && before?.kind == TokenKind.CURRENCY)
            }
            val unit = if (word.kind == TokenKind.WORD && afterSeparator) pack.unitFor(word.text) else null
            unit?.let {
                val confidence = ctx.confidence(word.lineIndex) * BARE_UNIT_CONFIDENCE
                QuantityCandidate(
                    quantity = Quantity(1.0, it),
                    confidence = confidence,
                    line = word.lineIndex,
                    box = word.box,
                    tokens = listOf(word.id),
                    isMultipack = false,
                    isBareUnit = true,
                )
            }
        }

    /**
     * Multipacks first, then the candidate closest to the price (the package size sits next to the
     * price on a tag, while product packaging in the background carries its own numbers), then mass
     * and volume over count, then confidence.
     */
    fun choose(candidates: List<QuantityCandidate>, price: Box?): QuantityCandidate? = candidates.sortedWith(
        compareByDescending<QuantityCandidate> { it.isMultipack }
            .thenBy { price?.let { p -> distance(p, it.box) } ?: 0f }
            .thenBy { if (it.quantity.dimension == Dimension.COUNT) 1 else 0 }
            .thenByDescending { it.confidence },
    ).firstOrNull()

    private fun distance(a: Box, b: Box): Float {
        val dx = maxOf(0f, maxOf(a.left, b.left) - minOf(a.right, b.right))
        val dy = maxOf(0f, maxOf(a.top, b.top) - minOf(a.bottom, b.bottom))
        return dx + dy
    }

    /** "грн /" on one detector line and "шт" alone on the next one. */
    private fun lineAboveEndsWithCurrency(ctx: TagContext, line: Int): Boolean {
        val above = ctx.lineAbove(line) ?: return false
        val tokens = ctx.tokens[above].filterNot { it.text == "/" }
        return tokens.lastOrNull()?.kind == TokenKind.CURRENCY
    }

    private class UnitMatch(val unit: MeasureUnit, val tokens: List<Token>)

    /**
     * The unit after the number at [i]. The unit may be a run of glued tokens that only together form
     * a pack alias: ATB's font turns "кг" into "k7" and "наб-р" into "на6-р".
     */
    private fun unitAfterNumber(tokens: List<Token>, i: Int): UnitMatch? {
        val number = tokens[i]
        val word = tokens.getOrNull(i + 1)
        val starts = number.kind == TokenKind.NUMBER && number.number != null && word?.kind == TokenKind.WORD
        return if (starts) unitInRun(gluedRun(tokens, i + 1)) else null
    }

    /** The token at [from] and up to [MAX_RUN] - 1 tokens glued to it without spaces. */
    private fun gluedRun(tokens: List<Token>, from: Int): List<Token> {
        val run = mutableListOf(tokens[from])
        while (run.size < MAX_RUN) {
            val next = tokens.getOrNull(from + run.size)?.takeIf { it.start == run.last().end } ?: break
            run += next
        }
        return run
    }

    private fun unitInRun(run: List<Token>): UnitMatch? {
        val joined = (run.size downTo 2).firstNotNullOfOrNull { size ->
            pack.unitFor(run.take(size).joinToString("") { it.text })?.let { UnitMatch(it, run.take(size)) }
        }
        // "82n214780361": a letter wedged between digit runs is a misread barcode, not a unit.
        val wedged = run.getOrNull(1)?.kind == TokenKind.NUMBER
        return joined ?: if (wedged) null else pack.unitFor(run.first().text)?.let { UnitMatch(it, run.take(1)) }
    }

    /** Null when OCR produced a number that cannot be a package size ("0 мл", "0,5 мг"). */
    private fun candidate(ctx: TagContext, tokens: List<Token>, i: Int, match: UnitMatch): QuantityCandidate? {
        val number = tokens[i]
        val word = match.tokens.first()
        val unit = match.unit
        var value = number.number ?: 0.0
        var used = listOf(number.id) + match.tokens.map { it.id }
        val multiplier = multiplierBefore(tokens, i)
        if (multiplier != null) {
            value *= multiplier.first
            used = multiplier.second + used
        }
        if (value <= 0.0) return null
        val quantity = Quantity(value, unit)
        if (quantity.toBase().value < MIN_BASE_VALUE) return null
        val glued = number.end == word.start
        val confidence = ctx.confidence(number.lineIndex) * if (glued) GLUED_CONFIDENCE else SPACED_CONFIDENCE
        val box = match.tokens.fold(number.box) { b, t -> b.union(t.box) }
        return QuantityCandidate(quantity, confidence, number.lineIndex, box, used, multiplier != null)
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
        const val BARE_UNIT_CONFIDENCE = 0.8f

        /** Nothing on a shelf is sold below one gram or one millilitre: "0,5 мг" is a misread "0,5 кг". */
        const val MIN_BASE_VALUE = 1.0
        const val SPACED_CONFIDENCE = 0.9f
        const val PIECES_COUNT_OFFSET = 3

        /** Longest glued run tried as one alias ("на", "6", "-р"). */
        const val MAX_RUN = 3
    }
}
