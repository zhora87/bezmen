package io.github.zhora87.bezmen.ocr.paddle

import java.io.File

/** Models from a directory, e.g. the output of `:core:ocr:fetchModels`. */
class FileModelStore(private val directory: File) : ModelStore {
    override fun read(name: String): ByteArray {
        val file = directory.resolve(name)
        require(file.isFile) { "model not found: $file" }
        return file.readBytes()
    }
}
