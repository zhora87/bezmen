package io.github.zhora87.bezmen.ocr.image

/** Packed 0xRRGGBB pixels, row-major. The only image type the OCR pipeline works with. */
class RgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0) { "Empty image ${width}x$height" }
        require(pixels.size == width * height) { "pixels has ${pixels.size} entries, expected ${width * height}" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    companion object {
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BLUE_SHIFT = 0
        const val CHANNEL_MASK = 0xFF

        fun red(p: Int): Int = (p shr RED_SHIFT) and CHANNEL_MASK
        fun green(p: Int): Int = (p shr GREEN_SHIFT) and CHANNEL_MASK
        fun blue(p: Int): Int = p and CHANNEL_MASK
        fun rgb(r: Int, g: Int, b: Int): Int = (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    }
}
