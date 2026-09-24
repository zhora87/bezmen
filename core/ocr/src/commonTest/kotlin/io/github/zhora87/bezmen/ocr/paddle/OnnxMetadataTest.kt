package io.github.zhora87.bezmen.ocr.paddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OnnxMetadataTest {
    private fun varint(value: Long): ByteArray {
        val out = mutableListOf<Byte>()
        var v = value
        while (v >= 0x80) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }

    private fun field(number: Int, payload: ByteArray): ByteArray =
        varint(((number shl 3) or 2).toLong()) + varint(payload.size.toLong()) + payload

    private fun entry(key: String, value: String): ByteArray =
        field(14, field(1, key.encodeToByteArray()) + field(2, value.encodeToByteArray()))

    @Test
    fun `reads a metadata value with characters outside the basic plane`() {
        // ir_version (varint), a large graph to skip, then two metadata entries.
        val model = varint((1 shl 3).toLong()) + varint(8) +
            field(7, ByteArray(300) { 1 }) +
            entry("other", "x") +
            entry("character", "a\n𝑢\nb\n𝜓\nc\n")

        val value = OnnxMetadata.read(model, "character")

        assertEquals("a\n𝑢\nb\n𝜓\nc\n", value)
        assertEquals(5, CtcDecoder.fromMetadata(value!!).dictionarySize - 1)
    }

    @Test
    fun `missing key gives null`() {
        assertNull(OnnxMetadata.read(entry("other", "x"), "character"))
    }

    @Test
    fun `truncated input fails instead of returning garbage`() {
        val model = entry("character", "abc")

        assertFailsWith<IllegalArgumentException> { OnnxMetadata.read(model.copyOf(model.size - 2), "character") }
    }
}
