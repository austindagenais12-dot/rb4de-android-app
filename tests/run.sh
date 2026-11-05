#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_dir="$project_dir/tests/build"
mkdir -p "$build_dir"
sources=(
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/DeluxeInstaller.java"
  "$project_dir/tests/InstallerSmokeTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/StartupLogStore.java"
  "$project_dir/tests/StartupLogStoreTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/NativeCrashSummary.java"
  "$project_dir/tests/NativeCrashSummaryTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/GpuWaitSummary.java"
  "$project_dir/tests/GpuWaitSummaryTest.java"
  "$project_dir/app/src/main/java/com/winlator/xenvironment/components/FramePreviewTrace.java"
  "$project_dir/tests/FramePreviewTraceTest.java"
)
if command -v javac >/dev/null 2>&1; then
  javac -d "$build_dir" "${sources[@]}"
else
  java --module jdk.compiler/com.sun.tools.javac.Main -d "$build_dir" "${sources[@]}"
fi
java -cp "$build_dir" InstallerSmokeTest
java -cp "$build_dir" dev.rb4dx.standalone.StartupLogStoreTest
java -cp "$build_dir" dev.rb4dx.standalone.NativeCrashSummaryTest
java -cp "$build_dir" dev.rb4dx.standalone.GpuWaitSummaryTest
java -cp "$build_dir" com.winlator.xenvironment.components.FramePreviewTraceTest
c++ -std=c++20 -Wall -Wextra -Werror \
  -I "$project_dir/native/patches" \
  "$project_dir/tests/VulkanDispatchGuardTest.cpp" \
  -o "$build_dir/VulkanDispatchGuardTest"
"$build_dir/VulkanDispatchGuardTest"
echo "PASS: optional Vulkan extension requires a valid dispatcher entrypoint"
cc -std=c11 -Wall -Wextra -Werror \
  -I "$project_dir/native/vortek" \
  "$project_dir/tests/VortekFrameRingTest.c" \
  "$project_dir/native/vortek/rb4dx_frame_ring.c" \
  -o "$build_dir/VortekFrameRingTest"
"$build_dir/VortekFrameRingTest"
cc -std=c11 -Wall -Wextra -Werror \
  -I "$project_dir/native/vortek" \
  "$project_dir/tests/VortekWindowImagesTest.c" \
  "$project_dir/native/vortek/rb4dx_window_images.c" \
  -o "$build_dir/VortekWindowImagesTest"
"$build_dir/VortekWindowImagesTest"
python3 "$project_dir/tests/test_runtime_verifier.py"
python3 "$project_dir/tests/test_gpu_wait_trace_package.py"
