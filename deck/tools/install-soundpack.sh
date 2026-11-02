#!/usr/bin/env bash
# Push a Visor sound pack to the TV from a phone, then make Visor reload its packs.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash install-soundpack.sh TVIP:PORT mypack.zip
#        bash install-soundpack.sh TVIP:PORT mypack/        (a folder works too)
#
# TVIP:PORT is on the TV under Developer options > Wireless debugging. Pair once first
# with: adb pair TVIP:PAIRPORT  (see docs/guide-termux.md). Open Visor once before the
# first push so its folder exists.
set -euo pipefail

PKG=com.nokoding.deck
DEST=/sdcard/Android/data/$PKG/files/soundpacks

die() { echo "error: $*" >&2; exit 1; }

[ $# -eq 2 ] || die "usage: bash install-soundpack.sh TVIP:PORT <pack.zip or folder>"
TARGET="$1"
SRC="${2%/}"

# host:port only, so the value cannot smuggle extra adb options
[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"
[ -e "$SRC" ] || die "not found: $SRC"

NAME="$(basename "$SRC")"
# same characters Visor accepts for a pack name
[[ "$NAME" =~ ^[A-Za-z0-9][A-Za-z0-9._\ -]{0,59}$ ]] || die "rename it to letters, digits, space, dot, dash or underscore: $NAME"
if [ -f "$SRC" ]; then
  case "${NAME,,}" in
    *.zip) ;;
    *) die "a file must be a .zip: $NAME" ;;
  esac
  SIZE=$(wc -c < "$SRC")
  [ "$SIZE" -le $((32 * 1024 * 1024)) ] || die "zip is over 32 MB, Visor will refuse it"
elif [ ! -d "$SRC" ]; then
  die "not a file or folder: $SRC"
fi

echo "connecting to $TARGET"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current (it changes after a reboot)."

adb -s "$TARGET" shell pm path "$PKG" >/dev/null 2>&1 || die "Visor is not installed on the TV"
adb -s "$TARGET" shell mkdir -p "$DEST"

echo "pushing $NAME"
adb -s "$TARGET" push "$SRC" "$DEST/"

# Safe reload: an explicit intent with one string extra that Visor checks against a short
# whitelist. No broadcast receiver, nothing exported beyond the home activity it already has.
echo "asking Visor to reload packs"
adb -s "$TARGET" shell am start -n "$PKG/.HomeActivity" --es deck_cmd reload_packs >/dev/null

echo "done. Visor unpacks zips itself. Check Setup > SOUND > Install pack for the result."
