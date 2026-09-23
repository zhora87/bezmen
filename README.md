# Bezmen

Offline price tag scanner for Android. Point the camera at a shelf tag, get the honest price per
100 g / 100 ml / piece, and compare several products side by side.

**Status:** early development. Nothing to install yet.

## Principles

- **Everything on device.** The app does not have the INTERNET permission at all.
- **No accounts, keys, developer servers, ads or analytics.** Ever.
- **Open source, open models.** AGPL-3.0 code, Apache-2.0 OCR models.
- **Universal core, regional locale packs.** Adding a country is a JSON file, not a fork.

## Two flavors

| flavor | OCR | Google dependencies | channel |
|---|---|---|---|
| `foss` | PaddleOCR via ONNX Runtime | none | F-Droid, GitHub Releases |
| `play` | PaddleOCR, optional ML Kit for Latin scripts | ML Kit | Google Play, GitHub Releases |

## Building

```
./gradlew build
```

JDK 21 and an Android SDK with platform 37. The first build downloads about 20 MB of OCR models
listed in `models.lock` and verifies their checksums. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Documentation

- [docs/architecture.md](docs/architecture.md): pipeline, modules, engines and flavors, models and
  licences, permissions, build and CI, design decisions.
- [docs/parsing.md](docs/parsing.md): how OCR lines become a price per unit, locale pack format,
  the regression corpus.

## Licence

[AGPL-3.0](LICENSE).
