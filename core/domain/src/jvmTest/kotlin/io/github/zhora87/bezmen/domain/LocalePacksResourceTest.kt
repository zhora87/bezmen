package io.github.zhora87.bezmen.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every shipped pack under locale-packs/ must load with strict JSON and pass validation. */
class LocalePacksResourceTest {
    private val ids = listOf("ru", "uk", "en-generic")

    private fun load(id: String): LocalePack {
        val resource = javaClass.classLoader.getResource("$id.json")
        val text = assertNotNull(resource, "locale-packs/$id.json missing").readText()
        return LocalePack.fromJson(text)
    }

    @Test
    fun `all shipped packs are valid`() {
        ids.forEach { id ->
            val pack = load(id)
            assertEquals(id, pack.id)
            assertEquals(emptyList(), pack.validate(), "problems in $id")
        }
    }

    @Test
    fun `cyrillic packs read the units a Russian or Ukrainian tag prints`() {
        val ru = load("ru")
        assertEquals(MeasureUnit.GRAM, ru.unitFor("гр."))
        assertEquals(MeasureUnit.KILOGRAM, ru.unitFor("кг"))
        assertEquals(MeasureUnit.MILLILITRE, ru.unitFor("мл"))
        assertEquals(MeasureUnit.LITRE, ru.unitFor("л"))
        assertEquals(MeasureUnit.PIECE, ru.unitFor("шт."))
        assertTrue(ru.isCurrency("₽") && ru.isCurrency("руб."))

        val uk = load("uk")
        assertEquals(MeasureUnit.GRAM, uk.unitFor("г"))
        assertTrue(uk.isCurrency("₴") && uk.isCurrency("грн"))
    }

    @Test
    fun `latin pack accepts both decimal separators`() {
        val en = load("en-generic")
        assertEquals(listOf(",", "."), en.currency.decimalSeparators)
        assertEquals(MeasureUnit.LITRE, en.unitFor("ltr"))
    }
}
