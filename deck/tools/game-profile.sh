#!/usr/bin/env bash
# Lower the picture resolution for a heavy game on the TV, and put it back, from a phone.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash game-profile.sh TVIP:PORT PACKAGE on [PERCENT] [game|display]
#        bash game-profile.sh TVIP:PORT PACKAGE off [game|display]
#        bash game-profile.sh TVIP:PORT PACKAGE status
#
# PERCENT is one of 85, 75, 67 or 50 (default 75).
#
# Two methods:
#   game     (default) Android's game mode downscale, for this app only:
#              on:  cmd game set --mode 4 --downscale 0.75 PACKAGE
#              off: cmd game reset PACKAGE
#            It does not touch the display. It only works for apps that declare themselves a game
#            (appCategory game) and it takes effect the next time the game starts.
#   display  The whole display, for everything on screen, until you run off:
#              on:  wm size WxH   and   wm density N   (the original values scaled by PERCENT)
#              off: wm size reset  and  wm density reset
#            Sizes are checked first: at least 640x360, the same shape as the panel, never bigger
#            than the panel. The current activity restarts when the size changes.
# Nothing else is touched. "status" only reads. If the picture ever looks wrong, run off for the
# display method, or by hand: adb shell wm size reset; adb shell wm density reset
#
# TVIP:PORT is on the TV under Developer options > Wireless debugging. Pair once first
# with: adb pair TVIP:PAIRPORT  (see docs/guide-termux.md). Details: docs/game-profiles.md.
set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

[ $# -ge 3 ] && [ $# -le 5 ] || die "usage: bash game-profile.sh TVIP:PORT PACKAGE on [PERCENT] [game|display] | off [game|display] | status"
TARGET="$1"
PKG="$2"
MODE="$3"
shift 3
PERCENT=75
METHOD=game
for a in "$@"; do
  case "$a" in
    game|display) METHOD="$a" ;;
    85|75|67|50) [ "$MODE" = on ] || die "PERCENT only goes with on"; PERCENT="$a" ;;
    *) die "unknown argument: $a (PERCENT is 85, 75, 67 or 50; method is game or display)" ;;
  esac
done

[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
[[ "$PKG" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] || die "package name looks wrong: $PKG (example com.roblox.client)"
[ ${#PKG} -le 255 ] || die "package name is too long"
case "$MODE" in on|off|status) ;; *) die "third argument must be on, off or status" ;; esac
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"

echo "connecting to $TARGET"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current (it changes after a reboot)."

sh_tv() { adb -s "$TARGET" shell "$@" | tr -d '\r'; }

sh_tv pm path "$PKG" >/dev/null 2>&1 || die "$PKG is not installed on the TV"

# The panel's own size and density, from wm. Output looks like "Physical size: 1920x1080" and
# "Physical density: 320"; an override adds an "Override size:" or "Override density:" line.
read_display() {
  PW=""; PH=""; PD=""
  local s d
  s=$(sh_tv wm size | sed -n 's/^Physical size: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | head -n 1)
  d=$(sh_tv wm density | sed -n 's/^Physical density: *\([0-9]*\).*/\1/p' | head -n 1)
  PW="${s% *}"; PH="${s#* }"; PD="$d"
  [[ "$PW" =~ ^[0-9]+$ && "$PH" =~ ^[0-9]+$ && "$PD" =~ ^[0-9]+$ ]] || die "could not read the panel's size and density from wm (got: '$s' and '$d')"
  [ "$PW" -ge 640 ] && [ "$PH" -ge 360 ] && [ "$PW" -le 8192 ] && [ "$PH" -le 8192 ] || die "the panel size looks wrong: ${PW}x${PH}"
  [ "$PD" -ge 120 ] && [ "$PD" -le 640 ] || die "the panel density looks wrong: $PD"
}

if [ "$MODE" = status ]; then
  echo "game mode for $PKG:"
  sh_tv cmd game list-configs "$PKG" 2>/dev/null | sed 's/^/  /' || true
  echo "display:"
  sh_tv wm size | sed 's/^/  /'
  sh_tv wm density | sed 's/^/  /'
  exit 0
fi

if [ "$METHOD" = game ]; then
  if [ "$MODE" = on ]; then
    case "$PERCENT" in 85) R=0.85 ;; 75) R=0.75 ;; 67) R=0.67 ;; 50) R=0.5 ;; esac
    echo "setting game mode downscale $R for $PKG"
    sh_tv cmd game set --mode 4 --downscale "$R" "$PKG"
    echo "done. Start $PKG again (close it first) for it to take effect. If nothing changes the app does not declare itself a game, use the display method."
  else
    echo "resetting game mode for $PKG"
    sh_tv cmd game reset "$PKG"
    echo "done."
  fi
  exit 0
fi

# display method
if [ "$MODE" = on ]; then
  read_display
  W=$(( (PW * PERCENT + 50) / 100 )); W=$(( W / 2 * 2 ))
  H=$(( (PH * PERCENT + 50) / 100 )); H=$(( H / 2 * 2 ))
  D=$(( (PD * PERCENT + 50) / 100 ))
  [ "$W" -ge 640 ] && [ "$H" -ge 360 ] && [ "$W" -le "$PW" ] && [ "$H" -le "$PH" ] || die "$PERCENT percent of ${PW}x${PH} would be ${W}x${H}, which is not allowed (minimum 640x360)"
  [ "$D" -ge 120 ] && [ "$D" -le 640 ] || die "the density $D is out of range (120 to 640)"
  echo "panel is ${PW}x${PH} at $PD dpi. Setting ${W}x${H} at $D dpi (the whole display, until you run off)."
  sh_tv wm size "${W}x${H}"
  sh_tv wm density "$D"
  echo "now:"
  sh_tv wm size | sed 's/^/  /'
  sh_tv wm density | sed 's/^/  /'
  echo "done. When you finish: bash game-profile.sh $TARGET $PKG off display"
else
  echo "putting the display back"
  sh_tv wm size reset
  sh_tv wm density reset
  echo "now:"
  sh_tv wm size | sed 's/^/  /'
  sh_tv wm density | sed 's/^/  /'
  echo "done."
fi
