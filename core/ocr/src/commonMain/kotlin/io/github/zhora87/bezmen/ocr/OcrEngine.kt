package io.github.zhora87.bezmen.ocr

import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Script

/**
 * Raw image handed to an engine. Kept free of platform types so commonMain never depends on
 * android.graphics.Bitmap; the Android side wraps CameraX frames into this.
 */
class OcrImage(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val format: Format,
    val bytes: ByteArray,
) {
    enum class Format { RGBA_8888, NV21 }
}

/**
 * Contract every OCR backend implements (docs/architecture.md). Implementations
 * run entirely on device: PaddleOCR via ONNX Runtime in every flavor, ML Kit optionally in `play`
 * for Latin scripts. Nothing here may touch the network.
 */
interface OcrEngine {
    val id: String
    val supportedScripts: Set<Script>

    suspend fun recognize(image: OcrImage): List<OcrLine>

    fun close()
}
