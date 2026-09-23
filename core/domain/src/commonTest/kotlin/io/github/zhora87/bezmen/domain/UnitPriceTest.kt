package io.github.zhora87.bezmen.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnitPriceTest {
    @Test
    fun `249,90 for 900 ml is 27,77 per 100 ml and 277,67 per litre`() {
        val unitPrice = UnitPriceCalculator.calculate(Money.of(249, 90, "RUB"), Quantity(900.0, MeasureUnit.MILLILITRE))

        assertEquals(Dimension.VOLUME, unitPrice.dimension)
        assertEquals(Money.of(27, 77, "RUB"), unitPrice.per(References.PER_100_ML))
        assertEquals(Money.of(277, 67, "RUB"), unitPrice.per(References.PER_1_L))
    }

    @Test
    fun `kilogram packs are converted through grams`() {
        val unitPrice = UnitPriceCalculator.calculate(Money.of(1200, 0, "RUB"), Quantity(1.5, MeasureUnit.KILOGRAM))

        assertEquals(Money.of(80, 0, "RUB"), unitPrice.per(References.PER_100_G))
        assertEquals(Money.of(800, 0, "RUB"), unitPrice.per(References.PER_1_KG))
    }

    @Test
    fun `pieces are priced per piece`() {
        val unitPrice = UnitPriceCalculator.calculate(Money.of(129, 0, "RUB"), Quantity(10.0, MeasureUnit.PIECE))

        assertEquals(Money.of(12, 90, "RUB"), unitPrice.per(References.PER_PIECE))
    }

    @Test
    fun `reference of another dimension is rejected`() {
        val unitPrice = UnitPriceCalculator.calculate(Money.of(100, 0, "RUB"), Quantity(1.0, MeasureUnit.LITRE))

        assertFailsWith<IllegalArgumentException> { unitPrice.per(References.PER_100_G) }
    }

    @Test
    fun `relative difference to a printed unit price`() {
        val computed = UnitPriceCalculator.calculate(Money.of(3, 49, "EUR"), Quantity(400.0, MeasureUnit.GRAM))
        val printed = UnitPriceCalculator.calculate(Money.of(8, 73, "EUR"), Quantity(1.0, MeasureUnit.KILOGRAM))

        assertTrue(computed.relativeDifference(printed) < 0.01)
    }

    @Test
    fun `default display references per dimension`() {
        assertEquals(References.PER_100_G, References.defaultFor(Dimension.MASS))
        assertEquals(References.PER_100_ML, References.defaultFor(Dimension.VOLUME))
        assertEquals(References.PER_PIECE, References.defaultFor(Dimension.COUNT))
    }
}
