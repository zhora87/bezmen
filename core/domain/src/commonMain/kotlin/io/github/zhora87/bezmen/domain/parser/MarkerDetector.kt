package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.ScriptFolding
import io.github.zhora87.bezmen.domain.UnitPriceCalculator
import kotlin.math.abs

/**
 * Step 4 plus the discount, old-price and loyalty flags: phrases from the locale pack.
 * A unit-price marker ("за 1 кг", "per 100 g") yields a reference quantity and, when a small
 * price sits next to it, a printed unit price. A marker without a price marks weighted goods.
 */
internal class MarkerDetector(
    private val pack: LocalePack,
    private val normalizer: TextNormalizer,
    private val moneyDetector: MoneyDetector,
) {
    internal class Result(
        val unitPrices: List<UnitPriceCandidate>,
        val flags: List<LineFlags>,
        val consumed: Set<TokenId>,
        /** Reference of a unit-price marker that had no price of its own ("цена за 1 кг" alone). */
        val weightedReference: Quantity?,
        val markerLines: Set<Int>,
        /** The tag says the goods are sold by weight ("ваговий"). */
        val weightedByMarker: Boolean,
        /** Lines holding a weighted-goods label or an article code: never part of the name. */
        val labelLines: Set<Int> = emptySet(),
    )

    /** One line that contained a unit-price marker. */
    private class Outcome(val line: Int, val reference: Quantity?, val money: MoneyCandidate?)

    private fun List<String>.prepared(): List<String> =
        map { ScriptFolding.fold(normalizer.normalize(it).lowercase(), pack.script) }

    private val unitPriceMarkers = pack.unitPriceMarkers.prepared().sortedByDescending { it.length }
    private val discount = pack.discountMarkers.prepared()
    private val oldPrice = pack.oldPriceMarkers.prepared()
    private val loyalty = pack.loyaltyMarkers.prepared()
    private val weighted = pack.weightedMarkers.prepared()
    private val codes = pack.codeMarkers.prepared()

    fun detect(ctx: TagContext): Result {
        val flags = ctx.lower.mapIndexed { line, text ->
            LineFlags(
                discount = discount.any(text::contains) || isPercentOnlyLine(ctx.tokens[line]),
                oldPrice = oldPrice.any(text::contains),
                loyalty = loyalty.any(text::contains),
            )
        }
        val consumed = mutableSetOf<TokenId>()
        // Article codes and barcodes: nothing on such a line is a price or a quantity.
        ctx.lines.indices.filter { line -> codes.any(ctx.lower[line]::contains) }.forEach { line ->
            consumed += ctx.tokens[line].map { it.id }
        }
        consumed += percentsUnderDiscount(ctx)
        val outcomes = ctx.lines.indices.mapNotNull { line -> processLine(ctx, line, consumed) }
        val reference = outcomes.firstOrNull { it.reference != null && it.money == null }?.reference
            ?: weightedLabelReference(ctx, consumed)
        return Result(
            unitPrices = outcomes.mapNotNull { it.toUnitPrice(ctx) },
            flags = flags,
            consumed = consumed,
            weightedReference = reference,
            markerLines = outcomes.map { it.line }.toSet(),
            weightedByMarker = ctx.lower.any { text -> weighted.any(text::contains) },
            labelLines = ctx.lines.indices.filter { line ->
                val text = ctx.lower[line]
                weighted.any(text::contains) || codes.any(text::contains)
            }.toSet(),
        )
    }

    private fun processLine(ctx: TagContext, line: Int, consumed: MutableSet<TokenId>): Outcome? {
        val span = findMarker(ctx, line) ?: return null
        val spanTokens = ctx.tokens[line].filter { it.start <= span.last && it.end > span.first }
        consumed += spanTokens.map { it.id }
        val after = ctx.tokens[line].filter { it.start > span.last }.take(REFERENCE_TOKENS)
        val reference = referenceIn(spanTokens)
            ?: referenceIn(after)?.also { consumed += after.map { t -> t.id } }
            ?: return Outcome(line, null, null)
        val money = moneyFor(ctx, line, consumed)
        if (money != null) consumed += money.tokens
        return Outcome(line, reference, money)
    }

    private fun Outcome.toUnitPrice(ctx: TagContext): UnitPriceCandidate? {
        val ref = reference ?: return null
        val found = money ?: return null
        return UnitPriceCandidate(
            unitPrice = UnitPriceCalculator.calculate(found.money, ref),
            reference = ref,
            money = found.money,
            confidence = minOf(ctx.confidence(line), found.confidence) * MARKER_CONFIDENCE,
            lines = (listOf(line) + found.lines).distinct(),
        )
    }

    /** "-50%" or "29%" standing alone is a promotion even without the word for it. Fat content ("ж.45%") is not. */
    private fun isPercentOnlyLine(tokens: List<Token>): Boolean {
        val meaningful = tokens.filterNot { it.kind == TokenKind.WORD && it.text in MINUS_SIGNS }
        if (meaningful.size != 2 || meaningful[1].kind != TokenKind.PERCENT) return false
        val number = meaningful[0]
        return number.isInteger && (number.number ?: 0.0).toInt() in DISCOUNT_PERCENT_RANGE
    }

    private fun findMarker(ctx: TagContext, line: Int): IntRange? {
        val text = ctx.lower[line]
        return unitPriceMarkers.firstNotNullOfOrNull { marker ->
            val idx = text.indexOf(marker)
            if (idx >= 0) idx until idx + marker.length else null
        }
    }

    /** "1 кг" or "100 г" inside the tokens, or a bare unit ("за кг") meaning one of it. */
    /**
     * "Знижка" over "10" whose "%" was lost: a lone 1..99 right under the word, in its column and set
     * at least as large, is the percentage. Kopecks are smaller than what sits above them.
     */
    private fun percentsUnderDiscount(ctx: TagContext): List<TokenId> =
        ctx.lines.indices.mapNotNull { line ->
            val above = ctx.lineAbove(line) ?: return@mapNotNull null
            val sole = ctx.tokens[line].singleOrNull()
                ?.takeIf { it.isInteger && it.digitCount <= MAX_PERCENT_DIGITS }
                ?: return@mapNotNull null
            val label = ctx.lines[above].box
            val box = ctx.lines[line].box
            val underLabel = discount.any(ctx.lower[above]::contains) &&
                box.centerX in label.left..label.right &&
                box.height >= label.height
            sole.id.takeIf { underLabel }
        }

    /**
     * "вартість вказана за 100 г" with the unit lost to OCR ("... за100"). Weighed goods are priced by
     * mass, so a bare number after the label is grams from 10 up and kilograms below.
     */
    private fun weightedLabelReference(ctx: TagContext, consumed: MutableSet<TokenId>): Quantity? {
        val found = ctx.lines.indices.firstNotNullOfOrNull { line -> labelReference(ctx, line, consumed) }
        found?.let { consumed += it.second }
        return found?.first
    }

    private fun labelReference(ctx: TagContext, line: Int, consumed: Set<TokenId>): Pair<Quantity, List<TokenId>>? {
        val end = weighted.mapNotNull { marker ->
            ctx.lower[line].indexOf(marker).takeIf { it >= 0 }?.let { it + marker.length }
        }.maxOrNull() ?: return null
        val after = ctx.tokens[line].filter { it.start >= end && it.id !in consumed }.take(REFERENCE_TOKENS)
        val value = after.firstOrNull()?.takeIf { it.kind == TokenKind.NUMBER }?.number?.takeIf { it > 0 }
            ?: return null
        val unit = after.getOrNull(1)?.let { pack.unitFor(it.text) }
            ?: if (value >= MIN_GRAMS_REFERENCE) MeasureUnit.GRAM else MeasureUnit.KILOGRAM
        return Quantity(value, unit) to after.map { it.id }
    }

    private fun referenceIn(tokens: List<Token>): Quantity? {
        val unitIndex = tokens.indexOfFirst { it.kind == TokenKind.WORD && pack.unitFor(it.text) != null }
        if (unitIndex < 0) return null
        val unit = pack.unitFor(tokens[unitIndex].text) ?: return null
        val number = tokens.getOrNull(unitIndex - 1)?.takeIf { it.kind == TokenKind.NUMBER }?.number
        return Quantity(number ?: 1.0, unit)
    }

    /** Price in the same line first, else the nearest small-print line. */
    private fun moneyFor(ctx: TagContext, line: Int, consumed: Set<TokenId>): MoneyCandidate? {
        val own = moneyDetector.inLine(ctx, ctx.tokensOf(line, consumed), allowBare = false)
        if (own.isNotEmpty()) return own.maxBy { it.confidence }
        val center = ctx.lines[line].box.centerY
        return ctx.lines.indices
            .filter { it != line && ctx.relHeight(it) < SMALL_PRINT_MAX_REL_HEIGHT }
            .sortedBy { abs(ctx.lines[it].box.centerY - center) }
            .firstNotNullOfOrNull { other ->
                val candidates = moneyDetector.inLine(ctx, ctx.tokensOf(other, consumed), allowBare = false)
                candidates.maxByOrNull { it.confidence }
            }
    }

    private companion object {
        const val MIN_GRAMS_REFERENCE = 10.0
        const val MAX_PERCENT_DIGITS = 2
        const val MARKER_CONFIDENCE = 0.9f
        const val SMALL_PRINT_MAX_REL_HEIGHT = 0.6f
        const val REFERENCE_TOKENS = 2
        val MINUS_SIGNS = setOf("-", "−", "–")
        val DISCOUNT_PERCENT_RANGE = 5..90
    }
}
