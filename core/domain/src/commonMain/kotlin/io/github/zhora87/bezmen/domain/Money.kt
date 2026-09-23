package io.github.zhora87.bezmen.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/** An amount in minor units (kopecks, cents) of one currency. Never negative. */
@Serializable
data class Money(val minor: Long, val currency: String) : Comparable<Money> {
    init {
        require(minor >= 0) { "Money cannot be negative: $minor" }
        require(currency.isNotBlank()) { "Currency code is required" }
    }

    val major: Long get() = minor / MINOR_PER_MAJOR
    val fraction: Int get() = (minor % MINOR_PER_MAJOR).toInt()

    fun toDouble(): Double = minor / MINOR_PER_MAJOR.toDouble()

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
    }

    /** "249,90" style, without the currency. */
    fun format(decimalSeparator: Char = ','): String = "$major$decimalSeparator${fraction.toString().padStart(2, '0')}"

    companion object {
        const val MINOR_PER_MAJOR = 100L

        fun of(major: Long, fraction: Int, currency: String): Money {
            require(fraction in 0 until MINOR_PER_MAJOR) { "Fraction out of range: $fraction" }
            return Money(major * MINOR_PER_MAJOR + fraction, currency)
        }

        fun fromDouble(amount: Double, currency: String): Money =
            Money((amount * MINOR_PER_MAJOR).roundToLong(), currency)
    }
}
