package io.github.zhora87.bezmen.ui.compare

import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.comparison.ComparisonItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonComparisonStoreTest {
    @Test
    fun `items survive a save and load round trip`() = runTest {
        var file: String? = null
        val store = JsonComparisonStore(read = { file }, write = { file = it })
        val items = listOf(
            ComparisonItem("a", "Молоко", Money.of(49, 90, "UAH"), Quantity(900.0, MeasureUnit.MILLILITRE)),
            ComparisonItem("b", null, Money.of(10, 0, "UAH"), Quantity(1.0, MeasureUnit.PIECE)),
        )

        store.save(items)

        assertEquals(items, store.load())
    }

    @Test
    fun `no file or a broken file means an empty list`() = runTest {
        assertEquals(emptyList(), JsonComparisonStore(read = { null }, write = {}).load())
        assertEquals(emptyList(), JsonComparisonStore(read = { "{not json" }, write = {}).load())
    }
}
