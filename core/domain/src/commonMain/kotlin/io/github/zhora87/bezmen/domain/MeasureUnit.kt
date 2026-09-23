package io.github.zhora87.bezmen.domain

import kotlinx.serialization.Serializable

/** Physical dimension a quantity is measured in. Quantities of different dimensions never compare. */
enum class Dimension(val baseUnit: MeasureUnit) {
    MASS(MeasureUnit.GRAM),
    VOLUME(MeasureUnit.MILLILITRE),
    COUNT(MeasureUnit.PIECE),
}

/**
 * Units a price tag can express a quantity in. `factorToBase` converts into the dimension's
 * base unit (gram, millilitre, piece). `code` is the key locale packs use for alias lists;
 * spelling variants per country live there (docs/parsing.md, Locale pack format).
 */
enum class MeasureUnit(val code: String, val dimensionName: String, val factorToBase: Double) {
    MILLIGRAM("mg", "MASS", 0.001),
    GRAM("g", "MASS", 1.0),
    KILOGRAM("kg", "MASS", 1000.0),
    POUND("lb", "MASS", 453.59237),
    MILLILITRE("ml", "VOLUME", 1.0),
    CENTILITRE("cl", "VOLUME", 10.0),
    LITRE("l", "VOLUME", 1000.0),
    PIECE("pc", "COUNT", 1.0),
    PAIR("pair", "COUNT", 2.0),
    DOZEN("dozen", "COUNT", 12.0),
    ;

    val dimension: Dimension get() = Dimension.valueOf(dimensionName)

    companion object {
        fun fromCode(code: String): MeasureUnit? = entries.firstOrNull { it.code == code }
    }
}

/** A quantity as printed on a price tag: 0.33 l, 900 g, 6 x 200 g (already multiplied out). */
@Serializable
data class Quantity(val value: Double, val unit: MeasureUnit) {
    init {
        require(value > 0) { "Quantity must be positive, got $value ${unit.name}" }
    }

    val dimension: Dimension get() = unit.dimension

    /** The same quantity expressed in the dimension's base unit. */
    fun toBase(): Quantity = Quantity(value * unit.factorToBase, dimension.baseUnit)
}
