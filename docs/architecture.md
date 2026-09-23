# Architecture

Bezmen reads a shelf price tag with the phone camera and turns it into a price per 100 g,
100 ml or piece, entirely on the device. This document describes how the pieces fit together.

## Principles

- **No network.** The app does not declare the `INTERNET` permission. A Gradle task verifies the
  merged manifest of every build variant and fails the build if the permission appears.
- **Open models.** OCR runs on PaddleOCR PP-OCRv5 mobile models (Apache-2.0) through ONNX Runtime.
  Every model ships with its licence and checksum.
- **Universal core, regional locale packs.** Units, currency, marker phrases and OCR quirks that
  vary by country or store chain live in JSON packs, not in code. Adding a country is a data change.

## Pipeline

```
CameraX ImageAnalysis
   │
   ▼
crop to the viewfinder, fix orientation
   │
   ▼
OcrEngine.recognize(image) ──► List<OcrLine>(text, box, confidence)
   │        ├─ PaddleOnnxOcrEngine   (all flavors; detection + recognition via ONNX Runtime)
   │        └─ MlKitOcrEngine        (play flavor only; Latin scripts only; optional)
   ▼
PriceTagParser.parse(lines, localePack) ──► ParseResult
   │
   ▼
stabiliser: a result counts once it repeats across frames
   │
   ▼
UnitPriceCalculator ──► price per 100 g / 100 ml / piece
   │
   ▼
UI: result card with editable fields, comparison list
   │
   ▼
local storage: comparison sessions (Room), settings (DataStore)
```

Everything above the UI is platform-independent Kotlin and is tested on the JVM. Camera, OCR
engines and storage drivers are Android code.

## Modules

```
androidApp/      Android application: product flavors, manifest, CameraX, dependency wiring
ui/              Compose Multiplatform UI (Kotlin Multiplatform library)
core/domain/     pure Kotlin: models, PriceTagParser, units, unit price, locale packs, comparison
core/ocr/        OcrEngine contract and implementations
core/data/       Room and DataStore (added with the app screens)
locale-packs/    JSON packs and their schema
corpus/          OCR dumps and expected values used as a regression corpus
tools/parser-cli JVM runner: parser accuracy over the corpus
tools/ml/        Python: OCR dumps for the corpus, model conversion
tools/corpus/    Python: photo intake
```

Dependencies point downwards only: `androidApp` → `ui`, `core/*`; `ui` → `core/domain`;
`core/ocr` → `core/domain`; `core/domain` depends on nothing. The application is a plain Android
module so that product flavors and flavor-scoped dependencies stay standard; shared logic and UI
are Kotlin Multiplatform libraries.

### core/domain

- `Money` (minor units), `Quantity` and `MeasureUnit` with conversion to base units (g, ml, piece),
  `UnitPrice`, `References` (100 g, 1 kg, 100 ml, 1 l, piece).
- `Box`, `OcrLine`, `Script`: the engine-agnostic view of recognised text.
- `LocalePack`: model, JSON loader, validation, script-aware token matching.
- `PriceTagParser` and its detectors, see [parsing.md](parsing.md).
- `Comparison`: ranking of scanned items by unit price with deltas.

### core/ocr

```kotlin
interface OcrEngine {
    val id: String
    val supportedScripts: Set<Script>
    suspend fun recognize(image: OcrImage): List<OcrLine>
    fun close()
}
```

`OcrImage` wraps raw bytes and dimensions so that common code never depends on Android bitmaps.
An `OcrEngineSelector` picks the engine from the locale pack, the flavor and user settings.

## OCR engines and flavors

ML Kit Text Recognition v2 does not support Cyrillic on device (only Latin, Chinese, Devanagari,
Japanese and Korean scripts). PaddleOCR is therefore the primary engine everywhere.

| flavor | OCR | Google dependencies | channel |
|---|---|---|---|
| `foss` | PaddleOCR via ONNX Runtime | none | F-Droid, GitHub Releases |
| `play` | PaddleOCR via ONNX Runtime; optional ML Kit for Latin-script packs | ML Kit | Google Play, GitHub Releases |

The flavor difference is confined to one optional engine implementation wired through a
flavor-scoped dependency and `BuildConfig.DISTRIBUTION`. If benchmarks show no advantage for
ML Kit on Latin scripts, it is dropped and the flavors differ only in store metadata.

## Models and licences

| component | purpose | licence |
|---|---|---|
| PP-OCRv5 mobile detection | text line detection | Apache-2.0 |
| `cyrillic_PP-OCRv5_mobile_rec` | recognition, Cyrillic scripts | Apache-2.0 |
| `latin_PP-OCRv5_mobile_rec` | recognition, Latin scripts | Apache-2.0 |
| ONNX Runtime (Android) | inference | MIT |
| ML Kit Text Recognition v2 | Latin recognition, `play` flavor only | proprietary |

Model files are not committed. A Gradle task downloads them by pinned URL, verifies the sha256
recorded in `models.lock` and places them in assets. Desktop tooling (`tools/ml/ocr_dump.py`) uses
the same ONNX files through RapidOCR, so corpus dumps match what the app sees.

## Locale packs

A pack declares the script and recognition model, currency symbols and decimal separators, unit
spellings, phrases that introduce a printed price per unit, discount / old-price / loyalty-price
markers, and OCR look-alike fixes. Packs are validated against `locale-packs/schema.json` and by
unit tests. Format and semantics: [parsing.md](parsing.md#locale-pack-format).

## Permissions

`CAMERA` only. Photo import uses the system Photo Picker and needs no storage permission.
`INTERNET` is removed from the merged manifest with `tools:node="remove"` and its absence is
verified on every variant (`verifyNoInternet<Variant>`, part of `check`).

## Updating models and packs

Not in the first releases: packs and models change with app updates. If out-of-band updates are
ever needed, they will use a statically hosted manifest signed with a project key and checked
before installation, in a release that adds the network permission explicitly and says so.

## Build, checks, CI

- Gradle with a version catalog. `./gradlew build` assembles both flavors, runs unit tests and
  lint and verifies the manifests. `./gradlew detekt` runs static analysis and formatting rules.
- `tools/parser-cli` measures parser accuracy on the corpus: `--engine synthetic` (hand-written
  dumps, must stay at 100%) and `--engine rapidocr` (real OCR dumps, regression floor that is
  raised as the corpus grows). Both run in CI.
- Documentation and metadata changes do not trigger CI.
- Releases: a tag builds release variants, signing happens outside CI, APKs and checksums are
  attached to GitHub Releases. `fastlane/metadata` carries store listings for F-Droid and Play.

## Decisions

- Kotlin Multiplatform with Compose Multiplatform: domain and UI are platform-neutral; Android is
  the only target for now, iOS stays possible without rewriting the core.
- Two flavors, `foss` without any Google dependency.
- AGPL-3.0 for the code.
- PaddleOCR through ONNX Runtime as the primary engine; ML Kit optional and Latin-only.
- No network permission in any variant.
- No shelf-level price tag detector in the first version: the user frames one tag in the
  viewfinder. Whole-shelf mode is a later addition.
- Interface language: Russian first, English as time permits. Recognition packs are independent
  of the interface language.
- Corpus photos are not versioned; the repository keeps OCR dumps and expected values, which is
  all the parser and CI need.

## Not in scope yet

Shelf mode with a detector, barcode lookup and product data (net weight, concentrates, ingredient
share), price history per store, out-of-band pack updates, iOS.
