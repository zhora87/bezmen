package io.github.zhora87.bezmen.ocr.image

import kotlin.math.roundToInt

/** Resizing for [ImageOps.resize]: block averaging for strong reductions, then bilinear sampling. */
internal object Resampling {
    private const val BYTE_MAX = 255
    private const val HALF = 0.5f

    fun resize(src: RgbImage, width: Int, height: Int): RgbImage {
        if (width == src.width && height == src.height) return src
        val blockX = src.width / width
        val blockY = src.height / height
        if (blockX >= 2 || blockY >= 2) return bilinear(shrink(src, maxOf(1, blockX), maxOf(1, blockY)), width, height)
        return bilinear(src, width, height)
    }

    /** Averages [bx] x [by] blocks; the right and bottom remainders are dropped (less than one block). */
    private fun shrink(src: RgbImage, bx: Int, by: Int): RgbImage {
        val w = src.width / bx
        val h = src.height / by
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) out[y * w + x] = blockAverage(src, x * bx, y * by, bx, by)
        }
        return RgbImage(w, h, out)
    }

    private fun blockAverage(src: RgbImage, left: Int, top: Int, bx: Int, by: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        for (dy in 0 until by) {
            val row = (top + dy) * src.width + left
            for (dx in 0 until bx) {
                val p = src.pixels[row + dx]
                r += RgbImage.red(p)
                g += RgbImage.green(p)
                b += RgbImage.blue(p)
            }
        }
        val area = bx * by
        return RgbImage.rgb(r / area, g / area, b / area)
    }

    private fun bilinear(src: RgbImage, width: Int, height: Int): RgbImage {
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
}
