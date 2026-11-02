#!/usr/bin/env bash
# Take real screenshots of the TV with adb, named by screen, one at a time.
# Works on a laptop (bash, Git Bash or WSL) and in Termux (pkg install android-tools).
#
# Usage: bash screenshots.sh TVIP:PORT [OUTPUT_FOLDER]     (default folder: ./visor-screenshots)
#        bash screenshots.sh --list                         (only print the list of screens)
#
# For each screen it tells you what to open on the TV. Open it, press Enter here, and the script saves a PNG.
# Type s to skip a screen, q to stop. Screenshots of your own TV may show your own app names: use the ones you
# are happy to share. See docs/screenshots.md for the list and how real screenshots replace the rendered previews.
set -euo pipefail

SCREENS=(
  "home-wheel|The home screen in WHEEL layout (Setup, LOOK, LAYOUT)"
  "home-grid|The home screen in GRID layout"
  "setup-help|Setup with a row focused, so the help line shows in the footer"
  "control-center-quick|Press the PS button: the control center, QUICK section"
  "control-center-apps|The control center, APPS section (the task manager)"
  "menu-over-game|The control center opened over a game (needs the overlay service)"
  "game-profile|Setup, SYSTEM, GAME PROFILES, one app"
  "remap|Setup, REMAP"
  "remap-learn|Setup, REMAP, LEARN A BUTTON"
  "theme-editor|Setup, LOOK, THEME EDITOR"
  "wallpaper|Home screen with a wallpaper pack picked"
  "screensaver|Setup, LOOK, SCREENSAVER, PREVIEW"
  "gamepad-test|Setup, PAD, GAMEPAD TEST with a controller connected, a few buttons pressed"
  "gyro-test|Setup, PAD, GYRO TEST with a controller connected"
  "tutorial|Setup, SYSTEM, TUTORIAL, any lesson"
)

die() { echo "error: $*" >&2; exit 1; }

if [ "${1:-}" = "--list" ]; then
  for s in "${SCREENS[@]}"; do echo "${s%%|*}  -  ${s#*|}"; done
  exit 0
fi

[ $# -ge 1 ] && [ $# -le 2 ] || die "usage: bash screenshots.sh TVIP:PORT [OUTPUT_FOLDER]  (or --list)"
TARGET="$1"
OUT="${2:-visor-screenshots}"
[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
command -v adb >/dev/null 2>&1 || die "adb not found (laptop: install platform-tools, Termux: pkg install -y android-tools)"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current."
mkdir -p "$OUT"

echo "Saving to: $OUT"
echo "Open each screen on the TV, then press Enter here. s = skip, q = stop."
for s in "${SCREENS[@]}"; do
  name="${s%%|*}"; what="${s#*|}"
  echo
  echo "[$name] $what"
  read -r -p "  ready? (Enter / s / q) " ans
  case "$ans" in
    q|Q) break ;;
    s|S) continue ;;
  esac
  # exec-out keeps the PNG bytes intact (no line ending changes)
  adb -s "$TARGET" exec-out screencap -p > "$OUT/$name.png"
  size="$(wc -c < "$OUT/$name.png" | tr -d ' ')"
  if [ "$size" -lt 1000 ]; then
    echo "  that file is tiny ($size bytes): the TV may block screenshots on this screen (a protected app, for example)"
    rm -f "$OUT/$name.png"
  else
    echo "  saved $OUT/$name.png ($size bytes)"
  fi
done
echo
echo "Done. Open an issue or a pull request to share them. Check each picture for anything private first."
