package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.ScriptFolding

/** Normalised text and tokens of one tag, shared by all detectors. */
internal class TagContext(
    val lines: List<OcrLine>,
    val normalized: List<String>,
    val tokens: List<List<Token>>,
    pack: LocalePack,
) {
    /** Lower-cased and script-folded copy of every line, for marker matching only. */
    val lower: List<String> = normalized.map { ScriptFolding.fold(it.lowercase(), pack.script) }

    /** Text printed sideways (codes, barcodes): tall narrow boxes with several characters. */
    val vertical: List<Boolean> = lines.map {
        it.box.height > it.box.width * VERTICAL_RATIO && it.text.length >= VERTICAL_MIN_CHARS
    }
    private val maxHeight: Float = lines.indices
        .filterNot { vertical[it] }
        .maxOfOrNull { lines[it].box.height }
        ?.coerceAtLeast(MIN_HEIGHT) ?: MIN_HEIGHT

    fun relHeight(line: Int): Float = lines[line].box.height / maxHeight

    fun confidence(line: Int): Float = lines[line].confidence

    fun tokensOf(line: Int, excluding: Set<TokenId>): List<Token> =
        if (vertical[line]) emptyList() else tokens[line].filter { it.id !in excluding }

    companion object {
        private const val MIN_HEIGHT = 1e-6f
        private const val VERTICAL_RATIO = 2f
        private const val VERTICAL_MIN_CHARS = 4

        fun build(
            lines: List<OcrLine>,
            normalizer: TextNormalizer,
            tokenizer: Tokenizer,
            pack: LocalePack,
        ): TagContext {
            val normalized = lines.map { normalizer.normalize(it.text) }
            val tokens = lines.mapIndexed { i, line -> tokenizer.tokenize(i, normalized[i], line.box) }
            return TagContext(lines, normalized, tokens, pack)
        }
    }
}
