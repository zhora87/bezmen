package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.LocalePack

/**
 * Step 1 of the parser (docs/parsing.md): whitespace, superscript kopecks, decimal separators and
 * OCR look-alike characters inside numbers. Words are never touched.
 */
internal class TextNormalizer(private val pack: LocalePack) {
    private val fixable: Map<Char, Char> = pack.charFixes.entries
        .filter { (from, to) -> from.length == 1 && to.length == 1 }
        .associate { (from, to) -> from[0] to to[0] }

    fun normalize(raw: String): String {
        var text = raw.replace(' ', ' ').replace(WHITESPACE, " ").trim()
        text = SUPERSCRIPT_CENTS.replace(text) { m ->
            m.groupValues[1] + "." + m.groupValues[2].map { SUPERSCRIPTS.getValue(it) }.joinToString("")
        }
        text = buildString(text.length) { text.forEach { append(SUPERSCRIPTS[it] ?: it) } }
        pack.currency.decimalSeparators.filter { it != "." }.forEach { text = text.replace(it, ".") }
        return text.split(' ').joinToString(" ") { fixToken(it) }
    }

    /** Replaces look-alikes only next to a digit, so "З00" becomes "300" while "Зефир" stays. */
    private fun fixToken(token: String): String {
        if (token.none { it.isDigit() }) return token
        val out = StringBuilder(token.length)
        token.forEachIndexed { i, c ->
            val fix = fixable[c]
            val nextToDigit = (i > 0 && out[i - 1].isDigit()) || (i + 1 < token.length && token[i + 1].isDigit())
            out.append(if (fix != null && nextToDigit) fix else c)
        }
        return out.toString()
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val SUPERSCRIPTS = mapOf(
            '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
            '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9',
        )
        val SUPERSCRIPT_CENTS = Regex("(\\d+)([⁰¹²³⁴⁵⁶⁷⁸⁹]{2})")
    }
}
