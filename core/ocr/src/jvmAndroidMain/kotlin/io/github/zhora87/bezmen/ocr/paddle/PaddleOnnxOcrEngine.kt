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
    threads: Int = DEFAULT_THREADS,
    private val postProcessor: DbPostProcessor = DbPostProcessor(),
) : OcrEngine {
    override val id: String = "paddle-onnx-${script.name.lowercase()}"
    override val supportedScripts: Set<Script> = setOf(script)

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val detector: OrtSession = env.createSession(store.read(Models.DETECTION), options)
    private val recognizer: OrtSession = env.createSession(store.read(Models.recognitionFor(script)), options)
    private val decoder: CtcDecoder = CtcDecoder.fromMetadata(
        recognizer.metadata.customMetadata[DICTIONARY_KEY]
            ?: error("recognition model has no '$DICTIONARY_KEY' metadata"),
    )

    override suspend fun recognize(image: OcrImage): List<OcrLine> = withContext(Dispatchers.Default) {
        recognize(toRgb(image))
    }

    /** Synchronous entry point for tests and tools. */
    fun recognize(image: RgbImage): List<OcrLine> {
        val detection = detect(image)
        return detection.boxes.mapNotNull { box -> recognizeBox(image, detection, box) }
    }

    /** Detection only, boxes in image pixels: for diagnostics and overlays. */
    fun detectBoxes(image: RgbImage): List<DbPostProcessor.TextBox> {
        val detection = detect(image)
        return detection.boxes.map { box ->
            DbPostProcessor.TextBox(
                left = (box.left * detection.scaleX).roundToInt(),
                top = (box.top * detection.scaleY).roundToInt(),
                right = (box.right * detection.scaleX).roundToInt(),
                bottom = (box.bottom * detection.scaleY).roundToInt(),
                score = box.score,
            )
        }
    }

    private class Detection(val boxes: List<DbPostProcessor.TextBox>, val scaleX: Float, val scaleY: Float)

    private fun detect(image: RgbImage): Detection {
        val scale = minOf(1f, DET_MAX_SIDE / maxOf(image.width, image.height))
        val width = multipleOf32(image.width * scale)
        val height = multipleOf32(image.height * scale)
        val resized = ImageOps.resize(image, width, height)
        val input = ImageOps.toTensor(resized, DET_MEAN, DET_STD)
        val probabilities = run(detector, input, height, width)
        val boxes = postProcessor.boxes(probabilities, width, height)
        return Detection(boxes, image.width.toFloat() / width, image.height.toFloat() / height)
    }

    private fun recognizeBox(image: RgbImage, detection: Detection, box: DbPostProcessor.TextBox): OcrLine? {
        val left = (box.left * detection.scaleX).roundToInt()
        val top = (box.top * detection.scaleY).roundToInt()
        val right = (box.right * detection.scaleX).roundToInt()
        val bottom = (box.bottom * detection.scaleY).roundToInt()
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

    private companion object {
        const val DICTIONARY_KEY = "character"
        const val DEFAULT_THREADS = 2
        const val CHANNELS = 3L
        const val MULTIPLE = 32
        const val DET_MAX_SIDE = 960f
        val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
        const val REC_HEIGHT = 48
        const val REC_MIN_WIDTH = 16
        const val REC_MAX_WIDTH = 2048
        val REC_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        val REC_STD = floatArrayOf(0.5f, 0.5f, 0.5f)
    }
}
