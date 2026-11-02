#!/usr/bin/env bash
# Re-signs a Visor APK with your own key, on your phone in Termux.
# Use this when a cloud build was made with a throwaway key.
# Needs apksigner: pkg install apksigner   (it comes with Termux's Android build tools)
# Usage: bash sign-on-phone.sh <in.apk> <deck.keystore> <password file> <out.apk>
# The password file holds the keystore password on one line. Keep both files private.
set -euo pipefail
if [ "$#" -ne 4 ]; then echo "Usage: bash $0 <in.apk> <deck.keystore> <password file> <out.apk>"; exit 2; fi
IN="$1"; KS="$2"; PW="$3"; OUT="$4"
for f in "$IN" "$KS" "$PW"; do [ -f "$f" ] || { echo "Missing file: $f"; exit 1; }; done
command -v apksigner >/dev/null || { echo "apksigner not found. Run: pkg install apksigner"; exit 1; }
apksigner sign --ks "$KS" --ks-pass "file:$PW" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --min-sdk-version 28 --out "$OUT" "$IN"
apksigner verify --print-certs "$OUT" | grep -i "SHA-256"
echo "Expected certificate digest: edae1fa04e6dac00154ad1b97814d9327df982284735581e1d29dfbf0903e406"
echo "Signed: $OUT"
