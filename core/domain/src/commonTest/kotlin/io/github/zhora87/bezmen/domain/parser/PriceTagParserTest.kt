package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.References
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceTagParserTest {
    private val ru = PriceTagParser(TestPacks.ru)

    private fun success(result: ParseResult): ParseResult.Success {
        assertIs<ParseResult.Success>(result, "expected Success, got $result")
        return result
    }

    private fun parse(parser: PriceTagParser = ru, vararg lines: OcrLine): ParseResult.Success =
        success(parser.parse(lines.toList()))

    @Test
    fun `plain russian tag - price, quantity, unit price and name`() {
        val result = parse(
            ru,
            line("Молоко Простоквашино 2,5%", top = 0.05f, height = 0.10f),
            line("900 мл", top = 0.20f, height = 0.06f),
            line("249,90 ₽", top = 0.50f, height = 0.30f),
        )

        assertEquals(Money.of(249, 90, "RUB"), result.tag.price?.value)
        assertEquals(Quantity(900.0, MeasureUnit.MILLILITRE), result.tag.quantity?.value)
        assertEquals(Money.of(27, 77, "RUB"), result.unitPrice.per(References.PER_100_ML))
        assertEquals("Молоко Простоквашино 2,5%", result.tag.name?.value)
        assertFalse(result.tag.isWeighted)
        assertNull(result.tag.oldPrice)
        assertTrue(result.overall >= ParseResult.CONFIRM_THRESHOLD, "overall=${result.overall}")
    }

    @Test
    fun `superscript kopecks glued to the price`() {
        val result = parse(ru, line("Кефир 1 л", 0.05f, 0.1f), line("89⁹⁰ ₽", 0.5f, 0.3f))

        assertEquals(Money.of(89, 90, "RUB"), result.tag.price?.value)
        assertEquals(Money.of(8, 99, "RUB"), result.unitPrice.per(References.PER_100_ML))
    }

    @Test
    fun `kopecks recognised as a separate small box to the right`() {
        val result = parse(
            ru,
            line("Сок Добрый 1 л", 0.05f, 0.10f),
            OcrLine("249", Box(0.10f, 0.50f, 0.50f, 0.80f), 0.95f),
            OcrLine("90", Box(0.52f, 0.50f, 0.62f, 0.62f), 0.90f),
        )

        assertEquals(Money.of(249, 90, "RUB"), result.tag.price?.value)
        assertEquals(listOf(1, 2), result.tag.price?.sourceLines)
        assertEquals(Money.of(24, 99, "RUB"), result.unitPrice.per(References.PER_100_ML))
    }

    @Test
    fun `multipack is multiplied out`() {
        val result = parse(ru, line("Йогурт Активиа 4 x 115 г", 0.05f, 0.1f), line("199,00 ₽", 0.5f, 0.3f))

        assertEquals(Quantity(460.0, MeasureUnit.GRAM), result.tag.quantity?.value)
        assertEquals(Money.of(43, 26, "RUB"), result.unitPrice.per(References.PER_100_G))
        assertEquals("Йогурт Активиа", result.tag.name?.value)
    }

    @Test
    fun `pieces with po multiplier`() {
        val result = parse(ru, line("Сырки 2 шт по 90 г", 0.05f, 0.1f), line("59,90 ₽", 0.5f, 0.3f))

        assertEquals(Quantity(180.0, MeasureUnit.GRAM), result.tag.quantity?.value)
    }

    @Test
    fun `count goods are priced per piece`() {
        val result = parse(ru, line("Яйца С1 10 шт", 0.05f, 0.1f), line("129 ₽", 0.5f, 0.3f))

        assertEquals(Quantity(10.0, MeasureUnit.PIECE), result.tag.quantity?.value)
        assertEquals(Money.of(129, 0, "RUB"), result.tag.price?.value)
        assertEquals(Money.of(12, 90, "RUB"), result.unitPrice.per(References.PER_PIECE))
    }

    @Test
    fun `weighted goods - price per kg in the same line as the marker`() {
        val result = parse(ru, line("Бананы", 0.05f, 0.12f), line("89,90 ₽ за 1 кг", 0.5f, 0.25f))

        assertTrue(result.tag.isWeighted)
        assertEquals(Money.of(89, 90, "RUB"), result.tag.price?.value)
        assertEquals(Quantity(1.0, MeasureUnit.KILOGRAM), result.tag.quantity?.value)
        assertEquals(Money.of(8, 99, "RUB"), result.unitPrice.per(References.PER_100_G))
        assertEquals("Бананы", result.tag.name?.value)
    }

    @Test
    fun `weighted goods - big price and a lone marker line`() {
        val result = parse(
            ru,
            line("Бананы", 0.05f, 0.12f),
            line("89,90", 0.4f, 0.3f),
            line("цена за 1 кг", 0.8f, 0.06f),
        )

        assertTrue(result.tag.isWeighted)
        assertEquals(Money.of(89, 90, "RUB"), result.tag.price?.value)
        assertEquals(Quantity(1.0, MeasureUnit.KILOGRAM), result.tag.quantity?.value)
    }

    @Test
    fun `printed unit price that agrees keeps confidence high`() {
        val result = parse(
            ru,
            line("Сок Rich 1 л", 0.05f, 0.1f),
            line("129,90 ₽", 0.4f, 0.3f),
            line("цена за 1 л 129,90", 0.85f, 0.05f),
        )

        assertEquals(Money.of(129, 90, "RUB"), result.tag.printedUnitPrice?.value?.per(References.PER_1_L))
        assertEquals(Quantity(1.0, MeasureUnit.LITRE), result.tag.quantity?.value)
        assertTrue(result.overall >= ParseResult.CONFIRM_THRESHOLD, "overall=${result.overall}")
    }

    @Test
    fun `printed unit price that disagrees lowers confidence`() {
        val result = parse(
            ru,
            line("Сок Rich 1 л", 0.05f, 0.1f),
            line("129,90 ₽", 0.4f, 0.3f),
            line("цена за 1 л 150,00", 0.85f, 0.05f),
        )

        assertTrue(result.overall < ParseResult.CONFIRM_THRESHOLD, "overall=${result.overall}")
    }

    @Test
    fun `discount tag - small old price, big current price`() {
        val result = parse(
            ru,
            line("АКЦИЯ", 0.02f, 0.08f),
            line("349,90", 0.15f, 0.08f),
            line("249,90 ₽", 0.45f, 0.35f),
            line("Сыр Ламбер 230 г", 0.85f, 0.08f),
        )

        assertEquals(Money.of(249, 90, "RUB"), result.tag.price?.value)
        assertEquals(Money.of(349, 90, "RUB"), result.tag.oldPrice?.value)
        assertEquals(Quantity(230.0, MeasureUnit.GRAM), result.tag.quantity?.value)
    }

    @Test
    fun `old price labelled explicitly wins over geometry`() {
        val result = parse(
            ru,
            line("Старая цена 299,00", 0.05f, 0.2f),
            line("199,00 ₽", 0.4f, 0.2f),
            line("Чай 100 г", 0.8f, 0.08f),
        )

        assertEquals(Money.of(199, 0, "RUB"), result.tag.price?.value)
        assertEquals(Money.of(299, 0, "RUB"), result.tag.oldPrice?.value)
    }

    @Test
    fun `loyalty card price is reported separately from the shelf price`() {
        val result = parse(
            ru,
            line("Пельмени 800 г", 0.05f, 0.1f),
            line("199,90 по карте", 0.3f, 0.12f),
            line("249,90 ₽", 0.5f, 0.3f),
        )

        assertEquals(Money.of(249, 90, "RUB"), result.tag.price?.value)
        assertEquals(Money.of(199, 90, "RUB"), result.tag.loyaltyPrice?.value)
        assertEquals(emptyList(), result.tag.price?.alternatives)
    }

    @Test
    fun `ukrainian tag with glued superscript hryvnia`() {
        val result = parse(
            PriceTagParser(TestPacks.uk),
            line("Молоко Галичина 2,5% 870 г", 0.05f, 0.1f),
            line("54⁹⁹ ₴", 0.5f, 0.3f),
        )

        assertEquals(Money.of(54, 99, "UAH"), result.tag.price?.value)
        assertEquals(Quantity(870.0, MeasureUnit.GRAM), result.tag.quantity?.value)
        assertEquals(Money.of(6, 32, "UAH"), result.unitPrice.per(References.PER_100_G))
    }

    @Test
    fun `english tag with 1 kg equals marker cross-checks fine`() {
        val result = parse(
            PriceTagParser(TestPacks.en),
            line("Nutella 400 g", 0.05f, 0.1f),
            line("3.49 €", 0.4f, 0.3f),
            line("1 kg = 8.73 €", 0.85f, 0.05f),
        )

        assertEquals(Money.of(3, 49, "EUR"), result.tag.price?.value)
        assertEquals(Quantity(400.0, MeasureUnit.GRAM), result.tag.quantity?.value)
        assertEquals(Money.of(8, 73, "EUR"), result.tag.printedUnitPrice?.value?.per(References.PER_1_KG))
        assertTrue(result.overall >= ParseResult.CONFIRM_THRESHOLD, "overall=${result.overall}")
    }

    @Test
    fun `atb - kopecks box overlapping the big digits, gram misread as р`() {
        val result = parse(
            PriceTagParser(TestPacks.uk),
            OcrLine("Масло", Box(0.56f, 0.09f, 0.76f, 0.22f), 0.64f),
            OcrLine("\"Білоцерківське\"", Box(0.39f, 0.20f, 0.92f, 0.38f), 0.97f),
            OcrLine("-50%", Box(0.07f, 0.30f, 0.30f, 0.49f), 1.0f),
            OcrLine("селянське, ж.72,6 %", Box(0.38f, 0.46f, 0.94f, 0.64f), 0.97f),
            OcrLine("99", Box(0.48f, 0.55f, 0.76f, 0.96f), 0.99f),
            OcrLine("90", Box(0.68f, 0.58f, 0.85f, 0.83f), 1.0f),
            OcrLine("180р", Box(0.73f, 0.84f, 0.82f, 0.91f), 0.88f),
            OcrLine("820019492025", Box(0.05f, 0.88f, 0.34f, 0.97f), 1.0f),
            OcrLine("1.21.6", Box(0.01f, 0.15f, 0.10f, 0.22f), 0.93f),
        )

        assertEquals(Money.of(99, 90, "UAH"), result.tag.price?.value)
        assertEquals(Quantity(180.0, MeasureUnit.GRAM), result.tag.quantity?.value)
        assertNull(result.tag.oldPrice)
    }

    @Test
    fun `atb - latin look-alikes in unit and currency`() {
        val result = parse(
            PriceTagParser(TestPacks.uk),
            OcrLine("Буряк", Box(0.27f, 0.16f, 0.61f, 0.27f), 0.77f),
            OcrLine("Україна", Box(0.26f, 0.47f, 0.58f, 0.56f), 0.95f),
            OcrLine("1 Kr", Box(0.35f, 0.55f, 0.50f, 0.61f), 0.74f),
            OcrLine("8", Box(0.18f, 0.60f, 0.50f, 0.91f), 1.0f),
            OcrLine("35", Box(0.59f, 0.61f, 0.77f, 0.74f), 1.0f),
            OcrLine("ррн.", Box(0.58f, 0.81f, 0.75f, 0.89f), 0.70f),
        )

        assertEquals(Money.of(8, 35, "UAH"), result.tag.price?.value)
        assertEquals(Quantity(1.0, MeasureUnit.KILOGRAM), result.tag.quantity?.value)
        assertEquals("Буряк", result.tag.name?.value)
    }

    @Test
    fun `atb - app price labelled in the same band, crossed old price, set as piece`() {
        val result = parse(
            PriceTagParser(TestPacks.uk),
            OcrLine("при скануванні", Box(0.11f, 0.14f, 0.50f, 0.24f), 0.94f),
            OcrLine("ЦІНА", Box(0.53f, 0.16f, 0.68f, 0.27f), 0.54f),
            OcrLine("Знижка", Box(0.73f, 0.21f, 0.92f, 0.33f), 0.66f),
            OcrLine("додатка АТБ", Box(0.14f, 0.23f, 0.45f, 0.32f), 0.96f),
            OcrLine("290", Box(0.44f, 0.25f, 0.63f, 0.42f), 1.0f),
            OcrLine("70", Box(0.63f, 0.27f, 0.68f, 0.35f), 1.0f),
            OcrLine("29%", Box(0.73f, 0.28f, 0.90f, 0.46f), 1.0f),
            OcrLine("Бритва \"Gillette\"", Box(0.20f, 0.48f, 0.60f, 0.59f), 0.91f),
            OcrLine("461", Box(0.66f, 0.52f, 0.86f, 0.70f), 0.84f),
            OcrLine("40", Box(0.86f, 0.53f, 0.91f, 0.61f), 0.84f),
            OcrLine("323", Box(0.62f, 0.71f, 0.80f, 0.88f), 1.0f),
            OcrLine("00", Box(0.80f, 0.73f, 0.87f, 0.82f), 0.99f),
            OcrLine("грн / 1 наб-р", Box(0.80f, 0.82f, 0.92f, 0.90f), 0.8f),
        )

        assertEquals(Money.of(323, 0, "UAH"), result.tag.price?.value)
        assertEquals(Money.of(461, 40, "UAH"), result.tag.oldPrice?.value)
        assertEquals(Money.of(290, 70, "UAH"), result.tag.loyaltyPrice?.value)
        assertEquals(Quantity(1.0, MeasureUnit.PIECE), result.tag.quantity?.value)
    }

    @Test
    fun `percent-only line marks a discount without the word for it`() {
        val result = parse(
            ru,
            line("Масло 180 г", 0.05f, 0.10f),
            line("-50%", 0.25f, 0.12f),
            line("199,80", 0.45f, 0.12f),
            line("99,90 ₽", 0.65f, 0.12f),
        )

        assertEquals(Money.of(99, 90, "RUB"), result.tag.price?.value)
        assertEquals(Money.of(199, 80, "RUB"), result.tag.oldPrice?.value)
        assertTrue(result.overall >= ParseResult.CONFIRM_THRESHOLD, "overall=${result.overall}")
    }

    @Test
    fun `barcode and date are not prices`() {
        val result = parse(
            ru,
            line("Гречка 800 г", 0.05f, 0.1f),
            line("89,90 ₽", 0.4f, 0.3f),
            line("4600680001234", 0.9f, 0.04f),
            line("12.05.2026", 0.95f, 0.04f),
        )

        assertEquals(Money.of(89, 90, "RUB"), result.tag.price?.value)
        assertNull(result.tag.oldPrice)
    }

    @Test
    fun `percent is never a quantity`() {
        val result = parse(ru, line("Сметана 20% 300 г", 0.05f, 0.1f), line("79,90 ₽", 0.5f, 0.3f))

        assertEquals(Quantity(300.0, MeasureUnit.GRAM), result.tag.quantity?.value)
    }

    @Test
    fun `glued decimal kilograms`() {
        val result = parse(ru, line("Сахар 1,5кг", 0.05f, 0.1f), line("119,90 ₽", 0.5f, 0.3f))

        assertEquals(Quantity(1500.0, MeasureUnit.GRAM), result.tag.quantity?.value?.toBase())
    }

    @Test
    fun `quantity derived from price and printed unit price`() {
        val result = parse(ru, line("249,90 ₽", 0.4f, 0.3f), line("за 100 мл 27,77", 0.85f, 0.05f))

        val quantity = assertNotNull(result.tag.quantity)
        assertEquals(MeasureUnit.MILLILITRE, quantity.value.unit)
        assertEquals(900.0, quantity.value.value, 1.0)
        assertFalse(result.tag.isWeighted)
        assertTrue(result.overall < ParseResult.CONFIRM_THRESHOLD)
    }

    @Test
    fun `quantity without price asks for the price`() {
        val result = ru.parse(listOf(line("500 г", 0.1f, 0.1f)))

        assertIs<ParseResult.NeedsInput>(result)
        assertEquals(setOf(FieldKind.PRICE), result.missing)
        assertEquals(Quantity(500.0, MeasureUnit.GRAM), result.tag.quantity?.value)
    }

    @Test
    fun `price without quantity asks for the quantity`() {
        val result = ru.parse(listOf(line("Хлеб Бородинский", 0.05f, 0.1f), line("59,90 ₽", 0.5f, 0.3f)))

        assertIs<ParseResult.NeedsInput>(result)
        assertEquals(setOf(FieldKind.QUANTITY), result.missing)
        assertEquals(Money.of(59, 90, "RUB"), result.tag.price?.value)
        assertEquals("Хлеб Бородинский", result.tag.name?.value)
    }

    @Test
    fun `no numbers at all`() {
        assertEquals(ParseResult.Nothing, ru.parse(listOf(line("Хлеб", 0.1f, 0.1f))))
        assertEquals(ParseResult.Nothing, ru.parse(emptyList()))
    }
}
