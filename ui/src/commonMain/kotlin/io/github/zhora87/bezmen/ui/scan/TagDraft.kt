package io.github.zhora87.bezmen.ui.scan

import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.References
import io.github.zhora87.bezmen.domain.UnitPriceCalculator
import io.github.zhora87.bezmen.domain.parser.FieldKind
import io.github.zhora87.bezmen.domain.parser.ParseResult

/** The unit price shown in large type: [amount] for [reference] ("5,54 ₴ за 100 мл"). */
data class DisplayPrice(val amount: Money, val reference: Quantity)

/**
 * The result card being reviewed. Fields are kept as typed text so a half-typed value never throws;
 * the price, quantity and unit price are derived from the text on every edit.
 */
data class TagDraft(
    val name: String,
    val priceText: String,
    val quantityText: String,
    val unit: MeasureUnit?,
    val currency: String,
    val oldPrice: Money?,
    val isWeighted: Boolean,
    /** The parser was unsure; the card highlights the fields and asks to check them. */
    val needsCheck: Boolean,
    /** Price with the store's card or app, empty when the tag has none. */
    val cardPriceText: String = "",
) {
    val price: Money? get() = MoneyInput.parse(priceText, currency)
    val quantity: Quantity? get() = QuantityInput.parse(quantityText, unit)

    val missing: Set<FieldKind>
        get() = buildSet {
            if (price == null) add(FieldKind.PRICE)
            if (quantity == null) add(FieldKind.QUANTITY)
        }

    val cardPrice: Money? get() = MoneyInput.parse(cardPriceText, currency)

    /** Per 100 g, 100 ml or 1 piece; weighted goods keep the reference printed on the tag. */
    fun displayPrice(): DisplayPrice? = display(price)

    /** The same for the card price, with the same quantity. */
    fun displayCardPrice(): DisplayPrice? = display(cardPrice)

    private fun display(amount: Money?): DisplayPrice? {
        val p = amount ?: return null
        val q = quantity ?: return null
        if (isWeighted) return DisplayPrice(p, q)
        val reference = References.defaultFor(q.dimension)
        return DisplayPrice(UnitPriceCalculator.calculate(p, q).per(reference), reference)
    }

    companion object {
        fun from(result: ParseResult, currency: String): TagDraft {
            val (tag, confident) = when (result) {
                is ParseResult.Success -> result.tag to (result.overall >= ParseResult.CONFIRM_THRESHOLD)
                is ParseResult.NeedsInput -> result.tag to false
                ParseResult.Nothing -> null to false
            }
            val quantity = tag?.quantity?.value
            return TagDraft(
                name = tag?.name?.value.orEmpty(),
                priceText = tag?.price?.value?.format().orEmpty(),
                quantityText = quantity?.let { QuantityInput.format(it.value) }.orEmpty(),
                unit = quantity?.unit,
                currency = currency,
                oldPrice = tag?.oldPrice?.value,
                isWeighted = tag?.isWeighted ?: false,
                needsCheck = !confident,
                cardPriceText = tag?.loyaltyPrice?.value?.format().orEmpty(),
            )
        }
    }
}
