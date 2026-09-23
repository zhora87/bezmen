package io.github.zhora87.bezmen.ocr.paddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DbPostProcessorTest {
    private val width = 64
    private val height = 32

    private fun map(vararg blobs: IntArray, value: Float = 0.9f): FloatArray {
        val prob = FloatArray(width * height)
        for (blob in blobs) {
            for (y in blob[1] until blob[3]) for (x in blob[0] until blob[2]) prob[y * width + x] = value
        }
        return prob
    }

    @Test
    fun `two separate blobs become two boxes in reading order, expanded by unclip`() {
        val prob = map(intArrayOf(40, 20, 60, 26), intArrayOf(4, 4, 24, 10))

        val boxes = DbPostProcessor().boxes(prob, width, height)

        assertEquals(2, boxes.size)
        val first = boxes[0]
        assertTrue(first.left < 4 && first.top < 4 && first.right > 24 && first.bottom > 10, "unclipped: $first")
        assertTrue(boxes[1].top > first.top)
        assertEquals(0.9f, first.score, 1e-6f)
    }

    @Test
    fun `weak and tiny blobs are dropped`() {
        val weak = map(intArrayOf(4, 4, 24, 10), value = 0.4f)
        val tiny = map(intArrayOf(4, 4, 6, 6))

        assertEquals(0, DbPostProcessor().boxes(weak, width, height).size)
        assertEquals(0, DbPostProcessor().boxes(tiny, width, height).size)
    }

    @Test
    fun `touching pixels form one component`() {
        val prob = map(intArrayOf(4, 4, 14, 10), intArrayOf(14, 4, 24, 10))

        assertEquals(1, DbPostProcessor().boxes(prob, width, height).size)
    }

    @Test
    fun `boxes stay inside the map`() {
        val prob = map(intArrayOf(0, 0, 20, 8))

        val box = DbPostProcessor().boxes(prob, width, height).single()

        assertTrue(box.left >= 0 && box.top >= 0 && box.right <= width && box.bottom <= height)
    }
}
