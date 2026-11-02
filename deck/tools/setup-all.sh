#!/usr/bin/env bash
# Run every one-time adb step Visor can use, in one go, from a phone in Termux.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash setup-all.sh TVIP:PORT [run|status|undo] [options]
#
#   run      (default) show the plan, ask once, then do every step in the chosen groups
#   status   only read: shows what is set now, changes nothing
#   undo     takes the steps back (the groups you choose, see the table below)
#
# Options:
#   --only LIST            only these groups, comma separated
#   --skip LIST            all groups except these
#   --apk FILE             install this Visor APK first (adb install -r), run mode only
#   --saver-minutes N      idle time before the screensaver starts, 1 to 120 (saver group)
#   --disable-google-launcher   ALSO turn off Google TV's own launcher. Never part of the default. Read
#                          docs/adb-commands.md first: some boxes can need a factory reset if nothing replaces it
#   --restore-google-launcher   undo mode: turn Google TV's launcher on again and give it the home role back
#   --yes                  do not ask
#
# Groups (every command is printed before it runs, and the same list is in docs/adb-commands.md):
#   access     usage access, for the task manager and the system recents source
#   overlay    the overlay service: the PS menu over other apps, remap and combos in other apps,
#              stick and touchpad volume over other apps (enable-ps-overlay.sh, keeps your other services)
#   display    lets Visor change the display itself, for the experimental VISOR DISPLAY game profile method
#   saver      makes Visor the TV's screensaver (enable-screensaver.sh)
#   home       gives Visor the home role and sets its home activity (the first two home steps)
# By default run does access, overlay, display, saver and home. Nothing else is touched.
#
# TVIP:PORT is on the TV under Developer options > Wireless debugging. Pair once first with:
# adb pair TVIP:PAIRPORT  (see docs/guide-termux.md).
set -uo pipefail

PKG=com.nokoding.deck
GTV=com.google.android.apps.tv.launcherx
HERE="$(cd "$(dirname "$0")" && pwd)"
ALL="access overlay display saver home"

die() { echo "error: $*" >&2; exit 1; }

[ $# -ge 1 ] || die "usage: bash setup-all.sh TVIP:PORT [run|status|undo] [--only LIST] [--skip LIST] [--apk FILE] [--saver-minutes N] [--disable-google-launcher] [--restore-google-launcher] [--yes]"
TARGET="$1"; shift
MODE=run
if [ $# -ge 1 ]; then case "$1" in run|status|undo) MODE="$1"; shift ;; esac; fi

ONLY=""; SKIP=""; APK=""; MINUTES=""; DISABLE_GTV=0; RESTORE_GTV=0; YES=0
while [ $# -gt 0 ]; do
  case "$1" in
    --only) [ $# -ge 2 ] || die "--only needs a list"; ONLY="$2"; shift 2 ;;
    --skip) [ $# -ge 2 ] || die "--skip needs a list"; SKIP="$2"; shift 2 ;;
    --apk) [ $# -ge 2 ] || die "--apk needs a file"; APK="$2"; shift 2 ;;
    --saver-minutes) [ $# -ge 2 ] || die "--saver-minutes needs a number"; MINUTES="$2"; shift 2 ;;
    --disable-google-launcher) DISABLE_GTV=1; shift ;;
    --restore-google-launcher) RESTORE_GTV=1; shift ;;
    --yes) YES=1; shift ;;
    *) die "unknown option: $1" ;;
  esac
done

# host:port only, so the value cannot smuggle extra adb options
[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
valid_list() { local g; for g in ${1//,/ }; do case " $ALL " in *" $g "*) ;; *) return 1 ;; esac; done; }
[ -z "$ONLY" ] || valid_list "$ONLY" || die "--only takes: ${ALL// /, }"
[ -z "$SKIP" ] || valid_list "$SKIP" || die "--skip takes: ${ALL// /, }"
[ -z "$ONLY" ] || [ -z "$SKIP" ] || die "use --only or --skip, not both"
if [ -n "$MINUTES" ]; then
  [[ "$MINUTES" =~ ^[0-9]{1,3}$ ]] || die "--saver-minutes must be a whole number from 1 to 120"
  MINUTES=$((10#$MINUTES))
  [ "$MINUTES" -ge 1 ] && [ "$MINUTES" -le 120 ] || die "--saver-minutes must be from 1 to 120"
fi
if [ -n "$APK" ]; then
  [ "$MODE" = run ] || die "--apk only goes with run"
  [ -f "$APK" ] || die "APK not found: $APK"
fi
[ "$RESTORE_GTV" = 0 ] || [ "$MODE" = undo ] || die "--restore-google-launcher only goes with undo"
[ "$DISABLE_GTV" = 0 ] || [ "$MODE" = run ] || die "--disable-google-launcher only goes with run"
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"

GROUPS_ON=""
for g in $ALL; do
  if [ -n "$ONLY" ]; then case ",$ONLY," in *",$g,"*) GROUPS_ON="$GROUPS_ON $g" ;; esac
  elif [ -n "$SKIP" ]; then case ",$SKIP," in *",$g,"*) ;; *) GROUPS_ON="$GROUPS_ON $g" ;; esac
  else GROUPS_ON="$GROUPS_ON $g"; fi
done
on() { case " $GROUPS_ON " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }

echo "connecting to $TARGET"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current (it changes after a reboot)."

# A command on the TV, printed first so you can see exactly what happens.
tv() { echo "  + adb shell $*"; adb -s "$TARGET" shell "$@" | tr -d '\r'; return "${PIPESTATUS[0]}"; }
# Same, but quiet output (reads)
tvq() { adb -s "$TARGET" shell "$@" 2>/dev/null | tr -d '\r'; }
helper() { echo "  + bash $(basename "$1") ${*:2}"; bash "$@"; }

FAILED=""
fail() { FAILED="$FAILED $1"; echo "  ! $1 did not finish"; }

if [ "$MODE" = status ]; then
  echo "== status (nothing is changed)"
  tvq pm path "$PKG" >/dev/null || echo "Visor is not installed on the TV"
  echo "-- access"; tv appops get "$PKG" GET_USAGE_STATS || true
  echo "-- overlay"; helper "$HERE/enable-ps-overlay.sh" "$TARGET" status || true
  echo "-- display"; tv dumpsys package "$PKG" | grep -E "WRITE_SECURE_SETTINGS" | head -n 3 || true
  tv wm size || true; tv wm density || true
  echo "-- saver"; helper "$HERE/enable-screensaver.sh" "$TARGET" status || true
  echo "-- home"; tv cmd role get-role-holders android.app.role.HOME || true
  echo "-- google launcher (disabled packages that match)"; tv pm list packages -d | grep -c "$GTV" || true
  exit 0
fi

if [ "$MODE" = run ]; then
  tvq pm path "$PKG" >/dev/null 2>&1 || [ -n "$APK" ] || die "Visor is not installed on the TV. Use --apk visor-launcher-v2.19-beta.apk or install it first."
  echo "== plan (run)"
  [ -z "$APK" ] || echo "  install: adb install -r $APK"
  on access && echo "  access:  adb shell appops set $PKG GET_USAGE_STATS allow"
  on overlay && echo "  overlay: bash enable-ps-overlay.sh $TARGET on   (reads the service list, adds only Visor's entry)"
  on display && echo "  display: adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
  on saver && echo "  saver:   bash enable-screensaver.sh $TARGET on${MINUTES:+ $MINUTES}"
  on home && { echo "  home:    adb shell cmd role add-role-holder --user 0 android.app.role.HOME $PKG"; echo "           adb shell cmd package set-home-activity $PKG/.HomeActivity"; }
  [ "$DISABLE_GTV" = 0 ] || echo "  google launcher: adb shell pm disable-user --user 0 $GTV   (you asked for this one)"
  if [ "$YES" = 0 ]; then
    read -r -p "Run all of these? [y/N] " a
    case "$a" in y|Y|yes|YES) ;; *) echo "nothing was changed"; exit 0 ;; esac
  fi
  if [ -n "$APK" ]; then
    echo "== install"
    echo "  + adb install -r $APK"
    adb -s "$TARGET" install -r "$APK" || die "install failed"
  fi
  if on access; then echo "== access"; tv appops set "$PKG" GET_USAGE_STATS allow && tv appops get "$PKG" GET_USAGE_STATS || fail access; fi
  if on overlay; then echo "== overlay"; helper "$HERE/enable-ps-overlay.sh" "$TARGET" on || fail overlay; fi
  if on display; then echo "== display"; tv pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS || fail display; fi
  if on saver; then echo "== saver"; if [ -n "$MINUTES" ]; then helper "$HERE/enable-screensaver.sh" "$TARGET" on "$MINUTES" || fail saver; else helper "$HERE/enable-screensaver.sh" "$TARGET" on || fail saver; fi; fi
  if on home; then
    echo "== home"
    tv cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" || fail home
    tv cmd package set-home-activity "$PKG/.HomeActivity" || fail home
    tv cmd role get-role-holders android.app.role.HOME || true
  fi
  if [ "$DISABLE_GTV" = 1 ]; then
    echo "== google launcher"
    echo "  Google TV's launcher wins the Home button while it is enabled. Turning it off is the last step of making Visor the home screen."
    echo "  Undo: bash setup-all.sh $TARGET undo --only home --restore-google-launcher"
    tv pm disable-user --user 0 "$GTV" || fail google-launcher
  fi
  echo
  if [ -n "$FAILED" ]; then echo "done, but these did not finish:$FAILED. See docs/adb-commands.md for each one by hand."; exit 1; fi
  echo "done. Open Visor and see Setup, SYSTEM, TUTORIAL."
  [ "$DISABLE_GTV" = 1 ] || echo "Home may still open Google TV's launcher. That is the optional --disable-google-launcher step (docs/adb-commands.md)."
  exit 0
fi

# undo
echo "== undo"
if [ "$YES" = 0 ]; then
  read -r -p "Take back the steps in these groups:$GROUPS_ON ? [y/N] " a
  case "$a" in y|Y|yes|YES) ;; *) echo "nothing was changed"; exit 0 ;; esac
fi
if on access; then echo "== access"; tv appops set "$PKG" GET_USAGE_STATS default || fail access; fi
if on overlay; then echo "== overlay"; helper "$HERE/enable-ps-overlay.sh" "$TARGET" off || fail overlay; fi
if on display; then
  echo "== display"
  tv wm size reset || fail display
  tv wm density reset || fail display
  tv pm revoke "$PKG" android.permission.WRITE_SECURE_SETTINGS || fail display
fi
if on saver; then echo "== saver"; helper "$HERE/enable-screensaver.sh" "$TARGET" off || fail saver; fi
if on home; then
  echo "== home"
  if [ "$RESTORE_GTV" = 1 ]; then
    tv pm enable "$GTV" || fail home
    tv cmd role add-role-holder --user 0 android.app.role.HOME "$GTV" || fail home
  else
    echo "  Visor stays the home app. To give Google TV's launcher the home role back, add --restore-google-launcher."
  fi
fi
echo
if [ -n "$FAILED" ]; then echo "done, but these did not finish:$FAILED"; exit 1; fi
echo "done."
