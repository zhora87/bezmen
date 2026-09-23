package io.github.zhora87.bezmen.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptFoldingTest {
    @Test
    fun `latin look-alikes fold onto cyrillic`() {
        assertEquals("Кг", ScriptFolding.fold("Kr", Script.CYRILLIC))
        assertEquals("ГРН.", ScriptFolding.fold("FPH.", Script.CYRILLIC))
        assertEquals("ЗНИЖКА", ScriptFolding.fold("3НИЖKA", Script.CYRILLIC))
        assertEquals("1кг", ScriptFolding.fold("1кr", Script.CYRILLIC))
    }

    @Test
    fun `digits inside numbers are left alone`() {
        assertEquals("290 70", ScriptFolding.fold("290 70", Script.CYRILLIC))
        assertEquals("300", ScriptFolding.fold("300", Script.CYRILLIC))
    }

    @Test
    fun `cyrillic look-alikes fold onto latin`() {
        assertEquals("Nutella", ScriptFolding.fold("Nutеllа", Script.LATIN))
        assertEquals("per kg", ScriptFolding.fold("рer кg", Script.LATIN))
    }
}
