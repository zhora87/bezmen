package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.LocalePack

internal enum class TokenKind { NUMBER, WORD, CURRENCY, PERCENT, DATE, CODE }

/** Stable identity of a token across detectors: line index in the input plus position in the line. */
internal data class TokenId(val line: Int, val index: Int)

internal data class Token(
    val text: String,
    val kind: TokenKind,
    val lineIndex: Int,
    val index: Int,
    /** Character offsets in the normalised line text, for marker matching. */
    val start: Int,
    val end: Int,
    val box: Box,
) {
    val id: TokenId get() = TokenId(lineIndex, index)
    val number: Double? get() = if (kind == TokenKind.NUMBER) text.toDoubleOrNull() else null
    val isInteger: Boolean get() = kind == TokenKind.NUMBER && '.' !in text
    val decimals: Int get() = if ('.' in text) text.length - text.indexOf('.') - 1 else 0
    val digitCount: Int get() = text.count { it.isDigit() }
}

/** Step 2: splits a normalised line into numbers, words, currency, percent and date tokens with boxes. */
internal class Tokenizer(private val pack: LocalePack) {
    fun tokenize(lineIndex: Int, text: String, lineBox: Box): List<Token> =
        PATTERN.findAll(text).mapIndexed { index, m ->
            val kind = when {
                m.groups[GROUP_DATE] != null -> TokenKind.DATE
                m.groups[GROUP_CODE] != null -> TokenKind.CODE
                m.groups[GROUP_NUMBER] != null -> TokenKind.NUMBER
                m.groups[GROUP_PERCENT] != null -> TokenKind.PERCENT
                pack.isCurrency(m.value) -> TokenKind.CURRENCY
                else -> TokenKind.WORD
            }
            Token(
                text = m.value,
                kind = kind,
                lineIndex = lineIndex,
                index = index,
                start = m.range.first,
                end = m.range.last + 1,
                box = lineBox.slice(m.range.first, m.range.last + 1, text.length),
            )
        }.toList()

    private companion object {
        // date | dotted code or time | number | percent | separator punctuation | word
        val PATTERN = Regex(
            "(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})" + // date
                "|(\\d+(?:[.:]\\d+){2,}|\\d{1,2}:\\d{2})" + // dotted code or time
                "|(\\d+(?:\\.\\d+)?)" + // number
                "|(%)|([/=()])|([^\\s\\d%/=()]+)", // percent, separators (brackets too: "г(Польща)"), word
        )
        const val GROUP_DATE = 1
        const val GROUP_CODE = 2
        const val GROUP_NUMBER = 3
        const val GROUP_PERCENT = 4
    }
}
