package io.github.zhora87.bezmen

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.MeasureUnit
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.Script
import io.github.zhora87.bezmen.domain.parser.ParseResult
import io.github.zhora87.bezmen.domain.parser.ParsedTag
import io.github.zhora87.bezmen.domain.parser.PriceTagParser
import io.github.zhora87.bezmen.ocr.image.ImageOps
import io.github.zhora87.bezmen.ocr.image.RgbImage
import io.github.zhora87.bezmen.ocr.paddle.AssetModelStore
import io.github.zhora87.bezmen.ocr.paddle.PaddleOnnxOcrEngine
import io.github.zhora87.bezmen.ocr.paddle.PaddleOnnxOcrEngine.Accelerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.system.measureNanoTime

/**
 * Runs the shipped OCR engine on the device: proves the models load from the APK, checks the
 * HardSwish issue on the Android ONNX Runtime build, benchmarks execution providers and, when the
 * local corpus was packed into the test APK, measures the whole pipeline end to end.
 * Results go to logcat (tag BezmenDevice) and to <external files>/bezmen-device-report.json,
 * rewritten after every configuration so a hang in a later one loses nothing.
 */
@RunWith(AndroidJUnit4::class)
class OcrDeviceTest {
    private val app: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val test: Context = InstrumentationRegistry.getInstrumentation().context
    private val report = JSONObject()
    private val reportFile = File(app.getExternalFilesDir(null), "bezmen-device-report.json")

    /** Execution provider, intra-op threads and how many corpus photos to run through it. */
    private data class Config(val accelerator: Accelerator, val threads: Int, val cases: Int)

    private inner class Case(val id: String, val asset: String, val priceMinor: Long?, val quantity: Quantity?) {
        /** Decoded on demand: the test process has a small heap and the corpus is dozens of photos. */
        fun decode(): RgbImage = test.assets.open(asset).use { BitmapFactory.decodeStream(it) }.toRgb()
    }

    @Test
    fun shippedModelsReadARenderedTag() {
        val engine = engine(Accelerator.CPU, 2)
        val lines = try {
            engine.recognize(rendered())
        } finally {
            engine.close()
        }
        val text = lines.joinToString(" | ") { it.text }
        log("rendered -> $text")
        report.put("rendered", text)
        assertTrue("digits missing in: $text", "249" in digits(lines) && "900" in digits(lines))
    }

    /** The original PP-OCRv5 detector (with HardSwish) is only present when the probe asset was packed. */
    @Test
    fun originalHardSwishModelProbe() {
        val bytes = readAsset(PROBE_MODEL) ?: return
        val verdict = probeDetector(bytes)
        log(verdict)
        report.put("hardswishProbe", verdict)
    }

    @Test
    fun benchmarkAndCorpus() {
        report.put("device", deviceDescription())
        val corpus = loadCorpus()
        val packJson = checkNotNull(readAsset("locale-packs/uk.json")).decodeToString()
        val parser = PriceTagParser(LocalePack.fromJson(packJson))
        val results = JSONArray()
        var anyWorked = false
        for (config in configs(corpus.size)) {
            val entry = JSONObject().put("accelerator", config.accelerator.name).put("threads", config.threads)
            runCatching { measure(config, corpus.take(config.cases), parser, entry) }
                .onSuccess { anyWorked = true }
                .onFailure { entry.put("error", it.toString().take(ERROR_LENGTH)) }
            log(entry.toString())
            results.put(entry)
            report.put("configs", results)
            reportFile.writeText(report.toString(2))
        }
        log("report written to $reportFile")
        assertTrue("no configuration ran", anyWorked)
    }

    /** NNAPI goes last and on a handful of photos: on recent Android it may fall back to a slow path. */
    private fun configs(corpusSize: Int): List<Config> = listOf(
        Config(Accelerator.CPU, 2, corpusSize),
        Config(Accelerator.CPU, 4, corpusSize),
        Config(Accelerator.XNNPACK, 4, corpusSize),
        Config(Accelerator.XNNPACK, 2, minOf(corpusSize, SAMPLE_LARGE)),
        Config(Accelerator.NNAPI, 2, minOf(corpusSize, SAMPLE_SMALL)),
    )

    private fun measure(config: Config, cases: List<Case>, parser: PriceTagParser, entry: JSONObject) {
        var engine: PaddleOnnxOcrEngine? = null
        val loadMs = measureNanoTime { engine = engine(config.accelerator, config.threads) } / NANOS_PER_MS
        val e = checkNotNull(engine)
        try {
            e.recognize(rendered()) // warm-up
            val renderedMs = List(RENDERED_RUNS) { measureNanoTime { e.recognize(rendered()) } / NANOS_PER_MS }.sorted()
            entry.put("loadMs", loadMs).put("renderedMedianMs", renderedMs[RENDERED_RUNS / 2])
            if (cases.isNotEmpty()) runCorpus(e, cases, parser, entry)
        } finally {
            e.close()
        }
    }

    private fun runCorpus(e: PaddleOnnxOcrEngine, cases: List<Case>, parser: PriceTagParser, entry: JSONObject) {
        val times = mutableListOf<Long>()
        var priceOk = 0
        var quantityOk = 0
        var allOk = 0
        for (case in cases) {
            val image = case.decode()
            var lines: List<OcrLine> = emptyList()
            times += measureNanoTime { lines = e.recognize(image) } / NANOS_PER_MS
            val tag = parser.parse(lines).tagOrNull()
            val price = tag?.price?.value?.minor == case.priceMinor
            val quantity = sameQuantity(tag?.quantity?.value, case.quantity)
            if (price) priceOk++
            if (quantity) quantityOk++
            if (price && quantity) allOk++
        }
        times.sort()
        val n = times.size
        entry.put("corpusCases", n)
            .put("corpusMedianMs", times[n / 2])
            .put("corpusP90Ms", times[n * P90_PERCENT / PERCENT])
            .put("priceAccuracy", priceOk.toDouble() / n)
            .put("quantityAccuracy", quantityOk.toDouble() / n)
            .put("allAccuracy", allOk.toDouble() / n)
    }

    /** Same rule as the parser CLI: equal in base units within half a gram, millilitre or piece. */
    private fun sameQuantity(actual: Quantity?, expected: Quantity?): Boolean {
        if (actual == null || expected == null) return actual == expected
        if (actual.dimension != expected.dimension) return false
        return abs(actual.toBase().value - expected.toBase().value) < QUANTITY_TOLERANCE
    }

    private fun loadCorpus(): List<Case> =
        test.assets.list("corpus/images").orEmpty().filter { it.endsWith(".jpg") }.sorted().mapNotNull { name ->
            val id = name.removeSuffix(".jpg")
            val expected = readAsset("corpus/expected/$id.json")?.let { JSONObject(it.decodeToString()) }
            if (expected == null || expected.optBoolean("pending")) return@mapNotNull null
            val quantity = expected.optJSONObject("quantity")
                ?.let { Quantity(it.getDouble("value"), MeasureUnit.valueOf(it.getString("unit"))) }
            Case(id, "corpus/images/$name", expected.optJSONObject("price")?.optLong("minor"), quantity)
        }

    private fun probeDetector(bytes: ByteArray): String {
        val env = OrtEnvironment.getEnvironment()
        val image = ImageOps.resize(rendered(), PROBE_WIDTH, PROBE_HEIGHT)
        val input = ImageOps.toTensor(image, DET_MEAN, DET_STD)
        val shape = longArrayOf(1, 3, PROBE_HEIGHT.toLong(), PROBE_WIDTH.toLong())
        val out = env.createSession(bytes).use { session -> runDetector(env, session, input, shape) }
        val above = out.count { it > PROBE_THRESHOLD }
        val status = if (above > PROBE_MIN_ACTIVE) "WORKS (bug absent on Android)" else "DEAD (same bug as on the JVM)"
        return "original HardSwish detector on Android ORT: max=${out.max()} above0.3=$above -> $status"
    }

    private fun runDetector(env: OrtEnvironment, session: OrtSession, input: FloatArray, shape: LongArray): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                (result[0] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
            }
        }

    private fun readAsset(name: String): ByteArray? = try {
        test.assets.open(name).use { it.readBytes() }
    } catch (e: IOException) {
        log("asset $name is not packed: $e")
        null
    }

    private fun engine(accelerator: Accelerator, threads: Int) =
        PaddleOnnxOcrEngine(AssetModelStore(app.assets), Script.CYRILLIC, threads, accelerator)

    private fun deviceDescription(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.first()
        return "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, $abi, $cores cores"
    }

    private fun rendered(): RgbImage {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            isFakeBoldText = true
        }
        paint.textSize = 40f
        canvas.drawText("Молоко 900 мл", 30f, 80f, paint)
        paint.textSize = 96f
        canvas.drawText("249,90 грн", 30f, 220f, paint)
        return bitmap.toRgb()
    }

    private fun Bitmap.toRgb(): RgbImage {
        val argb = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        return RgbImage(width, height, IntArray(argb.size) { argb[it] and 0xFFFFFF })
    }

    private fun ParseResult.tagOrNull(): ParsedTag? = when (this) {
        is ParseResult.Success -> tag
        is ParseResult.NeedsInput -> tag
        ParseResult.Nothing -> null
    }

    private fun digits(lines: List<OcrLine>) = lines.joinToString(" ") { it.text }.filter { it.isDigit() || it == ' ' }

    private fun log(message: String) = Log.i(TAG, message)

    private companion object {
        const val TAG = "BezmenDevice"
        const val PROBE_MODEL = "probe/ch_PP-OCRv5_det_mobile.onnx"
        const val NANOS_PER_MS = 1_000_000L
        const val RENDERED_RUNS = 5
        const val SAMPLE_LARGE = 20
        const val SAMPLE_SMALL = 5
        const val ERROR_LENGTH = 300
        const val PERCENT = 100
        const val P90_PERCENT = 90
        const val QUANTITY_TOLERANCE = 0.5
        const val PROBE_WIDTH = 640
        const val PROBE_HEIGHT = 352
        const val PROBE_THRESHOLD = 0.3f
        const val PROBE_MIN_ACTIVE = 100
        val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
