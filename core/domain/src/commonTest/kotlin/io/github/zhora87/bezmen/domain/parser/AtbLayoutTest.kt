package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shapes taken from real ATB tags read by PP-OCRv5 through the on-device pipeline. */
class AtbLayoutTest {
    private val uk = PriceTagParser(TestPacks.uk)

    private fun success(result: ParseResult): ParseResult.Success {
        assertIs<ParseResult.Success>(result, "expected Success, got $result")
        return result
    }

    @Test
    fun `price and kopecks merged into one token are split on tags with superscript kopecks`() {
        val result = success(
            uk.parse(
                listOf(
                    line("Стейк яловичий", 0.10f, 0.12f),
                    OcrLine("29480", Box(0.55f, 0.10f, 0.95f, 0.45f), 0.98f),
                    line("грн / шт", 0.50f, 0.06f, left = 0.70f, right = 0.95f),
                ),
            ),
        )

        assertEquals(Money.of(294, 80, "UAH"), result.tag.price?.value)
        assertEquals(Quantity(1.0, MeasureUnit.PIECE), result.tag.quantity?.value)
    }

    @Test
    fun `three-digit whole price stays whole`() {
        val result = success(
            uk.parse(
                listOf(
                    line("Дезодорант", 0.10f, 0.10f),
                    OcrLine("173", Box(0.5f, 0.2f, 0.9f, 0.55f), 0.99f),
                    line("грн / 85 мл", 0.6f, 0.05f),
                ),
            ),
        )

        assertEquals(Money.of(173, 0, "UAH"), result.tag.price?.value)
        assertEquals(Quantity(85.0, MeasureUnit.MILLILITRE), result.tag.quantity?.value)
    }

    @Test
    fun `bare unit after the currency means one piece, bare kg means sold by weight`() {
        val pieces = success(
            uk.parse(
                listOf(
                    line("Пончик", 0.1f, 0.1f),
                    OcrLine("20", Box(0.5f, 0.2f, 0.75f, 0.55f), 1f),
                    OcrLine("90", Box(0.76f, 0.2f, 0.9f, 0.35f), 1f),
                    line("грн/шт", 0.6f, 0.05f),
                ),
            ),
        )
        assertEquals(Quantity(1.0, MeasureUnit.PIECE), pieces.tag.quantity?.value)
        assertTrue(!pieces.tag.isWeighted)

        val weighted = success(
            uk.parse(
                listOf(
                    line("Цибуля", 0.1f, 0.1f),
                    OcrLine("17", Box(0.5f, 0.2f, 0.75f, 0.55f), 1f),
                    OcrLine("95", Box(0.76f, 0.2f, 0.9f, 0.35f), 1f),
                    line("грн/кг", 0.6f, 0.05f),
                ),
            ),
        )
        assertEquals(Quantity(1.0, MeasureUnit.KILOGRAM), weighted.tag.quantity?.value)
        assertTrue(weighted.tag.isWeighted)
    }

    @Test
    fun `code lines never yield prices and a weighted marker sets the flag`() {
        val result = success(
            uk.parse(
                listOf(
                    line("цей товар ваговий - вартість вказана за 100 г", 0.02f, 0.05f),
                    line("Вироби фаршеві", 0.15f, 0.10f),
                    OcrLine("23", Box(0.55f, 0.30f, 0.75f, 0.60f), 1f),
                    OcrLine("88", Box(0.76f, 0.30f, 0.90f, 0.45f), 1f),
                    line("грн / 100 г", 0.62f, 0.05f, left = 0.7f),
                    line("Код 200700 1.73", 0.85f, 0.30f),
                ),
            ),
        )

        assertEquals(Money.of(23, 88, "UAH"), result.tag.price?.value)
        assertEquals(Quantity(100.0, MeasureUnit.GRAM), result.tag.quantity?.value)
        assertTrue(result.tag.isWeighted)
        assertNull(result.tag.oldPrice)
    }

    @Test
    fun `a cheaper unlabelled price in smaller type is an alternative, not the old price`() {
        val result = success(
            uk.parse(
                listOf(
                    OcrLine("21", Box(0.50f, 0.10f, 0.62f, 0.22f), 1f),
                    OcrLine("47", Box(0.63f, 0.10f, 0.68f, 0.17f), 1f),
                    line("Сардельки 100 г", 0.30f, 0.10f),
                    OcrLine("23", Box(0.55f, 0.45f, 0.78f, 0.75f), 1f),
                    OcrLine("88", Box(0.79f, 0.45f, 0.90f, 0.60f), 1f),
                ),
            ),
        )

        assertEquals(Money.of(23, 88, "UAH"), result.tag.price?.value)
        assertNull(result.tag.oldPrice)
        assertEquals(listOf(Money.of(21, 47, "UAH")), result.tag.price?.alternatives)
    }

    @Test
    fun `a letter wedged into a barcode is not a unit`() {
        val result = success(
            uk.parse(
                listOf(
                    line("Яйце куряче", 0.1f, 0.1f),
                    OcrLine("79", Box(0.5f, 0.1f, 0.75f, 0.5f), 1f),
                    OcrLine("50", Box(0.76f, 0.1f, 0.9f, 0.3f), 1f),
                    line("10 шт", 0.42f, 0.05f, left = 0.75f),
                    line("82n214780361", 0.85f, 0.05f),
                ),
            ),
        )

        assertEquals(Quantity(10.0, MeasureUnit.PIECE), result.tag.quantity?.value)
    }

    @Test
    fun `quantity next to the price beats numbers printed on the product behind it`() {
        val result = success(
            uk.parse(
                listOf(
                    line("500 ml Alc. 6,0%", 0.02f, 0.06f, left = 0.05f, right = 0.60f),
                    line("Пиво світле", 0.40f, 0.10f, left = 0.05f, right = 0.45f),
                    OcrLine("77", Box(0.55f, 0.40f, 0.78f, 0.70f), 1f),
                    OcrLine("30", Box(0.79f, 0.40f, 0.90f, 0.55f), 1f),
                    line("грн / шт", 0.57f, 0.05f, left = 0.79f, right = 0.92f),
                ),
            ),
        )

        assertEquals(Quantity(1.0, MeasureUnit.PIECE), result.tag.quantity?.value)
    }

    @Test
    fun `nothing is sold below one gram so a misread milligram quantity is skipped`() {
        val result = success(
            uk.parse(
                listOf(
                    line("500g", 0.07f, 0.12f, left = 0.68f, right = 0.99f),
                    OcrLine("82", Box(0.55f, 0.51f, 0.83f, 0.71f), 1f),
                    OcrLine("60", Box(0.77f, 0.52f, 0.91f, 0.62f), 1f),
                    line("0,5 мг", 0.63f, 0.03f, left = 0.82f, right = 0.89f),
                ),
            ),
        )

        assertEquals(Quantity(500.0, MeasureUnit.GRAM), result.tag.quantity?.value)
    }
}
