package io.github.zhora87.bezmen.domain.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizerTest {
    private val normalizer = TextNormalizer(TestPacks.ru)

    @Test
    fun `superscript kopecks become a decimal fraction`() {
        assertEquals("249.90 ₽", normalizer.normalize("249⁹⁰ ₽"))
    }

    @Test
    fun `decimal comma becomes a dot`() {
        assertEquals("0.33 л", normalizer.normalize("0,33 л"))
    }

    @Test
    fun `look-alike letters inside numbers are fixed, words are left alone`() {
        assertEquals("900 мл", normalizer.normalize("9OO мл"))
        assertEquals("Молоко 300 г", normalizer.normalize("Молоко З00 г"))
        assertEquals("Оливки", normalizer.normalize("Оливки"))
    }

    @Test
    fun `non-breaking spaces and surrounding whitespace are cleaned`() {
        assertEquals("249.90 ₽", normalizer.normalize("  249,90 ₽ "))
    }
}
