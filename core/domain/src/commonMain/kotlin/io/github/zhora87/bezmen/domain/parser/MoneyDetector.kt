package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.Money

/**
 * Step 5: money candidates. Handles "249.90", "249 90", a "90" box glued to the right of a big "249",
 * "249 ₽" and, only on the tallest lines, a bare integer. Ignores barcodes, dates and percentages.
 */
internal class MoneyDetector(private val pack: LocalePack) {
    private class Match(val tokens: List<Token>, val money: Money, val baseConfidence: Float, val currency: Boolean)

    fun detectAll(ctx: TagContext, consumed: Set<TokenId>): List<MoneyCandidate> {
        val used = consumed.toMutableSet()
        val out = mutableListOf<MoneyCandidate>()
        out += mergeSplitCents(ctx, used)
        for (line in ctx.lines.indices) {
            val allowBare = ctx.relHeight(line) >= BARE_MIN_REL_HEIGHT
            out += inLine(ctx, ctx.tokensOf(line, used), allowBare)
        }
        return out
    }

    /** Candidates among the given tokens of one line, in reading order. */
    fun inLine(ctx: TagContext, tokens: List<Token>, allowBare: Boolean): List<MoneyCandidate> {
        val out = mutableListOf<MoneyCandidate>()
        val bareOk = allowBare && tokens.none { it.kind == TokenKind.WORD }
        var i = 0
        while (i < tokens.size) {
            val match = matchAt(tokens, i, bareOk)
            if (match == null) {
                i++
            } else {
                out += candidate(ctx, match)
                i += match.tokens.size
            }
        }
        return out
    }

    private fun matchAt(tokens: List<Token>, i: Int, allowBare: Boolean): Match? {
        val t = tokens[i]
        val next = tokens.getOrNull(i + 1)
        if (!isPriceLike(t, next)) return null
        val currencyNear = tokens.getOrNull(i - 1)?.kind == TokenKind.CURRENCY || next?.kind == TokenKind.CURRENCY
        return when {
            t.decimals == 2 -> Match(listOf(t), decimal(t), DECIMAL_CONFIDENCE, currencyNear)
            !t.isInteger || t.digitCount > MAX_MAJOR_DIGITS -> null
            next != null && isCents(next) -> {
                val currency = currencyNear || tokens.getOrNull(i + 2)?.kind == TokenKind.CURRENCY
                Match(listOf(t, next), Money.of(t.text.toLong(), next.text.toInt(), code), SPLIT_CONFIDENCE, currency)
            }
            currencyNear -> Match(listOf(t), whole(t), WHOLE_CONFIDENCE, true)
            allowBare -> Match(listOf(t), whole(t), BARE_CONFIDENCE, false)
            else -> null
        }
    }

    /** A line holding only a 1..6 digit integer next to a line holding only two digits, smaller and to the right. */
    private fun mergeSplitCents(ctx: TagContext, used: MutableSet<TokenId>): List<MoneyCandidate> {
        val out = mutableListOf<MoneyCandidate>()
        val majors = ctx.lines.indices.mapNotNull { line ->
            soleNumber(ctx, line, used)?.takeIf { it.isInteger && it.digitCount <= MAX_MAJOR_DIGITS }
        }
        val cents = ctx.lines.indices.mapNotNull { line -> soleNumber(ctx, line, used)?.takeIf { isCents(it) } }
        for (major in majors) {
            val majorBox = ctx.lines[major.lineIndex].box
            val cent = cents.firstOrNull {
                it.lineIndex != major.lineIndex && looksLikeCents(majorBox, ctx.lines[it.lineIndex].box)
            } ?: continue
            out += mergedCandidate(ctx, major, cent)
            used += ctx.tokens[major.lineIndex].map { it.id }
            used += ctx.tokens[cent.lineIndex].map { it.id }
        }
        return out
    }

    private fun mergedCandidate(ctx: TagContext, major: Token, cent: Token): MoneyCandidate {
        val lines = listOf(major.lineIndex, cent.lineIndex)
        val currency = lines.any { line -> ctx.tokens[line].any { it.kind == TokenKind.CURRENCY } }
        val bonus = if (currency) CURRENCY_BONUS else 0f
        val confidence = (SPLIT_LINE_CONFIDENCE + bonus) * lines.map(ctx::confidence).average().toFloat()
        return MoneyCandidate(
            money = Money.of(major.text.toLong(), cent.text.toInt(), code),
            confidence = confidence,
            lines = lines,
            box = ctx.lines[major.lineIndex].box.union(ctx.lines[cent.lineIndex].box),
            hasCurrency = currency,
            tokens = listOf(major.id, cent.id),
        )
    }

    /** The single NUMBER token of a line whose other tokens are at most currency symbols. */
    private fun soleNumber(ctx: TagContext, line: Int, used: Set<TokenId>): Token? {
        val tokens = ctx.tokensOf(line, used)
        val numbers = tokens.filter { it.kind == TokenKind.NUMBER }
        val others = tokens.any { it.kind != TokenKind.NUMBER && it.kind != TokenKind.CURRENCY }
        return if (numbers.size == 1 && !others) numbers.single() else null
    }

    private fun looksLikeCents(major: Box, cents: Box): Boolean {
        val h = major.height
        return cents.left >= major.left + major.width * CENTS_MIN_START &&
            cents.left <= major.right + h &&
            cents.top >= major.top - h * CENTS_VERTICAL_SLACK &&
            cents.bottom <= major.bottom + h * CENTS_VERTICAL_SLACK &&
            cents.height <= h * CENTS_MAX_HEIGHT_RATIO
    }

    private fun isPriceLike(t: Token, next: Token?): Boolean =
        t.kind == TokenKind.NUMBER && t.digitCount < BARCODE_DIGITS && next?.kind != TokenKind.PERCENT

    private fun isCents(t: Token): Boolean = t.isInteger && t.text.length == 2

    private fun decimal(t: Token): Money {
        val (major, fraction) = t.text.split('.')
        return Money.of(major.toLong(), fraction.toInt(), code)
    }

    private fun whole(t: Token): Money = Money.of(t.text.toLong(), 0, code)

    private fun candidate(ctx: TagContext, match: Match): MoneyCandidate {
        val line = match.tokens.first().lineIndex
        val box = match.tokens.map { it.box }.reduce { a, b -> a.union(b) }
        val bonus = if (match.currency) CURRENCY_BONUS else 0f
        val confidence = (match.baseConfidence + bonus) * ctx.confidence(line)
        return MoneyCandidate(match.money, confidence, listOf(line), box, match.currency, match.tokens.map { it.id })
    }

    private val code: String get() = pack.currency.code

    private companion object {
        const val DECIMAL_CONFIDENCE = 0.9f
        const val SPLIT_CONFIDENCE = 0.8f
        const val SPLIT_LINE_CONFIDENCE = 0.85f
        const val WHOLE_CONFIDENCE = 0.7f
        const val BARE_CONFIDENCE = 0.5f
        const val CURRENCY_BONUS = 0.05f
        const val BARE_MIN_REL_HEIGHT = 0.8f
        const val MAX_MAJOR_DIGITS = 6
        const val BARCODE_DIGITS = 8
        const val CENTS_MIN_START = 0.5f
        const val CENTS_VERTICAL_SLACK = 0.15f
        const val CENTS_MAX_HEIGHT_RATIO = 0.75f
    }
}
