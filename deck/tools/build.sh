#!/usr/bin/env bash
# Plain aapt2 + javac + R8 + zipalign + apksigner pipeline. No Gradle, no AndroidX.
# Fonts in assets/ are stored uncompressed (-0 ttf) so Typeface can map them instead of copying.
# Usage: tools/build.sh <sdk_root> <keystore> <store_password_file> <out_apk>
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
SDK="$1"; KS="$2"; PWFILE="$3"; OUT="$4"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
R8JAR="$SDK/cmdline-tools/latest/lib/r8.jar"
B="$HERE/build"
rm -rf "$B"; mkdir -p "$B/gen" "$B/classes" "$B/dex"

"$BT/aapt2" compile --dir "$HERE/res" -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" \
  --manifest "$HERE/AndroidManifest.xml" \
  --min-sdk-version 28 --target-sdk-version 34 \
  --version-code 22 --version-name 2.18-beta \
  -A "$HERE/assets" -0 ttf \
  --java "$B/gen" --proguard "$B/aapt-rules.pro" \
  "$B/res.zip"

javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -Xlint:all,-options -Werror \
  -d "$B/classes" $(find "$HERE/src" "$B/gen" -name '*.java')
(cd "$B/classes" && jar cf "$B/classes.jar" .)

java -cp "$R8JAR" com.android.tools.r8.R8 --release --min-api 28 \
  --lib "$JAR" --pg-conf "$HERE/proguard-rules.pro" --pg-conf "$B/aapt-rules.pro" \
  --pg-map-output "$B/mapping.txt" --output "$B/dex" "$B/classes.jar"

cp "$B/base.apk" "$B/unsigned.apk"
# Dex stays uncompressed so ART can map it in place (less RAM and storage on minSdk 28+).
(cd "$B/dex" && zip -q -0 -X "$B/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "file:$PWFILE" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --min-sdk-version 28 --out "$OUT" "$B/aligned.apk"
"$BT/apksigner" verify --verbose "$OUT"
ls -l "$OUT"
