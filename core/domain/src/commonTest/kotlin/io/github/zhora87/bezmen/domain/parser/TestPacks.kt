package io.github.zhora87.bezmen.domain.parser

import io.github.zhora87.bezmen.domain.Box
import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.OcrLine

/** Compact packs for synthetic tests. The real packs live in locale-packs/ and are checked in jvmTest. */
internal object TestPacks {
    val ru: LocalePack = LocalePack.fromJson(
        """
        {
          "id": "ru", "version": 1, "script": "CYRILLIC", "ocrModel": "cyrillic",
          "currency": { "code": "RUB", "symbols": ["₽", "руб", "руб.", "р."] },
          "units": {
            "g": ["г", "гр", "гр.", "g"], "kg": ["кг", "кг."], "ml": ["мл", "мл."], "l": ["л", "л."],
            "pc": ["шт", "шт.", "штук"]
          },
          "multipack": ["x", "х", "×", "*", "по"],
          "unitPriceMarkers": ["цена за 1 кг", "цена за 100 г", "за 1 кг", "за 100 г", "за 1 л", "за 100 мл", "за кг", "за л", "цена за"],
          "discountMarkers": ["акция", "скидка", "спеццена"],
          "oldPriceMarkers": ["старая цена", "было"],
          "loyaltyMarkers": ["по карте", "с картой", "клубная цена"],
          "charFixes": { "O": "0", "О": "0", "о": "0", "З": "3", "з": "3", "l": "1", "I": "1", "І": "1" }
        }
        """.trimIndent(),
    )

    val uk: LocalePack = LocalePack.fromJson(
        """
        {
          "id": "uk", "version": 1, "script": "CYRILLIC", "ocrModel": "cyrillic",
          "currency": { "code": "UAH", "symbols": ["₴", "грн", "грн.", "ррн", "ррн.", "рпн"] },
          "units": {
            "g": ["г", "гр", "гр.", "р", "g"], "kg": ["кг", "μг", "кр", "к7"], "ml": ["мл"], "l": ["л", "n"],
            "pc": ["шт", "шт.", "набір", "наб-р", "на6-р", "лоток", "wт", "wу", "w7"]
          },
          "multipack": ["x", "х", "×", "по"],
          "unitPriceMarkers": ["ціна за 1 кг", "за 1 кг", "за 100 г", "за 1 л", "за 100 мл", "ціна за"],
          "discountMarkers": ["акція", "знижка", "зниж", "акційна ціна"],
          "oldPriceMarkers": ["стара ціна"],
          "loyaltyMarkers": ["з карткою", "за карткою", "скануванн", "додатка"],
          "weightedMarkers": ["ваговий", "вартість вказана за", "вказана за"],
          "codeMarkers": ["код:", "код "],
          "labelWords": ["ціна", "цина", "при", "атб"],
          "charFixes": { "O": "0", "О": "0", "З": "3", "І": "1" }
        }
        """.trimIndent(),
    )

    /** The uk pack with the rest of ATB's loyalty label as markers. */
    val ukWithAtbLabels: LocalePack = uk.copy(loyaltyMarkers = uk.loyaltyMarkers + listOf("атб", "касі"))

    val en: LocalePack = LocalePack.fromJson(
        """
        {
          "id": "en-generic", "version": 1, "script": "LATIN", "ocrModel": "latin",
          "currency": { "code": "EUR", "symbols": ["€", "EUR"], "decimalSeparators": [",", "."] },
          "units": { "g": ["g", "gr"], "kg": ["kg"], "ml": ["ml"], "cl": ["cl"], "l": ["l", "ltr"], "pc": ["pc", "pcs", "st"], "lb": ["lb", "lbs"] },
          "multipack": ["x", "×", "*"],
          "unitPriceMarkers": ["per 1 kg", "per kg", "per 100 g", "per 100g", "per 1 l", "per litre", "per 100 ml", "1 kg =", "1 l =", "/kg", "/l"],
          "discountMarkers": ["sale", "offer", "promo"],
          "oldPriceMarkers": ["was"],
          "loyaltyMarkers": ["with card", "club price"],
          "charFixes": { "O": "0", "o": "0", "l": "1", "I": "1" }
        }
        """.trimIndent(),
    )
}

/** A line spanning most of the tag width at the given vertical position. */
internal fun line(
    text: String,
    top: Float,
    height: Float,
    left: Float = 0.05f,
    right: Float = 0.95f,
    confidence: Float = 0.95f,
): OcrLine = OcrLine(text, Box(left, top, right, top + height), confidence)
