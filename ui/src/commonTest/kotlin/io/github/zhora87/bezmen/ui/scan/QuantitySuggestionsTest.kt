package io.github.zhora87.bezmen.ui.scan

import io.github.zhora87.bezmen.domain.Dimension
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuantitySuggestionsTest {
    private fun draft(quantity: String, unit: MeasureUnit?) = TagDraft(
        name = "",
        priceText = "10,00",
        quantityText = quantity,
        unit = unit,
        currency = "UAH",
        oldPrice = null,
        isWeighted = false,
        needsCheck = false,
    )

    @Test
    fun `with no unit known the common package sizes come in shelf order`() {
        val all = QuantitySuggestions.forDraft(draft("", null))

        assertEquals(Quantity(100.0, MeasureUnit.GRAM), all.first())
        assertTrue(all.size in 8..12)
        assertEquals(all.size, all.distinct().size)
        assertTrue(all.any { it.unit == MeasureUnit.LITRE } && all.any { it.unit == MeasureUnit.PIECE })
    }

    @Test
    fun `sizes of the unit already picked come first`() {
        val litres = QuantitySuggestions.forDraft(draft("", MeasureUnit.MILLILITRE))

        assertEquals(Dimension.VOLUME, litres.first().dimension)
        assertTrue(litres.takeWhile { it.dimension == Dimension.VOLUME }.size >= 2)
    }

    @Test
    fun `picking a suggestion fills both the number and the unit`() {
        val filled = draft("", null).withQuantity(Quantity(0.5, MeasureUnit.LITRE))

        assertEquals("0,5", filled.quantityText)
        assertEquals(MeasureUnit.LITRE, filled.unit)
        assertEquals(Quantity(0.5, MeasureUnit.LITRE), filled.quantity)
    }
}
