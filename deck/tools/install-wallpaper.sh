#!/usr/bin/env bash
# Push a Visor wallpaper pack to the TV from a phone, then make Visor reload its wallpapers.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash install-wallpaper.sh TVIP:PORT mypack.zip
#        bash install-wallpaper.sh TVIP:PORT mypack/        (a folder works too)
#
# A pack is a folder (or a zip of one) with a wallpaper.json and its images:
# .png .jpg .jpeg .webp .gif, 8 MB per image, 24 MB and 16 files per pack. A video wallpaper (2.17,
# experimental) is one .mp4 up to 40 MB with type "video" in wallpaper.json, 48 MB per pack.
# See docs/wallpapers.md and the example in docs/examples/wallpaper-example.
#
# TVIP:PORT is on the TV under Developer options > Wireless debugging. Pair once first
# with: adb pair TVIP:PAIRPORT  (see docs/guide-termux.md). Open Visor once before the
# first push so its folder exists.
set -euo pipefail

PKG=com.nokoding.deck
DEST=/sdcard/Android/data/$PKG/files/wallpapers
MAX_IMAGE=$((8 * 1024 * 1024))
MAX_TOTAL=$((24 * 1024 * 1024))
MAX_VIDEO=$((40 * 1024 * 1024))
MAX_TOTAL_VIDEO=$((48 * 1024 * 1024))
MAX_ZIP=$((49 * 1024 * 1024))
MAX_FILES=16

die() { echo "error: $*" >&2; exit 1; }

[ $# -eq 2 ] || die "usage: bash install-wallpaper.sh TVIP:PORT <pack.zip or folder>"
TARGET="$1"
SRC="${2%/}"

# host:port only, so the value cannot smuggle extra adb options
[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"
[ -e "$SRC" ] || die "not found: $SRC"

NAME="$(basename "$SRC")"
# same characters Visor accepts for a pack name
[[ "$NAME" =~ ^[A-Za-z0-9][A-Za-z0-9._\ -]{0,59}$ ]] || die "rename it to letters, digits, space, dot, dash or underscore: $NAME"

size_of() { wc -c < "$1" | tr -d ' '; }

if [ -f "$SRC" ]; then
  case "${NAME,,}" in
    *.zip) ;;
    *) die "a file must be a .zip: $NAME" ;;
  esac
  [ "$(size_of "$SRC")" -le "$MAX_ZIP" ] || die "zip is over 49 MB, Visor will refuse it"
elif [ -d "$SRC" ]; then
  # the same caps Visor checks, so a bad folder fails here and not silently on the TV
  COUNT=0
  TOTAL=0
  HAVE_IMAGE=0
  HAVE_VIDEO=0
  while IFS= read -r -d '' f; do
    base="$(basename "$f")"
    case "${base,,}" in
      wallpaper.json|*.png|*.jpg|*.jpeg|*.webp|*.gif|*.mp4) ;;
      *) continue ;;
    esac
    s="$(size_of "$f")"
    COUNT=$((COUNT + 1))
    TOTAL=$((TOTAL + s))
    [ "${base,,}" = wallpaper.json ] || HAVE_IMAGE=1
    case "${base,,}" in
      *.mp4) HAVE_VIDEO=1; [ "$s" -le "$MAX_VIDEO" ] || die "$base is over 40 MB, Visor will refuse the pack" ;;
      wallpaper.json) ;;
      *) [ "$s" -le "$MAX_IMAGE" ] || die "$base is over 8 MB, Visor will refuse the pack" ;;
    esac
  done < <(find "$SRC" -maxdepth 1 -type f ! -name '.*' -print0)
  [ "$HAVE_IMAGE" -eq 1 ] || die "no image files in $SRC"
  [ "$COUNT" -le "$MAX_FILES" ] || die "$COUNT files, Visor takes at most $MAX_FILES"
  if [ "$HAVE_VIDEO" -eq 1 ]; then
    [ "$TOTAL" -le "$MAX_TOTAL_VIDEO" ] || die "the pack is over 48 MB, Visor will refuse it"
  else
    [ "$TOTAL" -le "$MAX_TOTAL" ] || die "the pack is over 24 MB, Visor will refuse it"
  fi
  [ -f "$SRC/wallpaper.json" ] || echo "note: no wallpaper.json. Visor will treat a single image as a plain wallpaper and refuse more than one."
else
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
echo "asking Visor to reload wallpapers"
adb -s "$TARGET" shell am start -n "$PKG/.HomeActivity" --es deck_cmd reload_wallpapers >/dev/null

echo "done. Visor unpacks zips itself and picks a single new pack. Check Setup > LOOK > WALLPAPER for the result and the status line under it."
