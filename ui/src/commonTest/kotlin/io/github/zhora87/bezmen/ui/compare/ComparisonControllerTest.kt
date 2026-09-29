package io.github.zhora87.bezmen.ui.compare

import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.comparison.ComparisonItem
import io.github.zhora87.bezmen.ui.scan.TagDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComparisonControllerTest {
    private class FakeStore(var saved: List<ComparisonItem> = emptyList()) : ComparisonStore {
        var saves = 0

        override suspend fun load(): List<ComparisonItem> = saved

        override suspend fun save(items: List<ComparisonItem>) {
            saved = items
            saves++
        }
    }

    private fun draft(name: String, price: String, quantity: String, unit: MeasureUnit?) = TagDraft(
        name = name,
        priceText = price,
        quantityText = quantity,
        unit = unit,
        currency = "UAH",
        oldPrice = null,
        isWeighted = false,
        needsCheck = false,
    )

    private val milk = ComparisonItem("m", "Молоко", Money.of(49, 90, "UAH"), Quantity(900.0, MeasureUnit.MILLILITRE))

    @Test
    fun `saved items are loaded at start and ranked`() = runTest {
        val store = FakeStore(listOf(milk))
        val c = ComparisonController(this, store)
        advanceUntilIdle()

        assertEquals(listOf(milk), c.state.value.items)
        assertEquals(1, c.state.value.ranked.single().rank)
    }

    @Test
    fun `a draft with price and quantity is added and saved`() = runTest {
        val store = FakeStore()
        val c = ComparisonController(this, store) { "id-1" }
        advanceUntilIdle()

        val added = c.add(draft("Масло", "99,90", "180", MeasureUnit.GRAM))
        advanceUntilIdle()

        assertTrue(added)
        val item = store.saved.single()
        assertEquals("id-1", item.id)
        assertEquals("Масло", item.name)
        assertEquals(Money.of(99, 90, "UAH"), item.price)
        assertEquals(Quantity(180.0, MeasureUnit.GRAM), item.quantity)
    }

    @Test
    fun `a draft without a usable quantity is refused`() = runTest {
        val store = FakeStore()
        val c = ComparisonController(this, store)
        advanceUntilIdle()

        assertFalse(c.add(draft("Масло", "99,90", "", null)))
        assertFalse(c.add(draft("Масло", "abc", "180", MeasureUnit.GRAM)))
        advanceUntilIdle()
        assertEquals(0, store.saves)
    }

    @Test
    fun `an unnamed item keeps a null name`() = runTest {
        val c = ComparisonController(this, FakeStore())
        advanceUntilIdle()

        c.add(draft("  ", "10,00", "1", MeasureUnit.PIECE))
        advanceUntilIdle()

        assertNull(c.state.value.items.single().name)
    }

    @Test
    fun `cheapest per unit comes first, other dimensions are not comparable`() = runTest {
        val c = ComparisonController(this, FakeStore())
        advanceUntilIdle()
        c.add(draft("Дорогое", "60,00", "500", MeasureUnit.GRAM))
        c.add(draft("Дешёвое", "100,00", "1", MeasureUnit.KILOGRAM))
        c.add(draft("Литры", "50,00", "1", MeasureUnit.LITRE))
        advanceUntilIdle()

        val ranked = c.state.value.ranked
        assertEquals(listOf("Дешёвое", "Дорогое", "Литры"), ranked.map { it.item.name })
        assertEquals(20.0, ranked[1].deltaPercent!!, 1e-9)
        assertFalse(ranked[2].isComparable)
    }

    @Test
    fun `remove, rename and clear are persisted`() = runTest {
        val store = FakeStore(listOf(milk, milk.copy(id = "k", name = "Кефір")))
        val c = ComparisonController(this, store)
        advanceUntilIdle()

        c.rename("k", "Кефір 1%")
        c.remove("m")
        advanceUntilIdle()
        assertEquals(listOf("Кефір 1%"), store.saved.map { it.name })

        c.clear()
        advanceUntilIdle()
        assertEquals(emptyList(), store.saved)
    }
}
