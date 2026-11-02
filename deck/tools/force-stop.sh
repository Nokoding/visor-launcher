#!/usr/bin/env bash
# Force stop one app on the TV from a phone. Android does not let a normal app do this, so this is
# the one route that always works: the adb shell may.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash force-stop.sh TVIP:PORT com.example.app
#
# It runs exactly one thing on the TV: am force-stop PACKAGE. The package name is checked first
# (letters, digits, dots and underscores, at least one dot) and Visor's own package is refused.
# Visor's task manager (PS menu, APPS) shows the same command for the app you pick.
#
# TVIP:PORT is on the TV under Developer options > Wireless debugging. Pair once first
# with: adb pair TVIP:PAIRPORT  (see docs/guide-termux.md). Details: docs/task-manager.md.
set -euo pipefail

SELF=com.nokoding.deck

die() { echo "error: $*" >&2; exit 1; }

[ $# -eq 2 ] || die "usage: bash force-stop.sh TVIP:PORT PACKAGE"
TARGET="$1"
PKG="$2"

[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
# letters, digits, dots, underscores; at least one dot; no leading, trailing or double dot; each part starts with a letter
[[ "$PKG" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] || die "package name looks wrong: $PKG (example com.roblox.client)"
[ ${#PKG} -le 255 ] || die "package name is too long"
[ "$PKG" != "$SELF" ] || die "that is Visor itself. Stopping it from here would only close the launcher."
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"

echo "connecting to $TARGET"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current (it changes after a reboot)."

adb -s "$TARGET" shell pm path "$PKG" >/dev/null 2>&1 || die "$PKG is not installed on the TV"
echo "stopping $PKG"
adb -s "$TARGET" shell am force-stop "$PKG"
echo "done. $PKG was force stopped."
