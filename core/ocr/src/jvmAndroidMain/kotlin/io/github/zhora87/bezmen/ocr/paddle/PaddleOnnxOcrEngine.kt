package io.github.zhora87.bezmen.ocr.paddle

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Script
import io.github.zhora87.bezmen.ocr.OcrEngine
import io.github.zhora87.bezmen.ocr.OcrImage
import io.github.zhora87.bezmen.ocr.image.ImageOps
import io.github.zhora87.bezmen.ocr.image.RgbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * PaddleOCR PP-OCRv5 mobile models through ONNX Runtime: DB text detection, then CTC recognition
 * of every detected line. Written once against the ONNX Runtime Java API, which is identical on
 * the JVM and on Android, so the same code is exercised by desktop tests and shipped in the app.
 * Everything runs on the device; the engine never touches the network.
 */
class PaddleOnnxOcrEngine(
    store: ModelStore,
    private val script: Script,
    threads: Int = defaultThreads(),
    accelerator: Accelerator = Accelerator.CPU,
    /** Longest side of the detector input; the frame is scaled down to it. */
    private val detectionMaxSide: Int = DET_MAX_SIDE,
    /** Second, coarse detector pass for price digits too large for the first one; 0 turns it off. */
    private val coarseSide: Int = COARSE_SIDE,
    private val postProcessor: DbPostProcessor = DbPostProcessor(),
) : OcrEngine {
    /**
     * Where ONNX Runtime executes the graph. CPU is the default: measured on an 8-core arm64 phone,
     * XNNPACK was slower on the detector and NNAPI no faster, while both add start-up cost.
     * XNNPACK and NNAPI only exist in the Android build and stay available for benchmarking.
     */
    enum class Accelerator { CPU, XNNPACK, NNAPI }

    override val id: String = "paddle-onnx-${script.name.lowercase()}-${accelerator.name.lowercase()}"
    override val supportedScripts: Set<Script> = setOf(script)

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        when (accelerator) {
            Accelerator.CPU -> setIntraOpNumThreads(threads)
            Accelerator.XNNPACK -> {
                addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
                setIntraOpNumThreads(1)
            }
            Accelerator.NNAPI -> {
                addNnapi()
                setIntraOpNumThreads(threads)
            }
        }
    }
    private val detector: OrtSession = env.createSession(store.read(Models.DETECTION), options)
    private val recognizer: OrtSession
    private val decoder: CtcDecoder

    init {
        val bytes = store.read(Models.recognitionFor(script))
        recognizer = env.createSession(bytes, options)
        // Parsed from the file bytes, not via recognizer.metadata: see OnnxMetadata for why.
        val dictionary = OnnxMetadata.read(bytes, DICTIONARY_KEY)
            ?: error("recognition model has no '$DICTIONARY_KEY' metadata")
        decoder = CtcDecoder.fromMetadata(dictionary)
    }

    override suspend fun recognize(image: OcrImage): List<OcrLine> = withContext(Dispatchers.Default) {
        recognize(toRgb(image))
    }

    /** Synchronous entry point for tests and tools. */
    /**
     * Lines of the fine detector pass; with [coarseSide] set, tall boxes of a coarse pass are read too
     * and replace the fine lines where they read more digits (large price digits, see ScaleMerge).
     */
    fun recognize(image: RgbImage): List<OcrLine> {
        val fine = detectBoxes(image).mapNotNull { box -> recognizeBox(image, box) }
        if (coarseSide <= 0 || coarseSide >= detectionMaxSide) return fine
        val coarse = detect(image, coarseSide)
            .filter { it.height >= image.height * COARSE_MIN_HEIGHT }
            .mapNotNull { box -> recognizeBox(image, box) }
        return ScaleMerge.merge(fine, coarse)
    }

    /** Boxes of the fine detector pass, in image pixels: for diagnostics and overlays. */
    fun detectBoxes(image: RgbImage): List<DbPostProcessor.TextBox> = detect(image, detectionMaxSide)

    /** Boxes found with the image scaled to [maxSide], in image pixels. */
    private fun detect(image: RgbImage, maxSide: Int): List<DbPostProcessor.TextBox> {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(image.width, image.height))
        val width = multipleOf32(image.width * scale)
        val height = multipleOf32(image.height * scale)
        val resized = ImageOps.resize(image, width, height)
        val input = ImageOps.toTensor(resized, DET_MEAN, DET_STD)
        val probabilities = run(detector, input, height, width)
        val sx = image.width.toFloat() / width
        val sy = image.height.toFloat() / height
        return postProcessor.boxes(probabilities, width, height).map { box ->
            DbPostProcessor.TextBox(
                left = (box.left * sx).roundToInt(),
                top = (box.top * sy).roundToInt(),
                right = (box.right * sx).roundToInt(),
                bottom = (box.bottom * sy).roundToInt(),
                score = box.score,
            )
        }
    }

    private fun recognizeBox(image: RgbImage, box: DbPostProcessor.TextBox): OcrLine? {
        val left = box.left
        val top = box.top
        val right = box.right
        val bottom = box.bottom
        val crop = ImageOps.crop(image, left, top, right, bottom) ?: return null
        val width = (REC_HEIGHT * crop.width.toFloat() / crop.height).roundToInt()
            .coerceIn(REC_MIN_WIDTH, REC_MAX_WIDTH)
        val input = ImageOps.toTensor(ImageOps.resize(crop, width, REC_HEIGHT), REC_MEAN, REC_STD)
        val decoded = recognizer.use(input, REC_HEIGHT, width) { tensor, shape ->
            decoder.decode(tensor, shape[1].toInt(), shape[2].toInt())
        }
        if (decoded.text.isEmpty()) return null
        val normalized = Box(
            left = left.toFloat() / image.width,
            top = top.toFloat() / image.height,
            right = right.toFloat() / image.width,
            bottom = bottom.toFloat() / image.height,
        )
        return OcrLine(decoded.text, normalized, decoded.confidence)
    }

    /** Runs the detector and returns the flat probability map. */
    private fun run(session: OrtSession, input: FloatArray, height: Int, width: Int): FloatArray =
        session.use(input, height, width) { tensor, _ -> tensor }

    private inline fun <T> OrtSession.use(
        input: FloatArray,
        height: Int,
        width: Int,
        block: (FloatArray, LongArray) -> T,
    ): T {
        val shape = longArrayOf(1, CHANNELS, height.toLong(), width.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            run(mapOf(inputNames.first() to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val buffer = output.floatBuffer
                val data = FloatArray(buffer.remaining())
                buffer.get(data)
                return block(data, output.info.shape)
            }
        }
    }

    private fun toRgb(image: OcrImage): RgbImage {
        val rgb = when (image.format) {
            OcrImage.Format.RGBA_8888 -> ImageOps.fromRgba(image.bytes, image.width, image.height)
            OcrImage.Format.NV21 -> ImageOps.fromNv21(image.bytes, image.width, image.height)
        }
        return ImageOps.rotate(rgb, image.rotationDegrees)
    }

    override fun close() {
        detector.close()
        recognizer.close()
        options.close()
    }

    private fun multipleOf32(value: Float): Int = maxOf(MULTIPLE, (value / MULTIPLE).roundToInt() * MULTIPLE)

    companion object {
        private const val DICTIONARY_KEY = "character"

        /** Four intra-op threads saturate a mobile SoC on the detector; more only adds scheduling noise. */
        const val MAX_DEFAULT_THREADS = 4

        fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_DEFAULT_THREADS)

        private const val CHANNELS = 3L
        private const val MULTIPLE = 32
        const val DET_MAX_SIDE = 960

        /** Price digits half the tag tall are the size the detector knows at this input. */
        const val COARSE_SIDE = 480

        /** Only coarse boxes this tall (share of the image height) are read: they are the price digits. */
        private const val COARSE_MIN_HEIGHT = 0.1f
        private val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
        private const val REC_HEIGHT = 48
        private const val REC_MIN_WIDTH = 16
        private const val REC_MAX_WIDTH = 2048
        private val REC_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        private val REC_STD = floatArrayOf(0.5f, 0.5f, 0.5f)
    }
}
