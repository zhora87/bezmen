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
tools/ocr-dump   JVM runner: the shipped OCR engine over corpus photos, writes OcrLine dumps
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

### Execution provider

`PaddleOnnxOcrEngine` runs ONNX Runtime on its CPU execution provider with up to four intra-op
threads. XNNPACK and NNAPI are selectable for measurements only. Latency per corpus photo (tags
cropped as the viewfinder frames them, 2048 px on the long side, 79 photos):

| device | provider | threads | photo median | photo p90 | model load |
|--------|----------|---------|--------------|-----------|------------|
| 2025 flagship, 8 cores, Android 16 | CPU     | 2 | 519 ms  | 759 ms  | 246 ms  |
|                                    | CPU     | 4 | 450 ms  | 656 ms  | 212 ms  |
|                                    | XNNPACK | 4 | 764 ms  | 1158 ms | 228 ms  |
| 2019 budget, Snapdragon 439, 3 GB, Android 10 | CPU     | 2 | 3980 ms | 5654 ms | 1248 ms |
|                                               | CPU     | 4 | 2949 ms | 4294 ms | 1172 ms |
|                                               | XNNPACK | 4 | 6687 ms | 9455 ms | 1172 ms |

Accuracy is identical on both phones and on the desktop JVM. NNAPI, checked on a five-photo sample
and on a rendered tag, was no faster than CPU with four threads on either phone; it is also
deprecated since Android 15. A smaller detector input (`detectionMaxSide` 640 or 736 instead of
960) saves about a third of the time but loses the small print on two or three corpus tags, so 960
stays the default. The instrumented test
`OcrDeviceTest` in `androidApp` reproduces the table and rewrites its report after every
configuration.

### Reading the recognition dictionary

The character dictionary lives in the recognition model's ONNX metadata (`character`). The engine
reads it straight from the model bytes (`OnnxMetadata`) instead of through ONNX Runtime's Java
metadata getter: on the desktop JVM build that getter mangles the two dictionary entries outside the
Basic Multilingual Plane (`𝑢`, `𝜓`) and swallows the newlines next to them, which shifted every
class after them and dropped the space class. `CtcDecoder` also refuses a dictionary whose size
does not match the model's class count.

## Models and licences

| component | purpose | licence |
|---|---|---|
| PP-OCRv5 mobile detection | text line detection | Apache-2.0 |
| `cyrillic_PP-OCRv5_mobile_rec` | recognition, Cyrillic scripts | Apache-2.0 |
| `latin_PP-OCRv5_mobile_rec` | recognition, Latin scripts | Apache-2.0 |
| ONNX Runtime (Android) | inference | MIT |
| ML Kit Text Recognition v2 | Latin recognition, `play` flavor only | proprietary |

Model files are not committed. The `fetchModels` Gradle task downloads them by pinned URL,
verifies the sha256 recorded in `models.lock` and places them in assets; a build with the files
already present needs no network.

The shipped files are the RapidOCR ONNX exports with every `HardSwish` node rewritten to the
numerically identical `HardSigmoid` + `Mul` pair (`tools/ml/rewrite_hardswish.py`): the ONNX
Runtime native library in the Maven artifact computes `HardSwish` incorrectly, which made every
PP-OCRv5 model return an input-independent result on the JVM. The rewritten files are hosted as
static release assets of this repository with a provenance notice. Desktop tooling
(`tools/ml/ocr_dump.py`) runs the unmodified upstream files through RapidOCR; both produce the
same numbers.

The `core/ocr` JVM tests run the real models against rendered text and, when corpus photos are
present locally, against a real price tag, so the engine that ships in the app is exercised on
every CI run.

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
  dumps, must stay at 100%), `--engine paddle-onnx` (dumps of the shipped engine, at least 85% of
  cases fully correct) and `--engine rapidocr` (a second engine's dumps, lower regression floor).
  All three run in CI, together with `:core:domain:koverVerify` (80% line coverage).
- Documentation and metadata changes do not trigger CI.
- APKs are split per ABI (arm64-v8a, armeabi-v7a, x86_64): ONNX Runtime carries 22 to 38 MB of
  native code per architecture, the models add about 20 MB. A release APK for arm64-v8a is
  about 50 MB.
- Releases: a tag builds release variants, signing happens outside CI, APKs and checksums are
  attached to GitHub Releases. `fastlane/metadata` carries store listings for F-Droid and Play.
- `:androidApp:connectedFossDebugAndroidTest` runs `OcrDeviceTest` on an attached phone: the
  models load from the APK, execution providers are benchmarked and, when `corpus/images/` is
  present locally, the whole pipeline is scored over the corpus. It is not part of CI.

## Decisions

- Kotlin Multiplatform with Compose Multiplatform: domain and UI are platform-neutral; Android is
  the only target for now, iOS stays possible without rewriting the core.
- Two flavors, `foss` without any Google dependency.
- AGPL-3.0 for the code.
- PaddleOCR through ONNX Runtime as the primary engine; ML Kit optional and Latin-only.
- ONNX Runtime on the CPU execution provider with up to four threads; XNNPACK and NNAPI were
  measured on a device and rejected.
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
