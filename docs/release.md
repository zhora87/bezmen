# Releases

Versions follow `major.minor.patch`. The Android `versionCode` is derived as
`major * 10000 + minor * 100 + patch`, so `0.1.0` is `100` and `1.2.3` is `10203`.

## Cutting a release

1. Move the `Unreleased` section of `CHANGELOG.md` under a new `## [x.y.z] - YYYY-MM-DD` heading
   and add the same text to `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
2. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`.
3. The `Release` workflow builds unsigned release APKs of both flavors for every ABI, checks that
   no variant carries the INTERNET permission, and creates a **draft** GitHub Release with the
   APKs, `SHA256SUMS-unsigned.txt` and the changelog section as notes.
4. Sign on your machine: `tools/release/sign.sh v0.1.0 /path/to/release.jks bezmen`. The script
   downloads the unsigned APKs, zipaligns and signs them with `apksigner`, writes `SHA256SUMS.txt`
   and uploads everything to the draft.
5. Review the draft and publish it.

The signing key never leaves the maintainer's machine and CI never sees it. A local signed build
is possible too: put a git-ignored `androidApp/keystore.properties` with `storeFile`,
`storePassword`, `keyAlias` and `keyPassword` next to the build script and run
`./gradlew assembleFossRelease`.

## Stores

- **F-Droid** builds from source and signs with its own key. The `foss` flavor has no Google
  dependency. Open question before the first submission: the OCR models are fetched at build time
  from this repository's release assets, and F-Droid builds have no network access; the models
  will have to be provided as a source library instead.
- **Google Play** takes the `play` flavor. Store listings for both live under
  `fastlane/metadata/android/`.
