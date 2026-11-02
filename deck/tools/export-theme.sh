#!/usr/bin/env bash
# Copy a theme file that SAVE THEME FILE wrote on the TV to your phone, so you can share it.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash export-theme.sh TVIP:PORT "THEME 1"        (copies THEME 1.json to the current folder)
#        bash export-theme.sh TVIP:PORT --list           (lists the themes on the TV)
set -euo pipefail

PKG=com.nokoding.deck
SRC=/sdcard/Android/data/$PKG/files/themes

die() { echo "error: $*" >&2; exit 1; }

[ $# -eq 2 ] || die "usage: bash export-theme.sh TVIP:PORT <name or --list>"
TARGET="$1"
NAME="$2"

[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET"
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current."

if [ "$NAME" = "--list" ]; then
  adb -s "$TARGET" shell ls "$SRC" 2>/dev/null || die "no themes folder yet. Save a theme in Visor first."
  exit 0
fi
NAME="${NAME%.json}"
[[ "$NAME" =~ ^[A-Za-z0-9][A-Za-z0-9._\ -]{0,23}$ ]] || die "the name has characters Visor never uses: $NAME"
adb -s "$TARGET" pull "$SRC/$NAME.json" "./$NAME.json"
echo "done: ./$NAME.json. Send it to a friend. They run: bash install-theme.sh TVIP:PORT \"$NAME.json\""
