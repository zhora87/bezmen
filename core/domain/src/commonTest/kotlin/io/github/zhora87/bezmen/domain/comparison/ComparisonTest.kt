package io.github.zhora87.bezmen.domain.comparison

import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComparisonTest {
    private fun item(id: String, price: Money, quantity: Quantity) = ComparisonItem(id, null, price, quantity)

    @Test
    fun `ranks by unit price and reports delta from the cheapest`() {
        val items = listOf(
            item("a", Money.of(249, 90, "RUB"), Quantity(900.0, MeasureUnit.MILLILITRE)),
            item("b", Money.of(259, 0, "RUB"), Quantity(1.0, MeasureUnit.LITRE)),
            item("c", Money.of(99, 0, "RUB"), Quantity(330.0, MeasureUnit.MILLILITRE)),
        )

        val ranked = Comparison.rank(items)

        assertEquals(listOf("b", "a", "c"), ranked.map { it.item.id })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals(0.0, ranked[0].deltaPercent!!, 1e-9)
        assertEquals(7.2, ranked[1].deltaPercent!!, 0.05)
        assertEquals(15.8, ranked[2].deltaPercent!!, 0.05)
        assertTrue(ranked.all { it.isComparable })
    }

    @Test
    fun `items of another dimension are listed last as not comparable`() {
        val items = listOf(
            item("g1", Money.of(100, 0, "RUB"), Quantity(500.0, MeasureUnit.GRAM)),
            item("ml", Money.of(100, 0, "RUB"), Quantity(500.0, MeasureUnit.MILLILITRE)),
            item("g2", Money.of(150, 0, "RUB"), Quantity(1.0, MeasureUnit.KILOGRAM)),
        )

        val ranked = Comparison.rank(items)

        assertEquals(listOf("g2", "g1", "ml"), ranked.map { it.item.id })
        assertFalse(ranked.last().isComparable)
        assertNull(ranked.last().rank)
        assertNull(ranked.last().deltaPercent)
    }

    @Test
    fun `empty input gives empty output`() {
        assertEquals(emptyList(), Comparison.rank(emptyList()))
    }
}
