#!/usr/bin/env bash
# Push a Visor theme file to the TV from a phone, then make Visor rescan its themes folder.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash install-theme.sh TVIP:PORT mytheme.json
#
# A theme is one small JSON file (at most 8 KB), see docs/themes.md. After it is pushed, open
# Setup > LOOK > THEME EDITOR, pick it under SAVED THEMES and press LOAD THEME.
# Open Visor once before the first push so its folder exists.
set -euo pipefail

PKG=com.nokoding.deck
DEST=/sdcard/Android/data/$PKG/files/themes
MAX_FILE=8192

die() { echo "error: $*" >&2; exit 1; }

[ $# -eq 2 ] || die "usage: bash install-theme.sh TVIP:PORT <theme.json>"
TARGET="$1"
SRC="$2"

[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"
[ -f "$SRC" ] || die "not a file: $SRC"

NAME="$(basename "$SRC")"
case "${NAME,,}" in
  *.json) ;;
  *) die "a theme file must end in .json: $NAME" ;;
esac
BASE="${NAME%.*}"
# the same characters Visor accepts, no path and no leading dot
[[ "$BASE" =~ ^[A-Za-z0-9][A-Za-z0-9._\ -]{0,23}$ ]] || die "rename it to 24 letters, digits, space, dot, dash or underscore at most: $BASE"
SIZE="$(wc -c < "$SRC" | tr -d ' ')"
[ "$SIZE" -le "$MAX_FILE" ] || die "the file is over 8 KB, Visor will skip it"
grep -q '"format"' "$SRC" && grep -q '"colors"' "$SRC" || die "this does not look like a Visor theme file (needs format and colors)"

echo "connecting to $TARGET"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current."
adb -s "$TARGET" shell pm path "$PKG" >/dev/null 2>&1 || die "Visor is not installed on the TV"
adb -s "$TARGET" shell mkdir -p "$DEST"

echo "pushing $NAME"
adb -s "$TARGET" push "$SRC" "$DEST/$NAME"
adb -s "$TARGET" shell am start -n "$PKG/.HomeActivity" --es deck_cmd reload_themes >/dev/null
echo "done. In Visor: Setup > LOOK > THEME EDITOR > SAVED THEMES, then LOAD THEME. A file Visor cannot read is skipped."
