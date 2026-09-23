package io.github.zhora87.bezmen.ocr.paddle

/**
 * Greedy CTC decoding for PaddleOCR recognition output: class 0 is the blank, class i is the
 * (i-1)-th dictionary entry, an empty dictionary entry stands for a space. Repeated classes
 * collapse into one character; confidence is the mean probability of the kept characters.
 */
class CtcDecoder(dictionary: List<String>) {
    private val chars: List<String> = dictionary.map { if (it.isEmpty()) " " else it }

    class Decoded(val text: String, val confidence: Float)

    /** [probabilities] is a flat [steps x classes] softmax output. */
    fun decode(probabilities: FloatArray, steps: Int, classes: Int): Decoded {
        val text = StringBuilder()
        var sum = 0f
        var kept = 0
        var previous = -1
        for (t in 0 until steps) {
            val offset = t * classes
            var best = 0
            var bestProb = probabilities[offset]
            for (c in 1 until classes) {
                val p = probabilities[offset + c]
                if (p > bestProb) {
                    bestProb = p
                    best = c
                }
            }
            if (best != BLANK && best != previous) {
                chars.getOrNull(best - 1)?.let { text.append(it) }
                sum += bestProb
                kept++
            }
            previous = best
        }
        return Decoded(text.toString().trim(), if (kept == 0) 0f else sum / kept)
    }

    companion object {
        const val BLANK = 0

        /** Dictionary as stored in the ONNX metadata key `character`: one entry per line. */
        fun fromMetadata(character: String): CtcDecoder = CtcDecoder(character.split('\n'))
    }
}
