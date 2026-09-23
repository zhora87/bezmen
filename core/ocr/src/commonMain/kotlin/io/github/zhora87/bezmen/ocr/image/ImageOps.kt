package io.github.zhora87.bezmen.ocr.image

import kotlin.math.roundToInt

/** Pure-Kotlin image operations: enough for OCR preprocessing, no platform bitmaps involved. */
object ImageOps {
    private const val BYTE_MAX = 255
    private const val MAX_BYTE_F = 255f
    private const val CHANNELS = 3

    /** Bilinear resize. */
    fun resize(src: RgbImage, width: Int, height: Int): RgbImage {
        if (width == src.width && height == src.height) return src
        val out = IntArray(width * height)
        val scaleX = src.width.toFloat() / width
        val scaleY = src.height.toFloat() / height
        for (y in 0 until height) {
            val sy = ((y + HALF) * scaleY - HALF).coerceIn(0f, (src.height - 1).toFloat())
            val y0 = sy.toInt()
            val y1 = minOf(y0 + 1, src.height - 1)
            val fy = sy - y0
            for (x in 0 until width) {
                val sx = ((x + HALF) * scaleX - HALF).coerceIn(0f, (src.width - 1).toFloat())
                val x0 = sx.toInt()
                val x1 = minOf(x0 + 1, src.width - 1)
                val fx = sx - x0
                out[y * width + x] = blend(src[x0, y0], src[x1, y0], src[x0, y1], src[x1, y1], fx, fy)
            }
        }
        return RgbImage(width, height, out)
    }

    private fun blend(p00: Int, p10: Int, p01: Int, p11: Int, fx: Float, fy: Float): Int {
        fun channel(shift: Int): Int {
            val c00 = (p00 shr shift) and BYTE_MAX
            val c10 = (p10 shr shift) and BYTE_MAX
            val c01 = (p01 shr shift) and BYTE_MAX
            val c11 = (p11 shr shift) and BYTE_MAX
            val top = c00 + (c10 - c00) * fx
            val bottom = c01 + (c11 - c01) * fx
            return (top + (bottom - top) * fy).roundToInt().coerceIn(0, BYTE_MAX)
        }
        return RgbImage.rgb(channel(RgbImage.RED_SHIFT), channel(RgbImage.GREEN_SHIFT), channel(RgbImage.BLUE_SHIFT))
    }

    /** Axis-aligned crop; the rectangle is clamped to the image. */
    fun crop(src: RgbImage, left: Int, top: Int, right: Int, bottom: Int): RgbImage? {
        val l = left.coerceIn(0, src.width - 1)
        val t = top.coerceIn(0, src.height - 1)
        val r = right.coerceIn(l + 1, src.width)
        val b = bottom.coerceIn(t + 1, src.height)
        if (r - l < 1 || b - t < 1) return null
        val w = r - l
        val h = b - t
        val out = IntArray(w * h)
        for (y in 0 until h) {
            src.pixels.copyInto(out, y * w, (t + y) * src.width + l, (t + y) * src.width + r)
        }
        return RgbImage(w, h, out)
    }

    /** Rotates by a multiple of 90 degrees clockwise. */
    fun rotate(src: RgbImage, degrees: Int): RgbImage {
        return when (((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN) {
            0 -> src
            QUARTER_TURN -> transform(src.height, src.width) { x, y -> src[y, src.height - 1 - x] }
            HALF_TURN -> transform(src.width, src.height) { x, y -> src[src.width - 1 - x, src.height - 1 - y] }
            else -> transform(src.height, src.width) { x, y -> src[src.width - 1 - y, x] }
        }
    }

    private fun transform(width: Int, height: Int, sample: (Int, Int) -> Int): RgbImage {
        val out = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) out[y * width + x] = sample(x, y)
        return RgbImage(width, height, out)
    }

    /** CHW float tensor, RGB order, `(p / 255 - mean[c]) / std[c]` per channel. */
    fun toTensor(img: RgbImage, mean: FloatArray, std: FloatArray): FloatArray {
        val plane = img.width * img.height
        val out = FloatArray(CHANNELS * plane)
        for (i in 0 until plane) {
            val p = img.pixels[i]
            out[i] = (RgbImage.red(p) / MAX_BYTE_F - mean[0]) / std[0]
            out[plane + i] = (RgbImage.green(p) / MAX_BYTE_F - mean[1]) / std[1]
            out[2 * plane + i] = (RgbImage.blue(p) / MAX_BYTE_F - mean[2]) / std[2]
        }
        return out
    }

    /** Camera NV21 (YUV420SP) frame to RGB. */
    fun fromNv21(bytes: ByteArray, width: Int, height: Int): RgbImage {
        val out = IntArray(width * height)
        val frameSize = width * height
        for (y in 0 until height) {
            val uvRow = frameSize + (y shr 1) * width
            for (x in 0 until width) {
                val yy = (bytes[y * width + x].toInt() and BYTE_MAX) - Y_OFFSET
                val uvIndex = uvRow + (x and 1.inv())
                val v = (bytes[uvIndex].toInt() and BYTE_MAX) - UV_OFFSET
                val u = (bytes[uvIndex + 1].toInt() and BYTE_MAX) - UV_OFFSET
                val yScaled = Y_SCALE * maxOf(yy, 0)
                val r = ((yScaled + V_TO_R * v) shr FIXED_SHIFT).coerceIn(0, BYTE_MAX)
                val g = ((yScaled - U_TO_G * u - V_TO_G * v) shr FIXED_SHIFT).coerceIn(0, BYTE_MAX)
                val b = ((yScaled + U_TO_B * u) shr FIXED_SHIFT).coerceIn(0, BYTE_MAX)
                out[y * width + x] = RgbImage.rgb(r, g, b)
            }
        }
        return RgbImage(width, height, out)
    }

    /** RGBA_8888 bytes to RGB. */
    fun fromRgba(bytes: ByteArray, width: Int, height: Int): RgbImage {
        val out = IntArray(width * height)
        for (i in 0 until width * height) {
            val o = i * RGBA_STRIDE
            val r = bytes[o].toInt() and BYTE_MAX
            val g = bytes[o + 1].toInt() and BYTE_MAX
            val b = bytes[o + 2].toInt() and BYTE_MAX
            out[i] = RgbImage.rgb(r, g, b)
        }
        return RgbImage(width, height, out)
    }

    private const val HALF = 0.5f
    private const val FULL_TURN = 360
    private const val QUARTER_TURN = 90
    private const val HALF_TURN = 180
    private const val RGBA_STRIDE = 4

    // Fixed-point BT.601 YUV to RGB, 10 fractional bits.
    private const val FIXED_SHIFT = 10
    private const val Y_OFFSET = 16
    private const val UV_OFFSET = 128
    private const val Y_SCALE = 1192
    private const val V_TO_R = 1634
    private const val U_TO_G = 400
    private const val V_TO_G = 833
    private const val U_TO_B = 2066
}
