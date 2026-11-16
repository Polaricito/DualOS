#!/bin/sh
# libcamera_client_patched.so and libcameracompatbilityinfo_patched.so for a
# Pixel, ColorOS or LineageOS guest: the image's native camera client libraries
# with One UI's transaction codes (switcher/patches/pixel_camera_native.py, table
# host_camera.txt for One UI 9 or host_camera_a16.txt for One UI 8.0/8.5 through
# ONEOS_CAMERA_TABLE), the native twin of patch-framework-camera.sh.
# guest_prepare.sh binds them over /system/lib64 and guards.txt pins them.
# Nothing is written for a library whose tables already agree.
#
# Usage: scripts/patch-camera-native.sh <libcamera_client.so> <libcameracompatbilityinfo.so|-> <out dir>
#   pass - for the second library when the image has none (Android 16 QPR0:
#   the calls carry rotationOverride as an int, no compatibility parcelable).
#   appends the sha256 lines to <out dir>/guards.txt when that file exists.
# make-overlays.sh calls it with the libraries dumped from the image; for a set
# whose image is gone, call it with copies pulled from the phone's stage.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
[ -f "$1" ] && [ -d "$3" ] && { [ "$2" = - ] || [ -f "$2" ]; } || { echo "usage: $0 <libcamera_client.so> <libcameracompatbilityinfo.so|-> <out dir>" >&2; exit 2; }
OUT=$(cd "$3" && pwd)
# One UI 9's table unless the caller names the host's own (make-overlays.sh
# passes One UI 8.0/8.5's host_camera_a16.txt for an SDK 36 image).
TABLE="${ONEOS_CAMERA_TABLE:-$ROOT/switcher/patches/host_camera.txt}"
PAIRS="$1:libcamera_client_patched.so"
[ "$2" = - ] || PAIRS="$PAIRS $2:libcameracompatbilityinfo_patched.so"
for pair in $PAIRS; do
    src=${pair%%:*}; name=${pair#*:}
    rm -f "$OUT/$name"
    sed -i "/ $name\$/d" "$OUT/guards.txt" 2>/dev/null || true
    # A refusal (exit 1) stops the build: inside $(...) set -e would not (0.4.31,
    # HyperOS's library was left unpatched and reported as "not needed").
    res=$(python3 -I "$ROOT/switcher/patches/pixel_camera_native.py" "$TABLE" "$src" "$OUT/$name") ||
        { echo "ERROR: $name: pixel_camera_native.py refused $src" >&2; exit 1; }
    if [ "$res" = changed ]; then
        chmod 644 "$OUT/$name"
        [ -f "$OUT/guards.txt" ] && (cd "$OUT" && sha256sum "$name") >> "$OUT/guards.txt"
        echo "built $OUT/$name"
    else
        echo "$name: not needed (the guest's tables are One UI's)"
    fi
done
