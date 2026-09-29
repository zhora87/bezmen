package io.github.zhora87.bezmen.ui.scan

import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Quantity

/**
 * Package sizes offered as one-tap chips when OCR missed the quantity. The list follows how often
 * each size turns up on shelf tags in the corpus; sizes of a unit the person already picked go first.
 */
object QuantitySuggestions {
    private val COMMON = listOf(
        Quantity(100.0, MeasureUnit.GRAM),
        Quantity(180.0, MeasureUnit.GRAM),
        Quantity(200.0, MeasureUnit.GRAM),
        Quantity(250.0, MeasureUnit.GRAM),
        Quantity(400.0, MeasureUnit.GRAM),
        Quantity(500.0, MeasureUnit.GRAM),
        Quantity(1.0, MeasureUnit.KILOGRAM),
        Quantity(0.5, MeasureUnit.LITRE),
        Quantity(1.0, MeasureUnit.LITRE),
        Quantity(1.0, MeasureUnit.PIECE),
    )

    fun forDraft(draft: TagDraft): List<Quantity> {
        val dimension = draft.unit?.dimension ?: return COMMON
        return COMMON.sortedBy { if (it.dimension == dimension) 0 else 1 }
    }
}

/** The draft with this quantity typed in, number and unit together. */
fun TagDraft.withQuantity(quantity: Quantity): TagDraft =
    copy(quantityText = QuantityInput.format(quantity.value), unit = quantity.unit)
