package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.UnitPrice

/** A recognised value with the parser's confidence in it and the OCR lines it came from. */
data class Field<T>(
    val value: T,
    val confidence: Float,
    val alternatives: List<T> = emptyList(),
    val sourceLines: List<Int> = emptyList(),
)

enum class FieldKind { PRICE, QUANTITY }

data class ParsedTag(
    val price: Field<Money>? = null,
    val oldPrice: Field<Money>? = null,
    val quantity: Field<Quantity>? = null,
    val printedUnitPrice: Field<UnitPrice>? = null,
    val name: Field<String>? = null,
    /** The price is already per kg or per litre and the tag carries no package quantity. */
    val isWeighted: Boolean = false,
)

sealed interface ParseResult {
    /** Price and quantity found; [overall] below [CONFIRM_THRESHOLD] means the UI should ask the user to check. */
    data class Success(val tag: ParsedTag, val unitPrice: UnitPrice, val overall: Float) : ParseResult

    /** Something was found but not enough to compute a unit price. */
    data class NeedsInput(val tag: ParsedTag, val missing: Set<FieldKind>) : ParseResult

    /** No price and no quantity in these lines. */
    data object Nothing : ParseResult

    companion object {
        const val CONFIRM_THRESHOLD = 0.75f
    }
}
