#!/usr/bin/env bash
# Turn Visor's PS overlay (the PS menu over other apps) on or off from a phone.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash enable-ps-overlay.sh TVIP:PORT on       turn it on
#        bash enable-ps-overlay.sh TVIP:PORT off      turn only Visor's service off
#        bash enable-ps-overlay.sh TVIP:PORT status   show what is on now
#
# Android keeps every enabled accessibility service in ONE setting,
# enabled_accessibility_services, a colon separated list. A plain "settings put" with
# only Visor's name would switch off everything else you run (a remote app, TalkBack).
# This script reads the list, adds or removes only Visor's entry, writes it back and
# reads it again to check.
#
# TVIP:PORT is on the TV under Developer options > Wireless debugging. Pair once first
# with: adb pair TVIP:PAIRPORT  (see docs/guide-termux.md).
set -euo pipefail

PKG=com.nokoding.deck
SVC=$PKG/$PKG.PsService
SHORT=$PKG/.PsService
KEY=enabled_accessibility_services

die() { echo "error: $*" >&2; exit 1; }

[ $# -eq 2 ] || die "usage: bash enable-ps-overlay.sh TVIP:PORT on|off|status"
TARGET="$1"
MODE="$2"

# host:port only, so the value cannot smuggle extra adb options
[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
case "$MODE" in on|off|status) ;; *) die "second argument must be on, off or status" ;; esac
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"

echo "connecting to $TARGET"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current (it changes after a reboot)."

sh_tv() { adb -s "$TARGET" shell "$@" | tr -d '\r'; }

sh_tv pm path "$PKG" >/dev/null 2>&1 || die "Visor is not installed on the TV"
VC=$(sh_tv dumpsys package "$PKG" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1)
if [ "$MODE" = on ] && [ -n "$VC" ] && [ "$VC" -lt 7 ]; then
  die "Visor on the TV is too old (version code $VC). Install visor-launcher-v2.19-beta.apk or newer first."
fi

read_list() {
  local v
  v=$(sh_tv settings get secure "$KEY")
  [ "$v" = null ] && v=""
  printf '%s' "$v"
}

# keep only real entries, drop blanks, "null" and duplicates, keep the order
clean() {
  local IFS=':' out="" e
  for e in $1; do
    e="${e//[[:space:]]/}"
    [ -z "$e" ] && continue
    [ "$e" = null ] && continue
    case ":$out:" in *":$e:"*) continue ;; esac
    out="${out:+$out:}$e"
  done
  printf '%s' "$out"
}

without_deck() {
  local IFS=':' out="" e
  for e in $1; do
    [ "$e" = "$SVC" ] && continue
    [ "$e" = "$SHORT" ] && continue
    out="${out:+$out:}$e"
  done
  printf '%s' "$out"
}

has_deck() { case ":$1:" in *":$SVC:"*|*":$SHORT:"*) return 0 ;; esac; return 1; }

# same characters Visor's own PsSetup.safeValue accepts; refuse rather than quote around
safe() { [[ "$1" =~ ^[A-Za-z0-9._/:\$-]*$ ]]; }

put_list() {
  safe "$1" || die "the current list has unexpected characters, not touching it: $1"
  # single quotes so a \$ in a class name stays literal on the TV's shell
  adb -s "$TARGET" shell "settings put secure $KEY '$1'"
}

CUR=$(clean "$(read_list)")
A11Y=$(sh_tv settings get secure accessibility_enabled)

if [ "$MODE" = status ]; then
  echo "enabled services: ${CUR:-(none)}"
  echo "accessibility_enabled: $A11Y"
  if has_deck "$CUR"; then echo "Visor PS overlay: listed"; else echo "Visor PS overlay: not enabled"; fi
  echo "Setup > PAD > PS OVERLAY on the TV shows whether the service is really running."
  exit 0
fi

if [ "$MODE" = on ]; then
  if has_deck "$CUR"; then
    echo "Visor is already listed, keeping the list as it is"
    NEW="$CUR"
  else
    NEW="${CUR:+$CUR:}$SVC"
    echo "writing: $NEW"
    put_list "$NEW"
  fi
  sh_tv settings put secure accessibility_enabled 1 >/dev/null
  CHECK=$(clean "$(read_list)")
  [ "$CHECK" = "$NEW" ] || die "the TV did not keep the new list (it reads: ${CHECK:-empty})"
  echo "done. Open Visor, Setup > PAD > PS OVERLAY should say ON. Press PS once on the home screen to check the box delivers it."
  exit 0
fi

# off
if ! has_deck "$CUR"; then
  echo "Visor is not listed, nothing to do"
  exit 0
fi
NEW=$(without_deck "$CUR")
if [ -z "$NEW" ]; then
  echo "Visor was the only service, removing the setting"
  sh_tv settings delete secure "$KEY" >/dev/null
  sh_tv settings put secure accessibility_enabled 0 >/dev/null
else
  echo "keeping the others: $NEW"
  put_list "$NEW"
fi
CHECK=$(clean "$(read_list)")
[ "$CHECK" = "$NEW" ] || die "the TV did not keep the new list (it reads: ${CHECK:-empty})"
echo "done. Visor's PS overlay is off. Other services were left alone."
