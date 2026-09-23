package io.github.zhora87.bezmen.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuantityTest {
    @Test
    fun `kilograms convert to grams`() {
        val base = Quantity(1.5, MeasureUnit.KILOGRAM).toBase()

        assertEquals(MeasureUnit.GRAM, base.unit)
        assertEquals(1500.0, base.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun `litres convert to millilitres`() {
        val base = Quantity(0.33, MeasureUnit.LITRE).toBase()

        assertEquals(MeasureUnit.MILLILITRE, base.unit)
        assertEquals(330.0, base.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun `dozen converts to pieces`() {
        val base = Quantity(1.0, MeasureUnit.DOZEN).toBase()

        assertEquals(MeasureUnit.PIECE, base.unit)
        assertEquals(12.0, base.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun `base unit stays unchanged`() {
        val grams = Quantity(250.0, MeasureUnit.GRAM)

        assertEquals(grams, grams.toBase())
    }

    @Test
    fun `every unit belongs to a dimension whose base unit is in the same dimension`() {
        MeasureUnit.entries.forEach { unit ->
            assertEquals(unit.dimension, unit.dimension.baseUnit.dimension, "base unit of ${unit.name}")
            assertEquals(1.0, unit.dimension.baseUnit.factorToBase, "base factor of ${unit.dimension}")
        }
    }

    @Test
    fun `non-positive quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { Quantity(0.0, MeasureUnit.GRAM) }
        assertFailsWith<IllegalArgumentException> { Quantity(-1.0, MeasureUnit.LITRE) }
    }
}
