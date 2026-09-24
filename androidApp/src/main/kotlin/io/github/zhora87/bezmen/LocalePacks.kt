package io.github.zhora87.bezmen

import android.content.Context
import android.content.res.AssetManager
import android.telephony.TelephonyManager
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.LocalePackChoice
import java.util.Locale

/** Locale packs shipped in assets/locale-packs/. */
object LocalePacks {
    /** Country of the mobile network, then of the SIM, then of the locale; no permission is needed for these. */
    fun defaultId(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val locale = Locale.getDefault()
        val countries = listOf(telephony?.networkCountryIso, telephony?.simCountryIso, locale.country)
        return LocalePackChoice.idFor(countries, locale.language)
    }

    fun load(assets: AssetManager, id: String): LocalePack {
        val text = assets.open("locale-packs/$id.json").use { it.readBytes().decodeToString() }
        return LocalePack.fromJson(text).also { pack ->
            val problems = pack.validate()
            check(problems.isEmpty()) { "locale pack '$id' is invalid: $problems" }
        }
    }
}
