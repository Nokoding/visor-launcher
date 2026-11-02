#!/usr/bin/env bash
# Push your own 3D controller model to the TV from a phone, for Visor's gyro test.
# Made for Termux (pkg install android-tools), works on any shell with adb.
#
# Usage: bash install-model.sh TVIP:PORT dualsense.obj
#        bash install-model.sh TVIP:PORT xbox-one.obj
#
# One Wavefront OBJ file per controller, with exactly one of those two names. Only v and f lines are
# read, faces of 3 to 8 corners, at most 24000 triangles and 24000 vertices, 1.5 MB, centimetres (a model
# bigger than 40 is taken as millimetres). See docs/gyro-models.md. A bad file is ignored by Visor, which
# then draws its built-in model. Open Visor once before the first push so the folder exists.
set -euo pipefail

PKG=com.nokoding.deck
DEST=/sdcard/Android/data/$PKG/files/models
MAX_BYTES=$((1536 * 1024))

die() { echo "error: $*" >&2; exit 1; }

[ $# -eq 2 ] || die "usage: bash install-model.sh TVIP:PORT <dualsense.obj or xbox-one.obj>"
TARGET="$1"
SRC="$2"

[[ "$TARGET" =~ ^[A-Za-z0-9.-]+:[0-9]{1,5}$ ]] || die "TVIP:PORT looks wrong: $TARGET (example 192.168.1.50:37425)"
command -v adb >/dev/null 2>&1 || die "adb not found. In Termux run: pkg install -y android-tools"
[ -f "$SRC" ] || die "not a file: $SRC"
NAME="$(basename "$SRC")"
case "$NAME" in
  dualsense.obj|xbox-one.obj) ;;
  *) die "the file must be called dualsense.obj or xbox-one.obj (it is $NAME)" ;;
esac
[ "$(wc -c < "$SRC" | tr -d ' ')" -le "$MAX_BYTES" ] || die "the file is over 1.5 MB, Visor will not read it"
VERTS="$(grep -c '^v ' "$SRC" || true)"
FACES="$(grep -c '^f ' "$SRC" || true)"
[ "$VERTS" -ge 4 ] && [ "$VERTS" -le 24000 ] || die "$VERTS vertices: Visor takes 4 to 24000"
[ "$FACES" -ge 1 ] || die "no f lines in the file"
# a rough triangle count: every f line is at least one triangle, a quad is two
TRIS="$(awk '/^f /{t += NF - 3} END{print t + 0}' "$SRC")"
[ "$TRIS" -le 24000 ] || die "$TRIS triangles: Visor takes at most 24000"

echo "connecting to $TARGET"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" get-state >/dev/null 2>&1 || die "TV not reachable. Check Wireless debugging is on and the port is current."
adb -s "$TARGET" shell pm path "$PKG" >/dev/null 2>&1 || die "Visor is not installed on the TV"
adb -s "$TARGET" shell mkdir -p "$DEST"
echo "pushing $NAME ($VERTS vertices, $TRIS triangles)"
adb -s "$TARGET" push "$SRC" "$DEST/$NAME"
echo "done. Open Setup > PAD > GYRO TEST. Visor reads the file when the test opens. If it draws the built-in model, the file was refused."
