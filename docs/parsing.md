# Parsing price tags

`PriceTagParser` turns the OCR lines of one price tag into a price per unit. It is a pure function
over recognised text: no platform types, no I/O, identical output for identical input. That is what
lets the corpus of OCR dumps run on the JVM in seconds and guard against regressions in CI.

## Input

```kotlin
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) // normalised 0..1
data class OcrLine(val text: String, val box: Box, val confidence: Float)
```

Lines arrive in whatever order the engine produced them; the parser sorts by geometry itself.

## Output

```kotlin
data class Field<T>(val value: T, val confidence: Float, val alternatives: List<T>, val sourceLines: List<Int>)

data class ParsedTag(
    val price: Field<Money>?,
    val oldPrice: Field<Money>?,
    val quantity: Field<Quantity>?,
    val printedUnitPrice: Field<UnitPrice>?,
    val name: Field<String>?,
    val isWeighted: Boolean,           // price is per kg or per litre, no package quantity
)

sealed interface ParseResult {
    data class Success(val tag: ParsedTag, val unitPrice: UnitPrice, val overall: Float) : ParseResult
    data class NeedsInput(val tag: ParsedTag, val missing: Set<FieldKind>) : ParseResult
    data object Nothing : ParseResult
}
```

An `overall` below `ParseResult.CONFIRM_THRESHOLD` (0.75) means the UI shows the result with a
"please check" mark and highlights the doubtful field.

## Algorithm

1. **Normalise text.** Whitespace, superscript kopecks (`249⁹⁰` → `249.90`), the pack's decimal
   separators to `.`, and look-alike characters inside numbers (`З00` → `300`) fixed only next to
   digits, so words are untouched.
2. **Tokenise.** Each line becomes numbers, words, currency symbols, percent signs, dates and dotted
   codes (`1.21.6`, `19:03`). Tokens keep character offsets and a proportional slice of the line box.
3. **Script folding for matching.** OCR emits Latin look-alikes inside Cyrillic words (`Kr`, `FPH`,
   `3НИЖKA`) and vice versa. Unit aliases, currency symbols and marker phrases are compared after
   folding both sides onto the pack's script. Displayed text is never folded.
4. **Quantity candidates.** A number followed by a unit alias, in one line or glued (`900мл`,
   `0.33 л`, `1.5кг`, `12 шт`). Multipacks multiply out: `6 x 200 г`, `2 шт по 90 г`. A unit right
   after the currency or a slash with no number means one of it (`грн/шт`; `грн/кг` means sold by
   weight). Percentages are never quantities, nor is anything below one gram or millilitre. When
   several candidates remain, the one closest to the price wins: package text in the background
   carries its own numbers.
5. **Printed unit price.** A marker phrase from the pack (`за 1 кг`, `per 100 g`, `1 kg =`) gives
   a reference quantity, taken from the phrase or the tokens right after it. The price belongs to
   the same line when present, otherwise to the nearest small-print line. A marker without a price
   marks weighted goods.
6. **Money candidates.** `249.90`; `249 90`; a two-digit box to the right of a large integer box,
   smaller and vertically inside it; `249 ₽`; and, only on the tallest lines, a bare integer. On
   packs with superscript kopecks a bare integer of four or more digits is the price glued to its
   kopecks (`29480` is 294.80). Barcodes (8+ digits), dates, codes, sideways text and numbers
   followed by `%` are excluded.
7. **Current and old price.** Labels apply to a price when they share a line or overlap it
   vertically. A price on an old-price label is the old price; a price on a loyalty label is an
   alternative, not the current price. Without labels, two prices whose heights differ by more than
   30%, or any two prices when a discount marker (or a lone `-50%` / `29%`) is present, split into
   current (taller) and old (smaller). Two prices of similar height without a marker take the
   lower amount as current with reduced confidence.
8. **Weighted goods.** A unit-price marker for 1 kg or 1 l and no package quantity: the price is per
   that reference and `isWeighted` is true.
9. **Name.** The topmost wordy line in the upper part of the tag, joined with the wordy lines
   directly beneath it in similar type. Marker and label lines are excluded; a quantity inside the
   name line is cut out.
10. **Cross-check and derivation.** With price, quantity and a printed unit price, a mismatch above
    3% lowers `overall`. With price and printed unit price but no quantity, the quantity is derived
    (price ÷ unit price) at reduced confidence. With no price of its own, the marker's price is used
    and the goods are weighted.
11. **Result.** Price and quantity present → `Success` with the computed unit price; one of them
    missing → `NeedsInput`; nothing usable → `Nothing`.

## Units

| dimension | base | units and factors |
|---|---|---|
| MASS | g | mg 0.001; g 1; kg 1000; lb 453.59 |
| VOLUME | ml | ml 1; cl 10; l 1000 |
| COUNT | piece | piece 1; pair 2; dozen 12 |

Display defaults: 100 g, 100 ml, 1 piece; settings switch to 1 kg and 1 l. Quantities of different
dimensions are not compared; such items are marked as not comparable in a comparison list.

## Locale pack format

```json
{
  "id": "uk",
  "version": 1,
  "script": "CYRILLIC",
  "ocrModel": "cyrillic",
  "currency": { "code": "UAH", "symbols": ["₴", "грн", "грн."], "decimalSeparators": [",", "."] },
  "units": {
    "g":  ["г", "гр", "гр.", "g"],
    "kg": ["кг", "кг.", "kg"],
    "ml": ["мл", "мл.", "ml"],
    "l":  ["л", "л.", "l"],
    "pc": ["шт", "шт.", "штук", "набір"]
  },
  "multipack": ["x", "х", "×", "*", "по"],
  "unitPriceMarkers": ["за 1 кг", "за 100 г", "ціна за", "за 1 л", "за 100 мл"],
  "discountMarkers": ["акція", "знижка"],
  "oldPriceMarkers": ["стара ціна"],
  "loyaltyMarkers": ["з карткою", "при скануванні"],
  "charFixes": { "O": "0", "О": "0", "З": "3", "l": "1", "І": "1" },
  "priceHints": { "superscriptCents": true }
}
```

| field | meaning |
|---|---|
| `script`, `ocrModel` | writing system and recognition model group (`cyrillic`, `latin`) |
| `currency` | ISO code, every symbol or word OCR may produce for it, accepted decimal separators |
| `units` | `MeasureUnit` code (`mg g kg lb ml cl l pc pair dozen`) to spellings found on tags, including store-font misreads |
| `multipack` | tokens between two numbers that multiply them |
| `unitPriceMarkers` | phrases introducing a printed price per unit |
| `discountMarkers`, `oldPriceMarkers`, `loyaltyMarkers` | promotion, crossed-out price and loyalty-card or app price labels |
| `weightedMarkers` | phrases meaning the goods are sold by weight ("ваговий") |
| `codeMarkers` | labels of article codes; nothing on such a line is a price or a quantity |
| `charFixes` | single-character look-alikes fixed inside numbers |

Schema: `locale-packs/schema.json`. Every shipped pack is loaded and validated by a test.

## Corpus

```
corpus/ocr/<engine>/<id>.json    OcrLine dumps from a specific engine
corpus/expected/<id>.json        expected price, oldPrice, quantity, isWeighted, note
corpus/images/                   photos; not versioned
```

`expected/<id>.json` selects the pack and may carry `"pending": true` while it is being annotated;
pending cases are skipped by the accuracy gate.

```json
{ "pack": "uk", "price": { "minor": 9990, "currency": "UAH" }, "oldPrice": null,
  "quantity": { "value": 180.0, "unit": "GRAM" }, "isWeighted": false, "note": "superscript kopecks" }
```

- `ocr/synthetic/` holds hand-written dumps mirroring the unit tests; CI requires 100% on them.
- `ocr/rapidocr/` holds dumps produced by `tools/ml/ocr_dump.py` (RapidOCR, PP-OCRv5 mobile) from
  photos cropped to the tag by `tools/corpus/autocrop.py`; CI enforces a regression floor that is
  raised as the corpus grows.
- `./gradlew :tools:parser-cli:run --args="corpus --engine rapidocr --verbose"` prints a per-pack
  table of price, quantity and overall accuracy.

## Known hard cases

- Kopecks as superscript glued to the integer part, or as a separate small box overlapping the
  large digits.
- Latin look-alikes inside Cyrillic units and currency (`Kr`, `FPH`), and store fonts that make
  OCR read one Cyrillic letter as another (`180р` for `180 г`).
- Two prices for one package: with and without a loyalty card or a store app.
- Quantities inside the name line: `Молоко 2,5% 900 мл`.
- Pieces versus multipacks: `10 шт` against `4 × 115 г`.
- Units wrapped to the next line; e-ink tags with low contrast; small print that OCR misses
  entirely, in which case the result is `NeedsInput` and the user types the quantity.

## Out of scope

Net weight without brine, concentrates and dry mixes, share of the main ingredient, comparing grams
with millilitres through density. These need product data (a barcode and a database), not the tag.
