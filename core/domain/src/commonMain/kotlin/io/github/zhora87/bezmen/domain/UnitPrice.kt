package io.github.zhora87.bezmen.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** Price per one base unit of a dimension (per gram, per millilitre, per piece), kept unrounded. */
@Serializable
data class UnitPrice(val minorPerBaseUnit: Double, val dimension: Dimension, val currency: String) {
    init {
        require(minorPerBaseUnit >= 0) { "Unit price cannot be negative" }
    }

    /** The price of a reference amount, e.g. per 100 g or per 1 l, rounded to minor units. */
    fun per(reference: Quantity): Money {
        require(reference.dimension == dimension) { "Reference ${reference.unit} is not $dimension" }
        return Money((minorPerBaseUnit * reference.toBase().value).roundToLong(), currency)
    }

    /** |this - other| / other, as a fraction. Used for cross-checking against a printed unit price. */
    fun relativeDifference(other: UnitPrice): Double =
        abs(minorPerBaseUnit - other.minorPerBaseUnit) / max(other.minorPerBaseUnit, EPSILON)

    private companion object {
        const val EPSILON = 1e-9
    }
}

object UnitPriceCalculator {
    fun calculate(price: Money, quantity: Quantity): UnitPrice =
        UnitPrice(price.minor / quantity.toBase().value, quantity.dimension, price.currency)
}

/** Reference amounts the UI displays unit prices for. */
object References {
    val PER_100_G = Quantity(100.0, MeasureUnit.GRAM)
    val PER_1_KG = Quantity(1.0, MeasureUnit.KILOGRAM)
    val PER_100_ML = Quantity(100.0, MeasureUnit.MILLILITRE)
    val PER_1_L = Quantity(1.0, MeasureUnit.LITRE)
    val PER_PIECE = Quantity(1.0, MeasureUnit.PIECE)

    /** The default display reference for a dimension: 100 g, 100 ml, 1 piece. */
    fun defaultFor(dimension: Dimension): Quantity = when (dimension) {
        Dimension.MASS -> PER_100_G
        Dimension.VOLUME -> PER_100_ML
        Dimension.COUNT -> PER_PIECE
    }
}
