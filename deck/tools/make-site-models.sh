#!/usr/bin/env bash
# Rebuilds site/models.json from the app's own built-in 3D controller models (ControllerModel).
# Usage: bash deck/tools/make-site-models.sh        (needs a JDK)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
P="$HERE/src/com/nokoding/deck"
OUT="$(mktemp -d)"
javac -d "$OUT" "$P/PadTest.java" "$P/ControllerModel.java" "$HERE/tools/site-models/DumpModel.java"
java -cp "$OUT" com.nokoding.deck.DumpModel > "$HERE/../site/models.json"
rm -rf "$OUT"
echo "wrote site/models.json"
