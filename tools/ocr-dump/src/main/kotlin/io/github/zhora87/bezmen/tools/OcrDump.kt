package io.github.zhora87.bezmen.tools

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.ocr.image.RgbImage
import io.github.zhora87.bezmen.ocr.paddle.FileModelStore
import io.github.zhora87.bezmen.ocr.paddle.PaddleOnnxOcrEngine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * Runs the shipped OCR engine (the same code and models as the app) over corpus photos and writes
 * `corpus/ocr/paddle-onnx/<id>.json`. The parser is then tuned on exactly what the app will see.
 *
 *     ./gradlew :tools:ocr-dump:run --args="corpus [--pack uk] [--ids uk-atb-003,uk-atb-010]"
 */
private const val ENGINE = "paddle-onnx"
private const val RGB_MASK = 0xFFFFFF
private const val PRECISION = 10_000f
private const val EXIT_USAGE = 2

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

private class DumpOptions(val corpus: File, val pack: String?, val ids: Set<String>)

fun main(args: Array<String>) {
    val options = parse(args) ?: run {
        println("usage: ocr-dump <corpus-dir> [--pack <id>] [--ids <id,id,...>]")
        exitProcess(EXIT_USAGE)
    }
    val modelsDir = File(System.getProperty("bezmen.models.dir") ?: error("bezmen.models.dir is not set"))
    val cases = findCases(options)
    if (cases.isEmpty()) {
        println("no photos with expected values under ${options.corpus}/images")
        exitProcess(EXIT_USAGE)
    }
    val out = File(options.corpus, "ocr/$ENGINE").apply { mkdirs() }
    val engines = mutableMapOf<String, PaddleOnnxOcrEngine>()
    try {
        for ((id, packId, photo) in cases) {
            val engine = engines.getOrPut(packId) {
                PaddleOnnxOcrEngine(FileModelStore(modelsDir), loadPack(packId).script)
            }
            var lines: List<OcrLine> = emptyList()
            val ms = measureTimeMillis { lines = engine.recognize(ImageIO.read(photo).toRgb()).map(::rounded) }
            File(out, "$id.json").writeText(json.encodeToString(ListSerializer(OcrLine.serializer()), lines) + "\n")
            println("$id: ${lines.size} lines, $ms ms")
        }
    } finally {
        engines.values.forEach { it.close() }
    }
}

private fun parse(args: Array<String>): DumpOptions? {
    val corpus = args.firstOrNull()?.takeUnless { it.startsWith("--") } ?: return null
    var pack: String? = null
    var ids = emptySet<String>()
    var i = 1
    while (i < args.size) {
        val value = args.getOrNull(i + 1) ?: return null
        when (args[i]) {
            "--pack" -> pack = value
            "--ids" -> ids = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            else -> return null
        }
        i += 2
    }
    return DumpOptions(File(corpus), pack, ids)
}

private data class DumpCase(val id: String, val pack: String, val photo: File)

/** Photos under images/<pack>/<store>/ that have an expected file; the pack comes from that file. */
private fun findCases(options: DumpOptions): List<DumpCase> {
    val root = File(options.corpus, "images")
    return root.listFiles { f -> f.isDirectory && !f.name.startsWith("_") }.orEmpty()
        .filter { options.pack == null || it.name == options.pack }
        .flatMap { packDir -> packDir.walkTopDown().filter { it.isFile && it.extension == "jpg" }.toList() }
        .map { it.nameWithoutExtension to it }
        .filter { (id, _) -> options.ids.isEmpty() || id in options.ids }
        .mapNotNull { (id, photo) ->
            val expected = File(options.corpus, "expected/$id.json").takeIf { it.isFile } ?: return@mapNotNull null
            val pack = json.parseToJsonElement(expected.readText()).let { el ->
                (el as? kotlinx.serialization.json.JsonObject)?.get("pack")?.toString()?.trim('"')
            } ?: return@mapNotNull null
            DumpCase(id, pack, photo)
        }
        .sortedBy { it.id }
}

private fun loadPack(id: String): LocalePack {
    val text = Thread.currentThread().contextClassLoader.getResource("$id.json")?.readText()
        ?: error("locale pack '$id' is not bundled")
    return LocalePack.fromJson(text)
}

private fun BufferedImage.toRgb(): RgbImage {
    val argb = IntArray(width * height).also { getRGB(0, 0, width, height, it, 0, width) }
    return RgbImage(width, height, IntArray(argb.size) { argb[it] and RGB_MASK })
}

/** Four decimals, like the RapidOCR dumps: keeps diffs between regenerations readable. */
private fun rounded(line: OcrLine): OcrLine {
    fun r(v: Float) = (v * PRECISION).roundToInt() / PRECISION
    val b = line.box
    return OcrLine(line.text, Box(r(b.left), r(b.top), r(b.right), r(b.bottom)), r(line.confidence))
}
