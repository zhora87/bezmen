package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.UnitPrice

internal data class MoneyCandidate(
    val money: Money,
    val confidence: Float,
    val lines: List<Int>,
    val box: Box,
    val hasCurrency: Boolean,
    val tokens: List<TokenId>,
)

internal data class QuantityCandidate(
    val quantity: Quantity,
    val confidence: Float,
    val line: Int,
    val box: Box,
    val tokens: List<TokenId>,
    val isMultipack: Boolean,
    /** "грн/кг" without a number: one of the unit, and for kg or l goods sold by weight. */
    val isBareUnit: Boolean = false,
)

internal data class UnitPriceCandidate(
    val unitPrice: UnitPrice,
    val reference: Quantity,
    val money: Money,
    val confidence: Float,
    val lines: List<Int>,
)

internal data class LineFlags(val discount: Boolean, val oldPrice: Boolean, val loyalty: Boolean) {
    val any: Boolean get() = discount || oldPrice || loyalty
}
