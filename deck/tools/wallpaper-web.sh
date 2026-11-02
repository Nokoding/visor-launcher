#!/usr/bin/env bash
# Send a wallpaper (a video, a GIF, a picture or a pack zip) from your phone's browser to the TV.
# Made for Termux: pkg install -y python android-tools
#
# Usage: bash wallpaper-web.sh TVIP:PORT [LOCALPORT]
#
# It starts a tiny web page on YOUR PHONE only (127.0.0.1, never reachable from the network). Open the
# address it prints in your phone's browser, pick a file, and the script pushes it to the TV with adb and
# tells Visor to reload. Why not a web page on the TV: that would need the INTERNET permission in Visor, which
# Visor does not have and should not get. See docs/wallpapers.md.
set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

[ $# -ge 1 ] && [ $# -le 2 ] || die "usage: bash wallpaper-web.sh TVIP:PORT [LOCALPORT]"
TARGET="$1"
LPORT="${2:-8765}"
[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
[[ "$LPORT" =~ ^[0-9]{4,5}$ ]] || die "the local port must be 4 or 5 digits: $LPORT"
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"
command -v python3 >/dev/null 2>&1 || die "python3 not found. In Termux run: pkg install -y python"

HERE="$(cd "$(dirname "$0")" && pwd)"
exec python3 "$HERE/wallpaper-web.py" "$TARGET" "$LPORT"
