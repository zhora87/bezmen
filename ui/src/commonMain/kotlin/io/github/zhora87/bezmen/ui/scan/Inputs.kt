package io.github.zhora87.bezmen.ui.scan

import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity

/** Parses what a person types into the price field: "249,90", "249.9", "1 299", "249". */
object MoneyInput {
    private val PATTERN = Regex("""^(\d{1,7})(?:[.,](\d{1,2}))?$""")
    private const val TENS = 10

    fun parse(text: String, currency: String): Money? {
        val match = PATTERN.matchEntire(text.filterNot { it.isWhitespace() }) ?: return null
        val major = match.groupValues[1].toLong()
        val digits = match.groupValues[2]
        val fraction = when (digits.length) {
            0 -> 0
            1 -> digits.toInt() * TENS
            else -> digits.toInt()
        }
        return Money.of(major, fraction, currency)
    }
}

/** Parses the quantity field: a positive number with comma or dot, in the unit picked next to it. */
object QuantityInput {
    private val PATTERN = Regex("""^\d{1,6}(?:[.,]\d{1,3})?$""")

    fun parse(text: String, unit: MeasureUnit?): Quantity? {
        val clean = text.filterNot { it.isWhitespace() }
        if (unit == null || !PATTERN.matches(clean)) return null
        val value = clean.replace(',', '.').toDouble()
        return if (value > 0) Quantity(value, unit) else null
    }

    /** "900", "0,5", "1,25": no trailing zeros, comma as the separator. */
    fun format(value: Double): String {
        val whole = value.toLong()
        if (value == whole.toDouble()) return whole.toString()
        return value.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }
}
