#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
# Build the pinned independent embedded X11 engine for Android ARM64.
# This does not start a game or claim that an Android Surface is connected.
set -euo pipefail

source_root="${1:?usage: build-rb4dx-x11.sh <magicdesk-x11-checkout> <output-dir>}"
output_root="${2:?usage: build-rb4dx-x11.sh <magicdesk-x11-checkout> <output-dir>}"

expected_revision=702b1736ca2d32de2932f6be72c6b7506a0c0da5
actual_revision="$(git -C "$source_root" rev-parse HEAD)"
if [[ "$actual_revision" != "$expected_revision" ]]; then
  echo "X11 source revision mismatch: $actual_revision" >&2
  exit 1
fi

if [[ ! -f "$source_root/lorie/src/main/cpp/lorie/embedded.h" ]]; then
  echo 'Embedded X11 API missing; initialize pinned git submodules' >&2
  exit 1
fi

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the installed Android NDK path}"
cmake -S "$source_root/lorie/src/main/cpp" \
  -B "$output_root/build" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-34 \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DLORIE_BUILD_SMOKE=ON

cmake --build "$output_root/build" --target lorie-smoke --parallel 4

smoke="$output_root/build/lorie-smoke"
if [[ ! -s "$smoke" ]]; then
  echo 'Native ARM64 X11 smoke binary is missing' >&2
  exit 1
fi
if ! readelf -h "$smoke" | grep -Eq 'Machine:.*AArch64'; then
  echo 'Unexpected X11 smoke binary architecture' >&2
  exit 1
fi

mkdir -p "$output_root/stage"
cp "$smoke" "$output_root/stage/lorie-smoke-arm64"
cp "$source_root/LICENSE" "$output_root/stage/X11-LICENSE-GPL3.txt"
cp "$source_root/lorie/src/main/cpp/lorie/embedded.h" "$output_root/stage/embedded.h"
printf 'upstream=https://github.com/mekhontsev/magicdesk-x11\nrevision=%s\narch=arm64-v8a\nandroid_api=34\nstatus=compile_only_no_android_surface\n' \
  "$expected_revision" > "$output_root/stage/BUILD-METADATA.txt"
echo "RB4DX_X11_ARM64_BUILD_PASS commit=$expected_revision"
echo 'NOTE: compilation confirms neither an X11 client connection nor rendering to Android.'
