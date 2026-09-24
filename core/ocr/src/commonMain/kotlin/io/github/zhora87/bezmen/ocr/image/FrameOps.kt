package io.github.zhora87.bezmen.ocr.image

import kotlin.math.roundToInt

/**
 * The viewfinder frame the user centres a price tag in, as fractions of the camera image, centred.
 * The preview shows the whole image (letterboxed), so the same fractions place the overlay.
 */
data class ViewfinderFrame(val widthFraction: Float, val heightFraction: Float) {
    init {
        require(widthFraction in MIN_SHARE..1f && heightFraction in MIN_SHARE..1f) {
            "frame must cover 10..100% of the image"
        }
    }

    companion object {
        private const val MIN_SHARE = 0.1f

        /** A shelf tag is wider than tall; the frame leaves room to hold the phone steady. */
        val PRICE_TAG = ViewfinderFrame(widthFraction = 0.86f, heightFraction = 0.36f)
    }
}

/** Camera-frame operations before OCR: crop to the viewfinder frame and judge focus. */
object FrameOps {
    /**
     * Below this [sharpness] a frame is certainly out of focus. Calibrated on the ATB corpus scaled
     * to a viewfinder crop: in-focus photos have a median of 755 and a 10th percentile of 380
     * (one low-contrast tag scores 44); a 7 px box blur gives a median of 97, a 13 px blur 30.
     * The threshold only catches hopeless frames; picking the sharpest of a burst does the rest.
     */
    const val BLUR_THRESHOLD = 60f

    private const val SHARPNESS_WIDTH = 480
    private const val LUMA_R = 299
    private const val LUMA_G = 587
    private const val LUMA_B = 114
    private const val LUMA_DIVISOR = 1000
    private const val NEIGHBOURS = 4

    /** The Laplacian needs a pixel on every side. */
    private const val MIN_SIDE = 3

    fun cropToFrame(image: RgbImage, frame: ViewfinderFrame): RgbImage {
        val w = (image.width * frame.widthFraction).roundToInt().coerceIn(1, image.width)
        val h = (image.height * frame.heightFraction).roundToInt().coerceIn(1, image.height)
        val left = (image.width - w) / 2
        val top = (image.height - h) / 2
        return checkNotNull(ImageOps.crop(image, left, top, left + w, top + h))
    }

    /**
     * Variance of the Laplacian of the luma at a fixed width: high for crisp edges, near zero for a
     * missed focus or motion blur. Scaling to a fixed width makes values comparable across cameras.
     */
    fun sharpness(image: RgbImage): Float {
        val height = maxOf(1, (image.height * SHARPNESS_WIDTH.toFloat() / image.width).roundToInt())
        val small = if (image.width > SHARPNESS_WIDTH) ImageOps.resize(image, SHARPNESS_WIDTH, height) else image
        if (small.width < MIN_SIDE || small.height < MIN_SIDE) return 0f
        val luma = IntArray(small.pixels.size) { luma(small.pixels[it]) }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until small.height - 1) {
            for (x in 1 until small.width - 1) {
                val i = y * small.width + x
                val around = luma[i - 1] + luma[i + 1] + luma[i - small.width] + luma[i + small.width]
                val lap = around - NEIGHBOURS * luma[i]
                sum += lap
                sumSq += lap.toDouble() * lap
                n++
            }
        }
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }

    private fun luma(p: Int): Int =
        (RgbImage.red(p) * LUMA_R + RgbImage.green(p) * LUMA_G + RgbImage.blue(p) * LUMA_B) / LUMA_DIVISOR
}
