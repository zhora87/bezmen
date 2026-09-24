package io.github.zhora87.bezmen.ocr.paddle

/**
 * Reads `metadata_props` straight from the bytes of an ONNX file (ModelProto field 14, entries of
 * StringStringEntryProto with key = 1 and value = 2), decoding UTF-8 in Kotlin.
 *
 * ONNX Runtime's Java getter for custom metadata goes through JNI string conversion, which mangles
 * characters outside the Basic Multilingual Plane and swallows the bytes next to them. PaddleOCR
 * dictionaries contain such characters, so the getter returned a dictionary with fewer entries and
 * every class after them decoded to the wrong character.
 */
object OnnxMetadata {
    private const val METADATA_PROPS = 14
    private const val KEY = 1
    private const val VALUE = 2
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH = 2
    private const val WIRE_FIXED32 = 5
    private const val FIXED64_BYTES = 8
    private const val FIXED32_BYTES = 4
    private const val TYPE_BITS = 3
    private const val TYPE_MASK = 7
    private const val PAYLOAD_MASK = 0x7F
    private const val CONTINUE_BIT = 0x80
    private const val MAX_SHIFT = 63
    private const val PAYLOAD_BITS = 7

    /** Value of the metadata entry [key], or null when the model has none. */
    fun read(model: ByteArray, key: String): String? {
        var result: String? = null
        Reader(model, 0, model.size).fields { number, reader ->
            if (number == METADATA_PROPS && result == null) {
                val (k, v) = entry(reader)
                if (k == key) result = v
            }
        }
        return result
    }

    private fun entry(reader: Reader): Pair<String?, String?> {
        var k: String? = null
        var v: String? = null
        reader.fields { number, field ->
            when (number) {
                KEY -> k = field.text()
                VALUE -> v = field.text()
            }
        }
        return k to v
    }

    /** Protobuf wire-format walker over [bytes] in [start, end). */
    private class Reader(private val bytes: ByteArray, private val start: Int, private val end: Int) {
        private var pos = start

        fun text(): String = bytes.decodeToString(start, end)

        /** Calls [onLengthDelimited] for every length-delimited field; other wire types are skipped. */
        fun fields(onLengthDelimited: (Int, Reader) -> Unit) {
            while (pos < end) {
                val tag = varint()
                val number = (tag ushr TYPE_BITS).toInt()
                when ((tag and TYPE_MASK.toLong()).toInt()) {
                    WIRE_VARINT -> varint()
                    WIRE_FIXED64 -> skip(FIXED64_BYTES)
                    WIRE_FIXED32 -> skip(FIXED32_BYTES)
                    WIRE_LENGTH -> {
                        val length = varint()
                        require(length in 0..(end - pos).toLong()) { "field $number runs past the end of the model" }
                        val from = pos
                        skip(length.toInt())
                        onLengthDelimited(number, Reader(bytes, from, pos))
                    }
                    else -> throw IllegalArgumentException("unsupported wire type in field $number")
                }
            }
        }

        private fun skip(count: Int) {
            require(count <= end - pos) { "truncated ONNX model" }
            pos += count
        }

        private fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < end && shift <= MAX_SHIFT) { "truncated ONNX model" }
                val b = bytes[pos++].toInt()
                result = result or ((b and PAYLOAD_MASK).toLong() shl shift)
                if (b and CONTINUE_BIT == 0) return result
                shift += PAYLOAD_BITS
            }
        }
    }
}
