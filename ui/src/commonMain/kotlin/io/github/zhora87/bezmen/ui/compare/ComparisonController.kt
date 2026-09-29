package io.github.zhora87.bezmen.ui.compare

import io.github.zhora87.bezmen.domain.comparison.Comparison
import io.github.zhora87.bezmen.domain.comparison.ComparisonItem
import io.github.zhora87.bezmen.domain.comparison.RankedItem
import io.github.zhora87.bezmen.ui.scan.TagDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The comparison list in insertion order and the same items ranked by unit price. */
data class ComparisonState(val items: List<ComparisonItem> = emptyList(), val loaded: Boolean = false) {
    val ranked: List<RankedItem> get() = Comparison.rank(items)
}

/** Holds the comparison list, keeps it in [store], and turns scanned tags into items. */
class ComparisonController(
    private val scope: CoroutineScope,
    private val store: ComparisonStore,
    private val newId: () -> String = { Random.nextLong().toULong().toString(RADIX) },
) {
    private val mutable = MutableStateFlow(ComparisonState())
    val state: StateFlow<ComparisonState> = mutable.asStateFlow()

    init {
        scope.launch { mutable.value = ComparisonState(store.load(), loaded = true) }
    }

    /** Adds the tag when it has a readable price and quantity; false otherwise. */
    fun add(draft: TagDraft): Boolean {
        val price = draft.price ?: return false
        val quantity = draft.quantity ?: return false
        val item = ComparisonItem(newId(), draft.name.trim().ifEmpty { null }, price, quantity)
        update { it + item }
        return true
    }

    fun remove(id: String) = update { items -> items.filterNot { it.id == id } }

    fun rename(id: String, name: String) = update { items ->
        items.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { null }) else it }
    }

    fun clear() = update { emptyList() }

    private fun update(change: (List<ComparisonItem>) -> List<ComparisonItem>) {
        val items = change(mutable.value.items)
        mutable.value = mutable.value.copy(items = items)
        scope.launch { store.save(items) }
    }

    private companion object {
        const val RADIX = 36
    }
}
