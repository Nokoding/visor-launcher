#!/usr/bin/env bash
# Checks the key table in RemapKey.java against the KEYCODE_ constants in android.jar.
# Every k(...) line must name a constant that exists and has the same number.
# Usage: tools/check-keycodes.sh <sdk_root> [--list]    (--list prints every KEYCODE_ the platform has)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
SDK="$1"
JAR="$SDK/platforms/android-34/android.jar"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
javap -constants -cp "$JAR" android.view.KeyEvent 2>/dev/null | sed -nE 's/.*int KEYCODE_([A-Z0-9_]+) = ([0-9]+);.*/\1 \2/p' | sort > "$T/jar.txt"
if [ "${2:-}" = "--list" ]; then sort -k2 -n "$T/jar.txt" | awk '{print $2, "KEYCODE_" $1}'; exit 0; fi
sed -nE 's/^ *k\(([0-9]+), "([A-Z0-9_]+)".*/\2 \1/p' "$HERE/src/com/nokoding/deck/RemapKey.java" | sort > "$T/table.txt"
bad=0
while read -r name code; do
  if ! grep -qx "$name $code" "$T/jar.txt"; then
    echo "MISMATCH: KEYCODE_$name = $code is not in android.jar (jar says: $(grep "^$name " "$T/jar.txt" || echo 'no such constant'))"
    bad=$((bad + 1))
  fi
done < "$T/table.txt"
echo "table: $(wc -l < "$T/table.txt") keys, android.jar: $(wc -l < "$T/jar.txt") KEYCODE_ constants, mismatches: $bad"
[ "$bad" -eq 0 ]
