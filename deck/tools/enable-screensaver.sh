#!/usr/bin/env bash
# Make Visor's own screensaver (DeckDream) the TV's screensaver, from a phone.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash enable-screensaver.sh TVIP:PORT on [MINUTES]   choose Visor's screensaver, turn screensavers on,
#                                                            and (if MINUTES is given, 1 to 120) set how long
#                                                            the TV must be idle before it starts
#        bash enable-screensaver.sh TVIP:PORT off            drop Visor's choice (only if Visor's is the current one)
#        bash enable-screensaver.sh TVIP:PORT status         show what is set now
#
# What it writes, and nothing else:
#   secure screensaver_components            Visor's dream, as package/class
#   secure screensaver_enabled               1
#   secure screensaver_activate_on_sleep     1  (Android's power manager starts a dream at the end of the idle
#                                                time only when this is 1; many TV builds already have it on)
#   system screen_off_timeout                only with MINUTES, in milliseconds (the TV's own screensaver page
#                                            uses this setting for "start after")
# "off" deletes screensaver_components, so Android falls back to its own default screensaver. It leaves the
# other values as they are. "on" prints the values it found first, so you can put them back by hand.
#
# TVIP:PORT is on the TV under Developer options > Wireless debugging. Pair once first
# with: adb pair TVIP:PAIRPORT  (see docs/guide-termux.md). Details: docs/screensaver.md.
set -euo pipefail

PKG=com.nokoding.deck
CLS=com.nokoding.deck.DeckDream
COMP=$PKG/$CLS
SHORT=$PKG/.DeckDream
MIN_MIN=1
MAX_MIN=120

die() { echo "error: $*" >&2; exit 1; }

[ $# -ge 2 ] && [ $# -le 3 ] || die "usage: bash enable-screensaver.sh TVIP:PORT on [MINUTES] | off | status"
TARGET="$1"
MODE="$2"
MINUTES="${3:-}"

# host:port only, so the value cannot smuggle extra adb options
[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
case "$MODE" in on|off|status) ;; *) die "second argument must be on, off or status" ;; esac
if [ -n "$MINUTES" ]; then
  [ "$MODE" = on ] || die "MINUTES only goes with on"
  [[ "$MINUTES" =~ ^[0-9]{1,3}$ ]] || die "MINUTES must be a whole number from $MIN_MIN to $MAX_MIN"
  MINUTES=$((10#$MINUTES))
  [ "$MINUTES" -ge "$MIN_MIN" ] && [ "$MINUTES" -le "$MAX_MIN" ] || die "MINUTES must be from $MIN_MIN to $MAX_MIN"
fi
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"

echo "connecting to $TARGET"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current (it changes after a reboot)."

sh_tv() { adb -s "$TARGET" shell "$@" | tr -d '\r'; }

sh_tv pm path "$PKG" >/dev/null 2>&1 || die "Visor is not installed on the TV"
VC=$(sh_tv dumpsys package "$PKG" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1)
if [ "$MODE" = on ] && [ -n "$VC" ] && [ "$VC" -lt 11 ]; then
  die "Visor on the TV is too old (version code $VC). Install visor-launcher-v2.19-beta.apk or newer first."
fi

# a setting's value, "" when it is not set
get() {
  local v
  v=$(sh_tv settings get "$1" "$2")
  [ "$v" = null ] && v=""
  printf '%s' "$v"
}

# same characters the Android component list uses; refuse rather than quote around anything else
safe() { [[ "$1" =~ ^[A-Za-z0-9._/,\$-]*$ ]]; }

first_entry() { local l="${1%%,*}"; printf '%s' "${l//[[:space:]]/}"; }

is_deck() { [ "$1" = "$COMP" ] || [ "$1" = "$SHORT" ]; }

show() {
  local c e s t
  c=$(get secure screensaver_components)
  e=$(get secure screensaver_enabled)
  s=$(get secure screensaver_activate_on_sleep)
  t=$(get system screen_off_timeout)
  echo "screensaver_components:        ${c:-(not set, Android uses its default)}"
  echo "screensaver_enabled:           ${e:-(not set)}"
  echo "screensaver_activate_on_sleep: ${s:-(not set)}"
  if [[ "$t" =~ ^[0-9]+$ ]] && [ "$t" -ge 60000 ]; then
    echo "screen_off_timeout:            $t ($((t / 60000)) min)"
  else
    echo "screen_off_timeout:            ${t:-(not set)}"
  fi
  if is_deck "$(first_entry "$c")"; then
    echo "verdict: Visor's screensaver is the current one"
  elif [ -z "$c" ]; then
    echo "verdict: no screensaver chosen, Android uses its own default"
  else
    echo "verdict: another screensaver is current: $(first_entry "$c")"
  fi
  if [ "$e" = 0 ]; then echo "note: screensavers are switched off (screensaver_enabled is 0)"; fi
  if [ "$s" = 0 ]; then echo "note: Android will not start a screensaver when idle (screensaver_activate_on_sleep is 0)"; fi
}

if [ "$MODE" = status ]; then
  show
  echo "On the TV, Setup > LOOK > SCREENSAVER > PREVIEW SCREENSAVER shows it without waiting."
  exit 0
fi

if [ "$MODE" = on ]; then
  echo "found:"
  show | sed 's/^/  /'
  PREV=$(get secure screensaver_components)
  if [ -n "$PREV" ] && ! is_deck "$(first_entry "$PREV")"; then
    if safe "$PREV"; then
      echo "replacing another screensaver. To go back to it: adb shell settings put secure screensaver_components '$PREV'"
    else
      echo "replacing another screensaver (its name has unusual characters, see the line above)"
    fi
  fi
  echo "writing"
  sh_tv settings put secure screensaver_components "$COMP" >/dev/null
  sh_tv settings put secure screensaver_enabled 1 >/dev/null
  sh_tv settings put secure screensaver_activate_on_sleep 1 >/dev/null
  if [ -n "$MINUTES" ]; then
    sh_tv settings put system screen_off_timeout "$((MINUTES * 60000))" >/dev/null
  fi
  [ "$(get secure screensaver_components)" = "$COMP" ] || die "the TV did not keep the screensaver setting (it reads: $(get secure screensaver_components))"
  [ "$(get secure screensaver_enabled)" = 1 ] || die "the TV did not keep screensaver_enabled"
  if [ -n "$MINUTES" ]; then
    [ "$(get system screen_off_timeout)" = "$((MINUTES * 60000))" ] || die "the TV did not keep screen_off_timeout"
  fi
  echo "now:"
  show | sed 's/^/  /'
  echo "done. Try it: on the TV open Visor, Setup > LOOK > SCREENSAVER > PREVIEW SCREENSAVER. The real one starts after the idle time."
  echo "If the TV never starts it, see 'If it does not start' in docs/screensaver.md."
  exit 0
fi

# off
CUR=$(get secure screensaver_components)
if ! is_deck "$(first_entry "$CUR")"; then
  echo "Visor's screensaver is not the current one, nothing to do"
  show
  exit 0
fi
sh_tv settings delete secure screensaver_components >/dev/null
[ -z "$(get secure screensaver_components)" ] || die "the TV did not drop the setting"
echo "done. Android uses its own default screensaver again. screensaver_enabled and the idle time were left as they are."
