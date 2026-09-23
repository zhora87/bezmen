# Contributing to Bezmen

Thanks for helping. This is a volunteer, non-commercial project; the rules below exist so it stays
alive after its predecessors did not.

## Ground rules

- **Privacy is not negotiable.** No network permission, no analytics, no accounts, no third-party
  SDKs that phone home. A change that adds any of these will not be merged
  (see [docs/architecture.md](docs/architecture.md), Permissions).
- **Open models only.** Every model ships with its licence and a sha256 in `models.lock`.
- **Regional logic lives in locale packs**, not in code ([docs/parsing.md](docs/parsing.md#locale-pack-format)).

## Building

```
./gradlew build          # both flavors, unit tests, lint, INTERNET-permission check
./gradlew detekt         # static analysis and formatting
./gradlew :androidApp:installFossDebug
```

JDK 21 and an Android SDK with platform 37 are required. Gradle downloads everything else,
including the OCR models pinned in `models.lock`.

## Reporting a misrecognised price tag

Use the "Price tag recognised incorrectly" issue template and attach a photo. Each report becomes
a corpus case and a regression test before the parser is touched. Accuracy on the corpus is checked
in CI and is not allowed to drop.

## Adding a locale pack

Open a "New locale pack" issue, then submit a PR with:

1. `locale-packs/<id>.json` validating against `locale-packs/schema.json`;
2. OCR dumps and expected values for at least 20 price tags from at least 2 store chains under
   `corpus/ocr/` and `corpus/expected/` (see `corpus/README.md`; photos themselves are not versioned yet).

No Kotlin required.

## Code

- Kotlin, `ktlint_official` style enforced through detekt-formatting. `./gradlew detekt` must pass.
- Domain code (`core/domain`) is pure Kotlin, tested on the JVM, 80% coverage minimum.
- Tests first for parser changes: add the corpus case, watch it fail, then fix.
- Commit messages follow Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`,
  `chore:`, `perf:`, `ci:`.
- Small PRs. One concern per PR.

## Licence

By contributing you agree that your contribution is licensed under AGPL-3.0. No CLA.
