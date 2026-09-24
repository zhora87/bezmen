package io.github.zhora87.bezmen.ui.scan

import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.UnitPriceCalculator
import io.github.zhora87.bezmen.domain.parser.Field
import io.github.zhora87.bezmen.domain.parser.FieldKind
import io.github.zhora87.bezmen.domain.parser.ParseResult
import io.github.zhora87.bezmen.domain.parser.ParsedTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagDraftTest {
    private val milk = ParsedTag(
        price = Field(Money.of(49, 90, "UAH"), 0.9f),
        quantity = Field(Quantity(900.0, MeasureUnit.MILLILITRE), 0.9f),
        name = Field("Молоко 2,5%", 0.6f),
    )

    private fun success(tag: ParsedTag, overall: Float): ParseResult.Success {
        val unitPrice = UnitPriceCalculator.calculate(checkNotNull(tag.price).value, checkNotNull(tag.quantity).value)
        return ParseResult.Success(tag, unitPrice, overall)
    }

    @Test
    fun `confident parse fills the draft and needs no check`() {
        val result = success(milk, 0.9f)

        val draft = TagDraft.from(result, "UAH")

        assertEquals("49,90", draft.priceText)
        assertEquals("900", draft.quantityText)
        assertEquals(MeasureUnit.MILLILITRE, draft.unit)
        assertEquals("Молоко 2,5%", draft.name)
        assertFalse(draft.needsCheck)
        assertEquals(Money(554, "UAH"), draft.displayPrice()?.amount)
    }

    @Test
    fun `low confidence asks the user to check`() {
        val result = success(milk, 0.5f)

        assertTrue(TagDraft.from(result, "UAH").needsCheck)
    }

    @Test
    fun `missing quantity leaves the field empty and no unit price`() {
        val result = ParseResult.NeedsInput(milk.copy(quantity = null), setOf(FieldKind.QUANTITY))

        val draft = TagDraft.from(result, "UAH")

        assertEquals("", draft.quantityText)
        assertEquals(setOf(FieldKind.QUANTITY), draft.missing)
        assertNull(draft.displayPrice())
    }

    @Test
    fun `editing the quantity recomputes the unit price`() {
        val result = ParseResult.NeedsInput(milk.copy(quantity = null), setOf(FieldKind.QUANTITY))

        val draft = TagDraft.from(result, "UAH").copy(quantityText = "1", unit = MeasureUnit.LITRE)

        assertEquals(emptySet(), draft.missing)
        assertEquals(Money(499, "UAH"), draft.displayPrice()?.amount)
        assertEquals(Quantity(100.0, MeasureUnit.MILLILITRE), draft.displayPrice()?.reference)
    }

    @Test
    fun `price input accepts comma, dot, spaces and whole numbers`() {
        assertEquals(Money(24990, "UAH"), MoneyInput.parse("249,90", "UAH"))
        assertEquals(Money(24990, "UAH"), MoneyInput.parse(" 249.9 ", "UAH"))
        assertEquals(Money(24900, "UAH"), MoneyInput.parse("249", "UAH"))
        assertEquals(Money(129900, "UAH"), MoneyInput.parse("1 299,00", "UAH"))
        assertNull(MoneyInput.parse("", "UAH"))
        assertNull(MoneyInput.parse("12,345", "UAH"))
        assertNull(MoneyInput.parse("abc", "UAH"))
    }

    @Test
    fun `quantity input rejects zero and garbage`() {
        assertEquals(Quantity(0.5, MeasureUnit.KILOGRAM), QuantityInput.parse("0,5", MeasureUnit.KILOGRAM))
        assertNull(QuantityInput.parse("0", MeasureUnit.KILOGRAM))
        assertNull(QuantityInput.parse("много", MeasureUnit.GRAM))
        assertNull(QuantityInput.parse("5", null))
    }

    @Test
    fun `weighted goods show the price per their printed reference`() {
        val tag = ParsedTag(
            price = Field(Money.of(33, 59, "UAH"), 0.9f),
            quantity = Field(Quantity(100.0, MeasureUnit.GRAM), 0.5f),
            isWeighted = true,
        )
        val result = success(tag, 0.8f)

        val shown = TagDraft.from(result, "UAH").displayPrice()

        assertEquals(Money(3359, "UAH"), shown?.amount)
        assertEquals(Quantity(100.0, MeasureUnit.GRAM), shown?.reference)
    }

    @Test
    fun `card price gets its own unit price with the same quantity`() {
        val tag = ParsedTag(
            price = Field(Money.of(94, 90, "UAH"), 0.9f),
            quantity = Field(Quantity(440.0, MeasureUnit.GRAM), 0.9f),
            loyaltyPrice = Field(Money.of(85, 41, "UAH"), 0.8f),
        )

        val draft = TagDraft.from(success(tag, 0.9f), "UAH")

        assertEquals("85,41", draft.cardPriceText)
        assertEquals(Money(2157, "UAH"), draft.displayPrice()?.amount)
        assertEquals(Money(1941, "UAH"), draft.displayCardPrice()?.amount)
    }

    @Test
    fun `no card price, or an unreadable one, shows nothing extra`() {
        val draft = TagDraft.from(success(milk, 0.9f), "UAH")

        assertEquals("", draft.cardPriceText)
        assertNull(draft.displayCardPrice())
        assertNull(draft.copy(cardPriceText = "8541abc").displayCardPrice())
    }
}
