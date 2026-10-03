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

    @Test
    fun `quantity wrapped to the next line of the name is joined`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Масло Mlekovita 82% Польське 200", Box(0.04f, 0.14f, 0.99f, 0.31f), 0.73f),
                OcrLine("г(Польща)", Box(0.05f, 0.26f, 0.34f, 0.37f), 0.88f),
                OcrLine("151", Box(0.30f, 0.50f, 0.64f, 0.81f), 1f),
                OcrLine("40", Box(0.60f, 0.51f, 0.75f, 0.65f), 1f),
            ),
        ).tagOrNull()

        assertEquals(Quantity(200.0, MeasureUnit.GRAM), tag?.quantity?.value)
        assertEquals(Money.of(151, 40, "UAH"), tag?.price?.value)
        assertEquals("Масло Mlekovita 82% Польське", tag?.name?.value)
    }

    @Test
    fun `a number above a unit elsewhere on the tag is not a wrapped quantity`() {
        // "Код 53640" at the right edge, "г" of some other text far to the left below it.
        val tag = uk.parse(
            listOf(
                OcrLine("Крупа гречана", Box(0.05f, 0.10f, 0.40f, 0.20f), 0.9f),
                OcrLine("33", Box(0.45f, 0.20f, 0.75f, 0.60f), 1f),
                OcrLine("60", Box(0.76f, 0.22f, 0.86f, 0.35f), 1f),
                OcrLine("КОД 53640", Box(0.75f, 0.70f, 0.95f, 0.75f), 0.9f),
                OcrLine("г", Box(0.30f, 0.76f, 0.33f, 0.80f), 0.5f),
            ),
        ).tagOrNull()

        assertEquals(null, tag?.quantity)
    }

    @Test
    fun `a stray unit letter under the price digits is not a wrapped quantity`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Яйце куряче", Box(0.14f, 0.13f, 0.47f, 0.29f), 0.96f),
                OcrLine("79", Box(0.53f, 0.15f, 0.76f, 0.56f), 1f),
                OcrLine("50", Box(0.74f, 0.16f, 0.86f, 0.38f), 1f),
                OcrLine("ррн/", Box(0.76f, 0.35f, 0.83f, 0.44f), 0.56f),
                OcrLine("10wT", Box(0.76f, 0.42f, 0.83f, 0.49f), 0.78f),
                OcrLine("п", Box(0.61f, 0.80f, 0.61f, 0.82f), 0.16f),
            ),
        ).tagOrNull()

        assertEquals(Quantity(10.0, MeasureUnit.PIECE), tag?.quantity?.value)
    }

    @Test
    fun `kopecks next to the price win over a multi-buy price's kopecks further up`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Борошно Сквирянка 800 г пак.", Box(0.10f, 0.14f, 0.76f, 0.29f), 0.97f),
                OcrLine("50", Box(0.69f, 0.40f, 0.74f, 0.47f), 1f),
                OcrLine("від 3шT.", Box(0.50f, 0.40f, 0.63f, 0.52f), 0.77f),
                OcrLine("4", Box(0.62f, 0.43f, 0.66f, 0.49f), 0.85f),
                OcrLine("80", Box(0.54f, 0.56f, 0.67f, 0.78f), 1f),
                OcrLine("44", Box(0.32f, 0.53f, 0.58f, 0.92f), 1f),
            ),
        ).tagOrNull()

        assertEquals(Money.of(44, 80, "UAH"), tag?.price?.value)
        assertEquals(Quantity(800.0, MeasureUnit.GRAM), tag?.quantity?.value)
    }

    @Test
    fun `multi-buy word misread by OCR still hides its count`() {
        // "від 3 шт." read as "вд 3шт."; the package size is in the name.
        val tag = uk.parse(
            listOf(
                OcrLine("700г", Box(0.77f, 0.09f, 0.92f, 0.21f), 0.92f),
                OcrLine("2,9%", Box(0.62f, 0.10f, 0.77f, 0.23f), 0.99f),
                OcrLine("Простонаше", Box(0.28f, 0.11f, 0.63f, 0.26f), 1f),
                OcrLine("Ряжанка", Box(0.05f, 0.14f, 0.29f, 0.25f), 0.97f),
                OcrLine("90", Box(0.70f, 0.33f, 0.82f, 0.47f), 0.99f),
                OcrLine("00", Box(0.79f, 0.33f, 0.85f, 0.41f), 0.98f),
                OcrLine("вд 3шт.", Box(0.56f, 0.35f, 0.72f, 0.48f), 0.68f),
                OcrLine("92", Box(0.35f, 0.51f, 0.65f, 0.90f), 1f),
                OcrLine("70", Box(0.64f, 0.52f, 0.77f, 0.70f), 1f),
            ),
        ).tagOrNull()

        assertEquals(Money.of(92, 70, "UAH"), tag?.price?.value)
        assertEquals(Quantity(700.0, MeasureUnit.GRAM), tag?.quantity?.value)
    }

    @Test
    fun `cashback logo and country of origin misread by OCR are not the name`() {
        val tag = uk.parse(
            listOf(
                OcrLine("Біфідойогурт Данон Активіа 1,5%", Box(0.04f, 0.10f, 0.80f, 0.22f), 0.95f),
                OcrLine("800 г бут. Класичний", Box(0.04f, 0.21f, 0.55f, 0.33f), 0.95f),
                OcrLine("нац!ональний", Box(0.09f, 0.34f, 0.29f, 0.44f), 0.91f),
                OcrLine("Украна", Box(0.80f, 0.34f, 0.92f, 0.44f), 0.9f),
                OcrLine("98", Box(0.32f, 0.54f, 0.68f, 0.95f), 1f),
                OcrLine("30", Box(0.66f, 0.57f, 0.82f, 0.78f), 0.99f),
            ),
        ).tagOrNull()

        assertEquals("Біфідойогурт Данон Активіа 1,5% бут. Класичний", tag?.name?.value)
        assertEquals(Quantity(800.0, MeasureUnit.GRAM), tag?.quantity?.value)
    }

    @Test
    fun `a full name line that reaches both edges of the frame is still the name`() {
        // The tag is wider than the viewfinder; packaging fragments cut by an edge are single words.
        val tag = uk.parse(
            listOf(
                OcrLine("shing&", Box(0.00f, 0.00f, 0.20f, 0.07f), 0.8f),
                OcrLine("Ряжанка Яготинський 2,9% 750 г", Box(0.00f, 0.08f, 1.00f, 0.24f), 0.91f),
                OcrLine("Курага", Box(0.01f, 0.20f, 0.23f, 0.35f), 0.78f),
                OcrLine("82", Box(0.32f, 0.54f, 0.68f, 1.00f), 1f),
                OcrLine("90", Box(0.66f, 0.57f, 0.82f, 0.78f), 0.99f),
            ),
        ).tagOrNull()

        assertEquals("Ряжанка Яготинський 2,9% Курага", tag?.name?.value)
        assertEquals(Quantity(750.0, MeasureUnit.GRAM), tag?.quantity?.value)
    }
}
