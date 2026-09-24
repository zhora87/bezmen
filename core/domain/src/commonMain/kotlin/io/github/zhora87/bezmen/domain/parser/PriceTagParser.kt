package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.UnitPrice
import io.github.zhora87.bezmen.domain.UnitPriceCalculator
import kotlin.math.roundToInt

/**
 * Turns OCR lines of one price tag into a price per unit (docs/parsing.md).
 * Pure: no platform types, no I/O, same input gives the same output.
 */
class PriceTagParser(private val pack: LocalePack) {
    private val normalizer = TextNormalizer(pack)
    private val tokenizer = Tokenizer(pack)
    private val moneyDetector = MoneyDetector(pack)
    private val markerDetector = MarkerDetector(pack, normalizer, moneyDetector)
    private val quantityDetector = QuantityDetector(pack)

    fun parse(lines: List<OcrLine>): ParseResult {
        if (lines.isEmpty()) return ParseResult.Nothing
        val ctx = TagContext.build(lines, normalizer, tokenizer, pack)
        val markers = markerDetector.detect(ctx)
        val quantities = quantityDetector.detect(ctx, markers.consumed)
        val consumed = markers.consumed + quantities.flatMap { it.tokens }
        val moneys = moneyDetector.detectAll(ctx, consumed)
        val prices = PriceSelector(ctx, markers.flags).select(moneys)
        val quantity = quantityDetector.choose(quantities, prices.current?.box)
        val quantityField = quantity?.let { chosen ->
            val others = quantities.map { it.quantity } - chosen.quantity
            Field(chosen.quantity, chosen.confidence, others, listOf(chosen.line))
        }
        val printed = markers.unitPrices.maxByOrNull { it.confidence }
        val name = NameDetector.detect(ctx, markers, quantity)
        val weightedByBareUnit = quantity != null && quantity.isBareUnit && quantity.quantity.isWholeUnit()
        val weighted = markers.weightedByMarker || weightedByBareUnit
        return Assembler(prices, quantityField, printed, markers.weightedReference, name, weighted).assemble()
    }
}

/** Chooses the current and the old price among money candidates (docs/parsing.md, step 7). */
private class PriceSelector(private val ctx: TagContext, private val flags: List<LineFlags>) {
    class Prices(
        val current: MoneyCandidate?,
        val old: MoneyCandidate?,
        val alternatives: List<Money>,
        val confidenceFactor: Float,
    )

    fun select(moneys: List<MoneyCandidate>): Prices {
        val old = moneys.filter { flagged(it) { f -> f.oldPrice } }
        val loyalty = moneys.filter { flagged(it) { f -> f.loyalty } && it !in old }
        val regular = (moneys - old.toSet() - loyalty.toSet()).sortedByDescending(::score)
        val pool = if (regular.isNotEmpty()) regular else loyalty.sortedByDescending(::score)
        var current = pool.firstOrNull() ?: return Prices(null, null, emptyList(), 1f)
        var oldPrice = old.maxByOrNull(::score)
        var factor = 1f
        val second = pool.getOrNull(1)
        if (oldPrice == null && second != null) {
            val clearlyTaller = current.box.height > second.box.height * (1 + HEIGHT_GAP)
            if (clearlyTaller || flags.any { it.discount }) {
                oldPrice = second
            } else {
                oldPrice = maxOf(current, second, compareBy { it.money.minor })
                current = minOf(current, second, compareBy { it.money.minor })
                factor = SIMILAR_HEIGHT_PENALTY
            }
        }
        if (oldPrice != null && oldPrice.money.minor < current.money.minor) {
            // A crossed-out price is never lower than the current one. If the cheaper candidate is set
            // in bigger type it is the real price and the roles are swapped; otherwise it is a card or
            // app price that escaped its label and only an alternative.
            if (oldPrice.box.height > current.box.height) {
                val swapped = current
                current = oldPrice
                oldPrice = swapped
            } else {
                oldPrice = null
            }
        }
        if (oldPrice != null && oldPrice.money.minor > current.money.minor * MAX_OLD_PRICE_RATIO) {
            oldPrice = null // a barcode fragment or a code, not a former price
        }
        val taken = setOf(current.money, oldPrice?.money)
        val alternatives = (pool + loyalty).map { it.money }.filter { it !in taken }.distinct()
        return Prices(current, oldPrice, alternatives, factor)
    }

    /** A label flags a price when they share a line or overlap vertically (label to the left of the price). */
    private fun flagged(c: MoneyCandidate, pick: (LineFlags) -> Boolean): Boolean = flags.indices.any { line ->
        pick(flags[line]) && (line in c.lines || overlapsVertically(ctx.lines[line].box, c.box))
    }

    private fun overlapsVertically(a: Box, b: Box): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return overlap >= minOf(a.height, b.height) * BAND_OVERLAP
    }

    private fun score(c: MoneyCandidate): Float {
        val relHeight = c.lines.maxOf { ctx.relHeight(it) }
        val lowerHalf = if (c.box.centerY > LOWER_HALF) POSITION_BONUS else 0f
        val currency = if (c.hasCurrency) CURRENCY_WEIGHT else 0f
        return c.confidence * CONFIDENCE_WEIGHT + relHeight * HEIGHT_WEIGHT + currency + lowerHalf
    }

    private companion object {
        const val HEIGHT_GAP = 0.3f
        const val SIMILAR_HEIGHT_PENALTY = 0.7f
        const val CONFIDENCE_WEIGHT = 0.6f
        const val HEIGHT_WEIGHT = 0.3f
        const val CURRENCY_WEIGHT = 0.1f
        const val POSITION_BONUS = 0.05f
        const val LOWER_HALF = 0.5f
        const val BAND_OVERLAP = 0.5f
        const val MAX_OLD_PRICE_RATIO = 5
    }
}

/**
 * Step 8: the topmost wordy line in the upper part of the tag, joined with the wordy lines that
 * follow directly under it in similar type ("Буряк / перший / ґатунок"). Markers and flag lines
 * are never part of the name; a quantity inside the name line is cut out.
 */
private object NameDetector {
    private const val MAX_TOP = 0.55f
    private const val MIN_LETTERS = 3
    private const val MAX_LINES = 3
    private const val NAME_CONFIDENCE = 0.5f
    private const val GAP_FACTOR = 0.6f
    private const val HEIGHT_RATIO = 1.5f
    private const val EDGE = 0.005f
    private val SPACES = Regex("\\s+")

    fun detect(ctx: TagContext, markers: MarkerDetector.Result, quantity: QuantityCandidate?): Field<String>? {
        val excluded = markers.markerLines + markers.labelLines
        val candidates = ctx.lines.indices
            .filter { ctx.lines[it].box.top < MAX_TOP && it !in excluded && !markers.flags[it].any }
            .filter { isWordy(ctx.normalized[it]) && !onlyLabelWords(ctx, it) && !cutByEdge(ctx.lines[it].box) }
            .sortedBy { ctx.lines[it].box.top }
        val first = candidates.firstOrNull() ?: return null
        val chosen = mutableListOf(first)
        for (next in candidates.drop(1)) {
            if (chosen.size == MAX_LINES || !continues(ctx, chosen.last(), next)) break
            chosen += next
        }
        val text = chosen.joinToString(" ") { lineText(ctx, it, quantity) }.replace(SPACES, " ").trim()
        if (text.isEmpty()) return null
        val confidence = NAME_CONFIDENCE * chosen.map(ctx::confidence).average().toFloat()
        return Field(text, confidence, emptyList(), chosen)
    }

    /** "ЦІНА", "при", "АТБ", a lone "грн": the tag's own labels and currency. */
    private fun onlyLabelWords(ctx: TagContext, line: Int): Boolean {
        val words = ctx.tokens[line].filter { it.kind == TokenKind.CURRENCY || it.text.any(Char::isLetter) }
        return words.isNotEmpty() && words.all { it.kind == TokenKind.CURRENCY || ctx.pack.isLabelWord(it.text) }
    }

    /** Text running into the left or right edge of the frame is packaging behind the tag. */
    private fun cutByEdge(box: Box): Boolean = box.left <= EDGE || box.right >= 1f - EDGE

    /** Enough letters, and more letters than digits: "Код: 128718", dates and barcodes are not names. */
    private fun isWordy(text: String): Boolean {
        val letters = text.count(Char::isLetter)
        return letters >= MIN_LETTERS && letters > text.count(Char::isDigit)
    }

    private fun continues(ctx: TagContext, previous: Int, next: Int): Boolean {
        val a = ctx.lines[previous].box
        val b = ctx.lines[next].box
        val closeBelow = b.top <= a.bottom + a.height * GAP_FACTOR
        val similarType = b.height <= a.height * HEIGHT_RATIO && a.height <= b.height * HEIGHT_RATIO
        return closeBelow && similarType
    }

    private fun lineText(ctx: TagContext, line: Int, quantity: QuantityCandidate?): String {
        val text = ctx.normalized[line]
        if (quantity == null || quantity.line != line) return text
        val tokens = ctx.tokens[line].filter { it.id in quantity.tokens }
        return text.removeRange(tokens.minOf { it.start }, tokens.maxOf { it.end })
    }
}

/** Steps 7, 9 and 10: weighted goods, cross-check, derived quantity and the final result. */
private class Assembler(
    private val prices: PriceSelector.Prices,
    private val quantity: Field<Quantity>?,
    private val printed: UnitPriceCandidate?,
    private val weightedReference: Quantity?,
    private val name: Field<String>?,
    private val weightedByMarker: Boolean,
) {
    private class Resolved(val price: Field<Money>, val quantity: Field<Quantity>, val weighted: Boolean)

    fun assemble(): ParseResult {
        val price = prices.current?.let { Field(it.money, it.confidence, prices.alternatives, it.lines) }
        val printedField = printed?.let { Field(it.unitPrice, it.confidence, emptyList(), it.lines) }
        val old = prices.old?.let { Field(it.money, it.confidence, emptyList(), it.lines) }
        val resolved = resolve(price) ?: return incomplete(price, old, printedField)
        var overall = minOf(resolved.price.confidence, resolved.quantity.confidence) * prices.confidenceFactor
        val unitPrice = UnitPriceCalculator.calculate(resolved.price.value, resolved.quantity.value)
        if (printed != null && !resolved.weighted && unitPrice.relativeDifference(printed.unitPrice) > TOLERANCE) {
            overall *= MISMATCH_PENALTY
        }
        val weighted = resolved.weighted || weightedByMarker
        val tag = ParsedTag(resolved.price, old, resolved.quantity, printedField, name, weighted)
        return ParseResult.Success(tag, unitPrice, overall)
    }

    /** Price, quantity and the weighted flag, or null when a unit price cannot be computed. */
    private fun resolve(price: Field<Money>?): Resolved? = when {
        price != null && quantity != null -> Resolved(price, quantity, false)
        printed != null && (price == null || price.value == printed.money) -> weightedFromMarker(price, printed)
        price == null -> null
        printed != null -> derived(price, printed)
        weightedReference != null -> weightedFromReference(price, weightedReference)
        else -> null
    }

    private fun weightedFromMarker(price: Field<Money>?, marker: UnitPriceCandidate): Resolved {
        val fromMarker = Field(marker.money, marker.confidence, price?.alternatives.orEmpty(), marker.lines)
        val reference = Field(marker.reference, marker.confidence, emptyList(), marker.lines)
        return Resolved(price ?: fromMarker, reference, true)
    }

    /** No quantity printed: price divided by the printed unit price gives the package size. */
    private fun derived(price: Field<Money>, marker: UnitPriceCandidate): Resolved? {
        val baseValue = (price.value.minor / marker.unitPrice.minorPerBaseUnit).roundToInt().toDouble()
        if (baseValue <= 0.0) return null
        val quantity = Quantity(baseValue, marker.unitPrice.dimension.baseUnit)
        val confidence = minOf(price.confidence, marker.confidence) * DERIVED_CONFIDENCE
        return Resolved(price, Field(quantity, confidence, emptyList(), marker.lines), false)
    }

    private fun weightedFromReference(price: Field<Money>, reference: Quantity): Resolved =
        Resolved(price, Field(reference, price.confidence * WEIGHTED_MARKER_CONFIDENCE, emptyList(), emptyList()), true)

    private fun incomplete(price: Field<Money>?, old: Field<Money>?, printedField: Field<UnitPrice>?): ParseResult {
        val tag = ParsedTag(price, old, quantity, printedField, name, weightedByMarker)
        return when {
            price != null -> ParseResult.NeedsInput(tag, setOf(FieldKind.QUANTITY))
            quantity != null -> ParseResult.NeedsInput(tag, setOf(FieldKind.PRICE))
            else -> ParseResult.Nothing
        }
    }

    private companion object {
        const val TOLERANCE = 0.03
        const val MISMATCH_PENALTY = 0.7f
        const val DERIVED_CONFIDENCE = 0.6f
        const val WEIGHTED_MARKER_CONFIDENCE = 0.85f
    }
}

/** One kilogram or one litre: the reference amount goods sold by weight are priced for. */
private fun Quantity.isWholeUnit(): Boolean =
    value == 1.0 && (unit == MeasureUnit.KILOGRAM || unit == MeasureUnit.LITRE)
