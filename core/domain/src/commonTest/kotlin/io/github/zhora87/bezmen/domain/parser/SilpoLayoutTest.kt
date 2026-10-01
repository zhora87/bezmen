package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shapes taken from Silpo tags: quantity inside the name, multi-buy prices, cashback banner. */
class SilpoLayoutTest {
    private val uk = PriceTagParser(TestPacks.uk)

    private fun ParseResult.tagOrNull() = when (this) {
        is ParseResult.Success -> tag
        is ParseResult.NeedsInput -> tag
        ParseResult.Nothing -> null
    }

    @Test
    fun `kopecks under a discount label that carries its own percentage stay kopecks`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Кеш'ю Winway", Box(0.16f, 0.15f, 0.57f, 0.27f), 0.95f),
                OcrLine("смажений, 100г", Box(0.17f, 0.25f, 0.59f, 0.36f), 0.98f),
                OcrLine("3Hижka -15%", Box(0.62f, 0.42f, 0.85f, 0.49f), 0.72f),
                OcrLine("139", Box(0.48f, 0.47f, 0.76f, 0.73f), 1f),
                OcrLine("164", Box(0.17f, 0.49f, 0.39f, 0.72f), 1f),
                OcrLine("00", Box(0.75f, 0.49f, 0.86f, 0.63f), 0.99f),
                OcrLine("00", Box(0.33f, 0.49f, 0.44f, 0.62f), 0.73f),
                OcrLine("ГРН", Box(0.37f, 0.63f, 0.44f, 0.70f), 0.81f),
                OcrLine("ГРН", Box(0.76f, 0.65f, 0.84f, 0.72f), 0.73f),
            ),
        ).tagOrNull()

        assertEquals(Money.of(139, 0, "UAH"), tag?.price?.value)
        assertEquals(Money.of(164, 0, "UAH"), tag?.oldPrice?.value)
        assertEquals(Quantity(100.0, MeasureUnit.GRAM), tag?.quantity?.value)
    }

    @Test
    fun `multi-buy price is not the price and its count is not the quantity`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Премія,", Box(0.40f, 0.16f, 0.66f, 0.29f), 0.91f),
                OcrLine("Базилік", Box(0.13f, 0.17f, 0.40f, 0.28f), 0.91f),
                OcrLine("10r", Box(0.13f, 0.28f, 0.25f, 0.39f), 0.88f),
                OcrLine("Від 3 шт.", Box(0.60f, 0.49f, 0.81f, 0.60f), 0.92f),
                OcrLine("2ШТ.", Box(0.27f, 0.51f, 0.42f, 0.59f), 0.69f),
                OcrLine("До", Box(0.22f, 0.51f, 0.30f, 0.60f), 0.77f),
                OcrLine("27", Box(0.60f, 0.57f, 0.80f, 0.82f), 1f),
                OcrLine("31", Box(0.25f, 0.61f, 0.42f, 0.81f), 1f),
                OcrLine("19", Box(0.80f, 0.61f, 0.88f, 0.72f), 1f),
                OcrLine("99", Box(0.42f, 0.62f, 0.50f, 0.73f), 1f),
                OcrLine("ГРН", Box(0.42f, 0.74f, 0.50f, 0.81f), 0.6f),
                OcrLine("ГРН", Box(0.79f, 0.74f, 0.88f, 0.82f), 0.63f),
            ),
        ).tagOrNull()

        assertEquals(Money.of(31, 99, "UAH"), tag?.price?.value)
        assertNull(tag?.oldPrice)
        assertNull(tag?.loyaltyPrice)
        assertEquals(Quantity(10.0, MeasureUnit.GRAM), tag?.quantity?.value)
        assertEquals("Базилік Премія,", tag?.name?.value)
    }

    @Test
    fun `multi-buy price on one line with its label is ignored too`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Борошно Сквирянка 800 г пак.", Box(0.05f, 0.08f, 0.70f, 0.18f), 0.9f),
                OcrLine("від 3 шт. 43,50", Box(0.50f, 0.30f, 0.80f, 0.40f), 0.8f),
                OcrLine("44", Box(0.35f, 0.50f, 0.60f, 0.85f), 1f),
                OcrLine("80", Box(0.60f, 0.48f, 0.72f, 0.65f), 1f),
            ),
        ).tagOrNull()

        assertEquals(Money.of(44, 80, "UAH"), tag?.price?.value)
        assertNull(tag?.oldPrice)
        assertEquals(Quantity(800.0, MeasureUnit.GRAM), tag?.quantity?.value)
    }

    @Test
    fun `a validity line with the same word is not a multi-buy label`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Вода до чаю 2 шт", Box(0.05f, 0.08f, 0.70f, 0.18f), 0.9f),
                OcrLine("44", Box(0.35f, 0.50f, 0.60f, 0.85f), 1f),
                OcrLine("80", Box(0.60f, 0.48f, 0.72f, 0.65f), 1f),
                OcrLine("Діє з 22.09.2026 до 05.10.2026", Box(0.05f, 0.88f, 0.60f, 0.94f), 0.9f),
            ),
        ).tagOrNull()

        assertEquals(Quantity(2.0, MeasureUnit.PIECE), tag?.quantity?.value)
    }

    @Test
    fun `cashback banner is not part of the name`() {
        val tag = uk.parse(
            listOf(
                OcrLine("НАЦІОНАЛЬНИЙ КЕШБЕК", Box(0.18f, 0.00f, 0.80f, 0.16f), 0.8f),
                OcrLine("Чипси Повна", Box(0.09f, 0.05f, 0.37f, 0.19f), 0.98f),
                OcrLine("Чаша", Box(0.36f, 0.10f, 0.50f, 0.18f), 0.97f),
                OcrLine("бананові", Box(0.48f, 0.10f, 0.67f, 0.21f), 0.83f),
                OcrLine("смажені,", Box(0.10f, 0.16f, 0.28f, 0.24f), 0.9f),
                OcrLine("200р", Box(0.27f, 0.16f, 0.37f, 0.25f), 0.84f),
                OcrLine("82", Box(0.42f, 0.35f, 0.73f, 0.79f), 1f),
                OcrLine("49", Box(0.73f, 0.45f, 0.84f, 0.62f), 1f),
                OcrLine("ГРН", Box(0.72f, 0.66f, 0.84f, 0.77f), 0.79f),
            ),
        ).tagOrNull()

        assertEquals(Money.of(82, 49, "UAH"), tag?.price?.value)
        assertEquals(Quantity(200.0, MeasureUnit.GRAM), tag?.quantity?.value)
        assertTrue(tag?.name?.value.orEmpty().startsWith("Чипси Повна Чаша бананові"), "name: ${tag?.name?.value}")
    }
}
