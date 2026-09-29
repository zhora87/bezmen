package io.github.zhora87.bezmen.ui.settings

import io.github.zhora87.bezmen.domain.DisplayUnits
import io.github.zhora87.bezmen.domain.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Current settings, applied at once and kept in [store]. */
class SettingsController(private val scope: CoroutineScope, private val store: SettingsStore) {
    private val mutable = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = mutable.asStateFlow()

    init {
        scope.launch { store.load()?.let { mutable.value = it } }
    }

    fun setUnits(units: DisplayUnits) = update { it.copy(units = units) }

    /** Null returns to choosing the pack by the phone's country. */
    fun setPack(packId: String?) = update { it.copy(packId = packId) }

    private fun update(change: (Settings) -> Settings) {
        val next = change(mutable.value)
        mutable.value = next
        scope.launch { store.save(next) }
    }
}
