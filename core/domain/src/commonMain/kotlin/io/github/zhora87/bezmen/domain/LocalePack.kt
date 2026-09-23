package io.github.zhora87.bezmen.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Currency as it appears on tags: ISO code plus every symbol or word OCR may produce for it. */
@Serializable
data class CurrencySpec(
    val code: String,
    val symbols: List<String>,
    val decimalSeparators: List<String> = listOf(",", "."),
)

@Serializable
data class PriceHints(
    /** Kopecks printed small or superscript next to a large integer part ("249⁹⁰"). */
    val superscriptCents: Boolean = true,
)

/**
 * Everything that varies between countries and store chains, so the parser itself stays universal
 * (docs/parsing.md, Locale pack format). Packs are JSON files under `locale-packs/`.
 */
@Serializable
data class LocalePack(
    val id: String,
    val version: Int,
    val script: Script,
    val ocrModel: String,
    val currency: CurrencySpec,
    /** MeasureUnit code (g, kg, ml, l, pc, ...) to the spellings found on tags. */
    val units: Map<String, List<String>>,
    /** Tokens between two numbers that multiply them: "6 x 200 г", "2 шт по 90 г". */
    val multipack: List<String> = listOf("x", "×"),
    /** Phrases introducing a printed price per unit: "за 1 кг", "per 100 g". */
    val unitPriceMarkers: List<String> = emptyList(),
    /** Words that mean the tag is a promotion. */
    val discountMarkers: List<String> = emptyList(),
    /** Words that label the crossed-out previous price. */
    val oldPriceMarkers: List<String> = emptyList(),
    /** Words that label a loyalty-card price. */
    val loyaltyMarkers: List<String> = emptyList(),
    /** Phrases meaning the goods are sold by weight and the price is per the printed reference. */
    val weightedMarkers: List<String> = emptyList(),
    /** Labels of article codes and similar numbers that are never prices ("Код:"). */
    val codeMarkers: List<String> = emptyList(),
    /** OCR look-alike characters to fix inside numbers: O to 0, З to 3. */
    val charFixes: Map<String, String> = emptyMap(),
    val priceHints: PriceHints = PriceHints(),
) {
    private val aliasToUnit: Map<String, MeasureUnit> by lazy {
        buildMap {
            units.forEach { (code, aliases) ->
                val unit = MeasureUnit.fromCode(code) ?: return@forEach
                aliases.forEach { put(normalizeToken(it), unit) }
            }
        }
    }
    private val currencySymbols: Set<String> by lazy { currency.symbols.map(::normalizeToken).toSet() }
    private val multipackTokens: Set<String> by lazy { multipack.map(::normalizeToken).toSet() }

    /** Lower-case, trailing dots removed, look-alike letters folded onto the pack's script. */
    fun normalizeToken(text: String): String = normalizeAlias(ScriptFolding.fold(text, script))

    fun unitFor(token: String): MeasureUnit? = aliasToUnit[normalizeToken(token)]

    fun isCurrency(token: String): Boolean = normalizeToken(token) in currencySymbols

    fun isMultipack(token: String): Boolean = normalizeToken(token) in multipackTokens

    /** Problems that make the pack unusable. Empty list means valid. */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        if (!ID_PATTERN.matches(id)) problems += "id '$id' must look like 'ru' or 'en-generic'"
        if (version < 1) problems += "version must be >= 1"
        if (ocrModel.isBlank()) problems += "ocrModel is required"
        if (!CURRENCY_CODE.matches(currency.code)) {
            problems += "currency.code '${currency.code}' must be 3 upper-case letters"
        }
        if (currency.symbols.isEmpty()) problems += "currency.symbols must not be empty"
        currency.decimalSeparators
            .filterNot { it == "," || it == "." }
            .forEach { problems += "unsupported decimal separator '$it'" }
        if (units.isEmpty()) problems += "units must not be empty"
        problems += validateUnits()
        val markers = unitPriceMarkers + discountMarkers + oldPriceMarkers + loyaltyMarkers + weightedMarkers +
            codeMarkers + multipack
        markers
            .filter { it.isBlank() }
            .forEach { _ -> problems += "markers must not contain blank strings" }
        return problems
    }

    private fun validateUnits(): List<String> {
        val problems = mutableListOf<String>()
        val seen = mutableMapOf<String, String>()
        units.forEach { (code, aliases) ->
            if (MeasureUnit.fromCode(code) == null) problems += "unknown unit code '$code'"
            if (aliases.isEmpty()) problems += "unit '$code' has no aliases"
            aliases.forEach { alias ->
                val key = normalizeAlias(alias)
                if (key.isEmpty()) problems += "unit '$code' has a blank alias"
                val previous = seen.put(key, code)
                if (previous != null && previous != code) {
                    problems += "alias '$alias' is used by both '$previous' and '$code'"
                }
            }
        }
        return problems
    }

    companion object {
        private val ID_PATTERN = Regex("[a-z]{2}(-[a-z0-9]+)?")
        private val CURRENCY_CODE = Regex("[A-Z]{3}")
        val json: Json = Json { ignoreUnknownKeys = false }

        fun fromJson(text: String): LocalePack = json.decodeFromString(serializer(), text)

        fun normalizeAlias(text: String): String = text.trim().trimEnd('.').lowercase()
    }
}
