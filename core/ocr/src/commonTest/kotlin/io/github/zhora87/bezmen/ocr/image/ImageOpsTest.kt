package io.github.zhora87.bezmen.ocr.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImageOpsTest {
    private fun image(w: Int, h: Int, f: (Int, Int) -> Int) = RgbImage(w, h, IntArray(w * h) { f(it % w, it / w) })

    @Test
    fun `resize keeps flat colours and dimensions`() {
        val src = image(8, 4) { _, _ -> RgbImage.rgb(10, 20, 30) }

        val out = ImageOps.resize(src, 4, 2)

        assertEquals(4, out.width)
        assertEquals(2, out.height)
        assertEquals(RgbImage.rgb(10, 20, 30), out[3, 1])
    }

    @Test
    fun `crop clamps to the image and copies pixels`() {
        val src = image(6, 6) { x, y -> RgbImage.rgb(x, y, 0) }

        val out = assertNotNull(ImageOps.crop(src, 2, 3, 100, 100))

        assertEquals(4, out.width)
        assertEquals(3, out.height)
        assertEquals(RgbImage.rgb(2, 3, 0), out[0, 0])
        assertEquals(RgbImage.rgb(5, 5, 0), out[3, 2])
    }

    @Test
    fun `rotate 90 moves the top-left pixel to the top-right`() {
        val src = image(3, 2) { x, y -> RgbImage.rgb(x, y, 0) }

        val out = ImageOps.rotate(src, 90)

        assertEquals(2, out.width)
        assertEquals(3, out.height)
        assertEquals(RgbImage.rgb(0, 0, 0), out[1, 0])
        assertEquals(src, ImageOps.rotate(src, 360))
    }

    @Test
    fun `tensor is CHW and normalised`() {
        val src = image(2, 1) { x, _ -> if (x == 0) RgbImage.rgb(255, 0, 0) else RgbImage.rgb(0, 0, 255) }

        val t = ImageOps.toTensor(src, floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f))

        assertEquals(6, t.size)
        assertEquals(1f, t[0], 1e-6f) // R of pixel 0
        assertEquals(-1f, t[1], 1e-6f) // R of pixel 1
        assertEquals(1f, t[5], 1e-6f) // B of pixel 1
    }

    @Test
    fun `nv21 grey frame decodes to grey pixels`() {
        val w = 4
        val h = 2
        val bytes = ByteArray(w * h * 3 / 2) { if (it < w * h) 128.toByte() else 128.toByte() }

        val out = ImageOps.fromNv21(bytes, w, h)

        val p = out[0, 0]
        assertEquals(RgbImage.red(p), RgbImage.green(p))
        assertEquals(RgbImage.green(p), RgbImage.blue(p))
        assertEquals(130, RgbImage.red(p), "expected mid grey, got ${RgbImage.red(p)}") // (128-16)*1192>>10
    }
}
