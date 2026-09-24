package io.github.zhora87.bezmen.domain

/**
 * Which recognition pack to use before the user picks one. Tags follow the country the phone is in,
 * not the interface language: a Russian-language phone in Ukraine reads Ukrainian tags.
 */
object LocalePackChoice {
    private const val GENERIC = "en-generic"
    private val PACK_BY_COUNTRY = mapOf("UA" to "uk", "RU" to "ru", "BY" to "ru", "KZ" to "ru", "KG" to "ru")
    private val PACK_BY_LANGUAGE = mapOf("uk" to "uk", "ru" to "ru")

    /**
     * [countries] in order of trust: mobile network, SIM, locale. Blank entries are skipped; the first
     * known country decides, an unknown one falls through to the [language] and then the generic pack.
     */
    fun idFor(countries: List<String?>, language: String): String {
        val country = countries.firstOrNull { !it.isNullOrBlank() }?.uppercase()
        return country?.let { PACK_BY_COUNTRY[it] } ?: PACK_BY_LANGUAGE[language.lowercase()] ?: GENERIC
    }
}
