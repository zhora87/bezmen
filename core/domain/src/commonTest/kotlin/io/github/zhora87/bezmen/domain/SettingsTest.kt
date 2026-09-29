package io.github.zhora87.bezmen.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    @Test
    fun `standard units price by the kilogram, litre and piece`() {
        assertEquals(References.PER_1_KG, DisplayUnits.STANDARD.referenceFor(Dimension.MASS))
        assertEquals(References.PER_1_L, DisplayUnits.STANDARD.referenceFor(Dimension.VOLUME))
        assertEquals(References.PER_PIECE, DisplayUnits.STANDARD.referenceFor(Dimension.COUNT))
    }

    @Test
    fun `per-hundred units price by 100 g and 100 ml`() {
        assertEquals(References.PER_100_G, DisplayUnits.PER_HUNDRED.referenceFor(Dimension.MASS))
        assertEquals(References.PER_100_ML, DisplayUnits.PER_HUNDRED.referenceFor(Dimension.VOLUME))
        assertEquals(References.PER_PIECE, DisplayUnits.PER_HUNDRED.referenceFor(Dimension.COUNT))
    }

    @Test
    fun `settings round-trip through JSON and unknown keys are ignored`() {
        val json = Json { ignoreUnknownKeys = true }
        val settings = Settings(units = DisplayUnits.PER_HUNDRED, packId = "ru")

        assertEquals(
            settings,
            json.decodeFromString(Settings.serializer(), json.encodeToString(Settings.serializer(), settings))
        )
        assertEquals(Settings(), json.decodeFromString(Settings.serializer(), """{"future":1}"""))
    }
}
