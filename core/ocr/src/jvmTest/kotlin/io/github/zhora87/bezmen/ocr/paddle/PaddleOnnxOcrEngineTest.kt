package io.github.zhora87.bezmen.ocr.paddle

import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Script
import io.github.zhora87.bezmen.domain.ScriptFolding
import io.github.zhora87.bezmen.ocr.image.RgbImage
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** Runs the real PP-OCRv5 models (fetched by :core:ocr:fetchModels) on the JVM. */
class PaddleOnnxOcrEngineTest {
    private val modelsDir = File(System.getProperty("bezmen.models.dir") ?: error("bezmen.models.dir not set"))
    private val corpusDir = File(System.getProperty("bezmen.corpus.dir") ?: ".")

    private fun rendered(vararg lines: Pair<String, Int>): RgbImage {
        val img = BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        g.color = Color.BLACK
        var y = 40
        for ((text, size) in lines) {
            g.font = Font(Font.SANS_SERIF, Font.BOLD, size)
            y += size
            g.drawString(text, 30, y)
            y += size / 3
        }
        g.dispose()
        return img.toRgb()
    }

    private fun BufferedImage.toRgb(): RgbImage {
        val argb = IntArray(width * height).also { getRGB(0, 0, width, height, it, 0, width) }
        return RgbImage(width, height, IntArray(argb.size) { argb[it] and RGB_MASK })
    }

    private fun digits(lines: List<OcrLine>) = lines.joinToString(" ") { it.text }.filter { it.isDigit() || it == ' ' }

    @Test
    fun `reads a rendered cyrillic price tag`() {
        PaddleOnnxOcrEngine(FileModelStore(modelsDir), Script.CYRILLIC).use { engine ->
            val lines = engine.recognize(rendered("Молоко 900 мл" to 40, "249,90 грн" to 96))
            val text = lines.joinToString(" | ") { "${it.text} (${"%.2f".format(it.confidence)})" }
            println("rendered tag -> $text")

            assertTrue(lines.size >= 2, "expected at least two lines, got: $text")
            assertTrue("249" in digits(lines) && "900" in digits(lines), "digits missing in: $text")
            val cyrillic = lines.any { "мл" in it.text.lowercase() || "грн" in it.text.lowercase() }
            assertTrue(cyrillic, "no cyrillic unit/currency in: $text")
            assertTrue(lines.all { it.box.left in 0f..1f && it.box.bottom in 0f..1f }, "boxes must be normalised")
        }
    }

    @Test
    fun `reads a corpus photo when it is available locally`() {
        val photo = File(corpusDir, "images/uk/atb/uk-atb-004.jpg")
        if (!photo.isFile) {
            println("corpus photo not present, skipping")
            return
        }
        PaddleOnnxOcrEngine(FileModelStore(modelsDir), Script.CYRILLIC).use { engine ->
            val lines = engine.recognize(ImageIO.read(photo).toRgb())
            val text = lines.joinToString(" | ") { it.text }
            println("uk-atb-004 -> $text")

            assertTrue("99" in digits(lines) && "90" in digits(lines), "price digits missing in: $text")
            // OCR emits Latin look-alikes inside Cyrillic words ("Маслo"); the parser folds them, so do we here.
            val named = lines.any { "масло" in ScriptFolding.fold(it.text.lowercase(), Script.CYRILLIC) }
            assertTrue(named, "product name missing in: $text")
        }
    }

    private inline fun <T> PaddleOnnxOcrEngine.use(block: (PaddleOnnxOcrEngine) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private companion object {
        const val RGB_MASK = 0xFFFFFF
    }
}
