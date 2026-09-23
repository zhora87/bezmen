package io.github.zhora87.bezmen.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class BoxTest {
    @Test
    fun `slice splits a line box proportionally by character offsets`() {
        val line = Box(0.0f, 0.0f, 1.0f, 0.1f)

        val slice = line.slice(from = 5, to = 10, total = 20)

        assertEquals(0.25f, slice.left, 1e-6f)
        assertEquals(0.5f, slice.right, 1e-6f)
        assertEquals(0.1f, slice.height, 1e-6f)
    }

    @Test
    fun `union covers both boxes`() {
        val a = Box(0.1f, 0.2f, 0.3f, 0.4f)
        val b = Box(0.25f, 0.1f, 0.5f, 0.3f)

        assertEquals(Box(0.1f, 0.1f, 0.5f, 0.4f), a.union(b))
    }
}
