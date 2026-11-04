#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
# Build the *public* ARM64 shadPS4/FEX Linux runtime without using Bachata's APK.
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <shadps4-arm64-source-dir> <output-dir>" >&2
  exit 2
fi

core_dir="$(cd -- "$1" && pwd -P)"
output_dir="$2"
mkdir -p -- "$output_dir"
output_dir="$(cd -- "$output_dir" && pwd -P)"

if [[ ! -f "$core_dir/runtime/scripts/build-shadps4-arm64.sh" ]]; then
  echo "Not a complete shadps4-arm64 source checkout: $core_dir" >&2
  exit 1
fi
if [[ ! -f "$core_dir/runtime/locks/components.lock.json" ]]; then
  echo "Missing upstream pinned component manifest" >&2
  exit 1
fi

echo "Building open-source ARM64 shadPS4 + FEXCore"
echo "Core commit: $(git -C "$core_dir" rev-parse HEAD)"

# These are upstream-maintained build scripts. They write only to their
# checkout's runtime/build and runtime/sources locations.
bash "$core_dir/runtime/scripts/build-shadps4-arm64.sh"
bash "$core_dir/runtime/scripts/vendor-vortek.sh"
bash "$core_dir/runtime/scripts/build-vortek-client.sh"
node "$core_dir/runtime/scripts/stage-debian-runtime.mjs"
node "$core_dir/runtime/scripts/package-runtime.mjs" "$output_dir"

python3 "$(dirname "${BASH_SOURCE[0]}")/verify-native-runtime.py" \
  "$output_dir/runtime.zip" "$output_dir/manifest.json"

echo "Verified backend package: $output_dir/runtime.zip"
echo "This is a Linux ARM64 runtime bundle, NOT an installable Android APK."
