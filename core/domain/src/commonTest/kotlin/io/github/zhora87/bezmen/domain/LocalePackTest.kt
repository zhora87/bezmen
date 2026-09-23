package io.github.zhora87.bezmen.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalePackTest {
    private val pack = LocalePack.fromJson(
        """
        {
          "id": "xx",
          "version": 1,
          "script": "CYRILLIC",
          "ocrModel": "cyrillic",
          "currency": { "code": "XXX", "symbols": ["¤", "xx."] },
          "units": { "g": ["г", "гр.", "g"], "ml": ["мл"], "pc": ["шт", "шт."] },
          "multipack": ["x", "×", "по"],
          "unitPriceMarkers": ["за 1 кг"],
          "discountMarkers": ["акция"],
          "charFixes": { "O": "0" }
        }
        """.trimIndent(),
    )

    @Test
    fun `parses and validates a minimal pack`() {
        assertEquals(emptyList(), pack.validate())
        assertEquals("XXX", pack.currency.code)
        assertEquals(listOf(",", "."), pack.currency.decimalSeparators)
    }

    @Test
    fun `resolves unit aliases ignoring case and trailing dots`() {
        assertEquals(MeasureUnit.GRAM, pack.unitFor("Гр."))
        assertEquals(MeasureUnit.GRAM, pack.unitFor("гр"))
        assertEquals(MeasureUnit.PIECE, pack.unitFor("ШТ"))
        assertNull(pack.unitFor("кг"))
    }

    @Test
    fun `folds OCR look-alikes before matching aliases`() {
        assertEquals(MeasureUnit.GRAM, pack.unitFor("r"))
        assertEquals(MeasureUnit.GRAM, pack.unitFor("Гp."))
        assertEquals(MeasureUnit.PIECE, pack.unitFor("шT"))
    }

    @Test
    fun `recognises currency and multipack tokens`() {
        assertTrue(pack.isCurrency("¤"))
        assertTrue(pack.isCurrency("XX"))
        assertTrue(pack.isMultipack("×"))
        assertTrue(pack.isMultipack("ПО"))
    }

    @Test
    fun `reports unknown unit codes and duplicate aliases`() {
        val broken = pack.copy(
            id = "Bad",
            units = mapOf("g" to listOf("г"), "furlong" to listOf("фл"), "kg" to listOf("Г.", "кг")),
            currency = pack.currency.copy(code = "rub", decimalSeparators = listOf(";")),
        )

        val problems = broken.validate()

        assertTrue(problems.any { "unknown unit code 'furlong'" in it }, problems.toString())
        assertTrue(problems.any { "alias 'Г.'" in it }, problems.toString())
        assertTrue(problems.any { "id 'Bad'" in it }, problems.toString())
        assertTrue(problems.any { "currency.code" in it }, problems.toString())
        assertTrue(problems.any { "decimal separator" in it }, problems.toString())
    }
}
