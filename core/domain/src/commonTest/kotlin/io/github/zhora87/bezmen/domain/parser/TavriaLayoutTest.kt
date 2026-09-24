package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals

/** Shapes taken from Tavria V tags: italic price, raised kopecks, quantity inside the name. */
class TavriaLayoutTest {
    private val uk = PriceTagParser(TestPacks.uk)

    private fun ParseResult.tagOrNull() = when (this) {
        is ParseResult.Success -> tag
        is ParseResult.NeedsInput -> tag
        ParseResult.Nothing -> null
    }

    @Test
    fun `kopecks raised above the top of the price digits still belong to it`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Вода Єврогруп 1,5 л с/газ.", Box(0.03f, 0.05f, 0.62f, 0.21f), 0.94f),
                OcrLine("2890", Box(0.66f, 0.41f, 0.83f, 0.57f), 0.82f),
                OcrLine("50", Box(0.67f, 0.52f, 0.82f, 0.68f), 1f),
                OcrLine("-12%", Box(0.05f, 0.50f, 0.20f, 0.66f), 0.89f),
                OcrLine("25", Box(0.49f, 0.57f, 0.69f, 0.78f), 1f),
            ),
        ).tagOrNull()

        assertEquals(Money.of(25, 50, "UAH"), tag?.price?.value)
        assertEquals(Quantity(1.5, MeasureUnit.LITRE), tag?.quantity?.value)
    }

    @Test
    fun `price read with its kopecks in one block takes no second kopecks`() {
        // "34⁹⁰" read as "3490"; the crossed-out "41⁹⁰" above left a lone "90".
        val tag = uk.parse(
            listOf(
                OcrLine("Пиво Уманьпиво 0,5 л Віденське", Box(0.05f, 0.10f, 0.70f, 0.20f), 0.9f),
                OcrLine("90", Box(0.78f, 0.52f, 0.86f, 0.60f), 0.9f),
                OcrLine("3490", Box(0.52f, 0.58f, 0.86f, 0.85f), 0.95f),
            ),
        ).tagOrNull()

        assertEquals(Money.of(34, 90, "UAH"), tag?.price?.value)
    }
}
