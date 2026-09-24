package io.github.zhora87.bezmen.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalePackChoiceTest {
    @Test
    fun `the shelf is in the country of the mobile network, whatever the interface language`() {
        assertEquals("uk", LocalePackChoice.idFor(countries = listOf("ua", "ua", "RU"), language = "ru"))
    }

    @Test
    fun `falls back from network to sim to locale country`() {
        assertEquals("ru", LocalePackChoice.idFor(countries = listOf("", null, "BY"), language = "en"))
        assertEquals("uk", LocalePackChoice.idFor(countries = listOf(null, "UA", "RU"), language = "ru"))
    }

    @Test
    fun `unknown country uses the language, then the generic pack`() {
        assertEquals("ru", LocalePackChoice.idFor(countries = listOf(null, null, ""), language = "ru"))
        assertEquals("en-generic", LocalePackChoice.idFor(countries = listOf("de"), language = "de"))
        assertEquals("en-generic", LocalePackChoice.idFor(countries = emptyList(), language = "en"))
    }
}
