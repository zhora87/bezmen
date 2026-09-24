package io.github.zhora87.bezmen.ocr.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameOpsTest {
    private fun image(w: Int, h: Int, f: (Int, Int) -> Int) = RgbImage(w, h, IntArray(w * h) { f(it % w, it / w) })

    /** Black and white stripes, a crude stand-in for printed text. */
    private fun stripes(w: Int, h: Int, period: Int) =
        image(w, h) { x, _ -> if ((x / period) % 2 == 0) 0 else RgbImage.rgb(255, 255, 255) }

    /** Box blur of the given radius, what a missed focus does to text. */
    private fun blurred(src: RgbImage, radius: Int) = image(src.width, src.height) { x, y ->
        var sum = 0
        var n = 0
        for (dx in -radius..radius) {
            val sx = (x + dx).coerceIn(0, src.width - 1)
            sum += RgbImage.red(src[sx, y])
            n++
        }
        val v = sum / n
        RgbImage.rgb(v, v, v)
    }

    @Test
    fun `flat image has zero sharpness`() {
        assertEquals(0f, FrameOps.sharpness(image(64, 32) { _, _ -> RgbImage.rgb(128, 128, 128) }))
    }

    @Test
    fun `sharp stripes score higher than the same stripes blurred`() {
        val sharp = stripes(400, 120, 6)

        val crisp = FrameOps.sharpness(sharp)
        val soft = FrameOps.sharpness(blurred(sharp, 4))

        assertTrue(crisp > soft * 3, "sharp $crisp vs blurred $soft")
    }

    @Test
    fun `sharpness does not depend on resolution`() {
        val small = FrameOps.sharpness(stripes(480, 120, 6))
        val large = FrameOps.sharpness(stripes(1920, 480, 24))

        assertTrue(large / small in 0.7f..1.4f, "small $small vs large $large")
    }

    @Test
    fun `frame fractions crop the centre of the image`() {
        val src = image(1000, 2000) { x, y -> RgbImage.rgb(x / 4, y / 8, 0) }

        val out = FrameOps.cropToFrame(src, ViewfinderFrame(widthFraction = 0.8f, heightFraction = 0.25f))

        assertEquals(800, out.width)
        assertEquals(500, out.height)
        assertEquals(src[100, 750], out[0, 0])
    }
}
