package io.github.zhora87.bezmen.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    @Test
    fun `builds from major and fraction`() {
        val m = Money.of(249, 90, "RUB")
        assertEquals(24990, m.minor)
        assertEquals(249, m.major)
        assertEquals(90, m.fraction)
        assertEquals("249,90", m.format())
        assertEquals("249.90", m.format('.'))
    }

    @Test
    fun `rounds doubles to minor units`() {
        assertEquals(2777, Money.fromDouble(27.766, "RUB").minor)
        assertEquals(1, Money.fromDouble(0.005, "EUR").minor)
    }

    @Test
    fun `compares only within one currency`() {
        assertTrue(Money(100, "RUB") < Money(200, "RUB"))
        assertFailsWith<IllegalArgumentException> { Money(100, "RUB").compareTo(Money(100, "UAH")) }
    }

    @Test
    fun `rejects negative amounts and invalid fractions`() {
        assertFailsWith<IllegalArgumentException> { Money(-1, "RUB") }
        assertFailsWith<IllegalArgumentException> { Money.of(1, 100, "RUB") }
    }
}
