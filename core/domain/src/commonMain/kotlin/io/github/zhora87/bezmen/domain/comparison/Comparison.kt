package io.github.zhora87.bezmen.domain.comparison

import io.github.zhora87.bezmen.domain.Dimension
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.UnitPrice
import io.github.zhora87.bezmen.domain.UnitPriceCalculator

/** One scanned product in a comparison session. */
data class ComparisonItem(val id: String, val name: String?, val price: Money, val quantity: Quantity) {
    val unitPrice: UnitPrice get() = UnitPriceCalculator.calculate(price, quantity)
}

/** [rank] and [deltaPercent] are null for items that cannot be compared with the rest. */
data class RankedItem(val item: ComparisonItem, val rank: Int?, val deltaPercent: Double?, val isComparable: Boolean)

object Comparison {
    /**
     * Cheapest per base unit first. Items of another dimension or currency than the dominant one are
     * appended as not comparable (docs/parsing.md, Units).
     */
    fun rank(items: List<ComparisonItem>, dimension: Dimension? = null): List<RankedItem> {
        if (items.isEmpty()) return emptyList()
        val targetDimension = dimension ?: dominantDimension(items)
        val currency = items.first().price.currency
        val comparable = items
            .filter { it.quantity.dimension == targetDimension && it.price.currency == currency }
            .sortedBy { it.unitPrice.minorPerBaseUnit }
        val best = comparable.firstOrNull()?.unitPrice?.minorPerBaseUnit
        val ranked = comparable.mapIndexed { index, item ->
            RankedItem(item, index + 1, best?.let { deltaPercent(item.unitPrice.minorPerBaseUnit, it) }, true)
        }
        val rest = items.filterNot { it in comparable }.map { RankedItem(it, null, null, false) }
        return ranked + rest
    }

    private fun deltaPercent(value: Double, best: Double): Double =
        if (best == 0.0) 0.0 else (value - best) / best * PERCENT

    private fun dominantDimension(items: List<ComparisonItem>): Dimension =
        items.groupingBy { it.quantity.dimension }.eachCount().maxBy { it.value }.key

    private const val PERCENT = 100.0
}
