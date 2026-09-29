#!/usr/bin/env bash
# Signs the unsigned release APKs of a draft GitHub Release and uploads the signed ones.
#
#   tools/release/sign.sh v0.1.0 path/to/release.jks key-alias
#
# Needs: gh (logged in), the Android build-tools on PATH or ANDROID_HOME set. Asks for the
# keystore and key passwords. Never stores them anywhere.
set -euo pipefail

tag="${1:?tag, e.g. v0.1.0}"
keystore="${2:?keystore file}"
alias="${3:?key alias}"
version="${tag#v}"

tools="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools"
build_tools="$(ls -d "$tools"/* | sort -V | tail -1)"
apksigner="$build_tools/apksigner"
zipalign="$build_tools/zipalign"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "Downloading unsigned APKs of $tag ..."
gh release download "$tag" --pattern '*-unsigned.apk' --dir "$work/unsigned"

mkdir -p "$work/signed"
for unsigned in "$work"/unsigned/*.apk; do
  name="$(basename "$unsigned" -unsigned.apk)"
  aligned="$work/$name-aligned.apk"
  signed="$work/signed/$name.apk"
  "$zipalign" -p -f 4 "$unsigned" "$aligned"
  "$apksigner" sign --ks "$keystore" --ks-key-alias "$alias" --out "$signed" "$aligned"
  "$apksigner" verify --print-certs "$signed" | head -3
done

(cd "$work/signed" && sha256sum *.apk > SHA256SUMS.txt && cat SHA256SUMS.txt)

echo "Uploading signed APKs ..."
gh release upload "$tag" "$work"/signed/*.apk "$work/signed/SHA256SUMS.txt" --clobber
echo "Done. Review the draft release on GitHub, remove the unsigned files if you like, then publish it."
