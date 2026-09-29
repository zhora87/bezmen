package io.github.zhora87.bezmen.ui.settings

import io.github.zhora87.bezmen.domain.DisplayUnits
import io.github.zhora87.bezmen.domain.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsControllerTest {
    private class FakeStore(var saved: Settings? = null) : SettingsStore {
        override suspend fun load(): Settings? = saved

        override suspend fun save(settings: Settings) {
            saved = settings
        }
    }

    @Test
    fun `defaults apply until a saved file is loaded`() = runTest {
        val c = SettingsController(this, FakeStore(Settings(DisplayUnits.PER_HUNDRED, "ru")))
        assertEquals(Settings(), c.state.value)

        advanceUntilIdle()

        assertEquals(Settings(DisplayUnits.PER_HUNDRED, "ru"), c.state.value)
    }

    @Test
    fun `changes are applied at once and saved`() = runTest {
        val store = FakeStore()
        val c = SettingsController(this, store)
        advanceUntilIdle()

        c.setUnits(DisplayUnits.PER_HUNDRED)
        c.setPack("uk")
        advanceUntilIdle()

        assertEquals(Settings(DisplayUnits.PER_HUNDRED, "uk"), store.saved)
        c.setPack(null)
        advanceUntilIdle()
        assertNull(store.saved?.packId)
    }

    @Test
    fun `json store round trip and empty file`() = runTest {
        var file: String? = null
        val store = JsonSettingsStore(read = { file }, write = { file = it })

        assertNull(store.load())
        store.save(Settings(DisplayUnits.PER_HUNDRED, "en-generic"))

        assertEquals(Settings(DisplayUnits.PER_HUNDRED, "en-generic"), store.load())
        assertNull(JsonSettingsStore(read = { "garbage" }, write = {}).load())
    }
}
