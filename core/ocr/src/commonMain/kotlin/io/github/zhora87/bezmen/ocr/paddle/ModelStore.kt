package io.github.zhora87.bezmen.ocr.paddle

import io.github.zhora87.bezmen.domain.Script

/** Where model files come from: APK assets on Android, a directory on the JVM. */
interface ModelStore {
    fun read(name: String): ByteArray
}

/** File names as listed in models.lock: PP-OCRv5 mobile with HardSwish rewritten (tools/ml/rewrite_hardswish.py). */
object Models {
    const val DETECTION = "ch_PP-OCRv5_det_mobile-nohs.onnx"
    const val RECOGNITION_CYRILLIC = "cyrillic_PP-OCRv5_rec_mobile-nohs.onnx"
    const val RECOGNITION_LATIN = "latin_PP-OCRv5_rec_mobile-nohs.onnx"

    fun recognitionFor(script: Script): String = when (script) {
        Script.CYRILLIC -> RECOGNITION_CYRILLIC
        Script.LATIN -> RECOGNITION_LATIN
    }
}
