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
)
if command -v javac >/dev/null 2>&1; then
  javac -d "$build_dir" "${sources[@]}"
else
  java --module jdk.compiler/com.sun.tools.javac.Main -d "$build_dir" "${sources[@]}"
fi
java -cp "$build_dir" InstallerSmokeTest
java -cp "$build_dir" dev.rb4dx.standalone.StartupLogStoreTest
java -cp "$build_dir" dev.rb4dx.standalone.NativeCrashSummaryTest
c++ -std=c++20 -Wall -Wextra -Werror \
  -I "$project_dir/native/patches" \
  "$project_dir/tests/VulkanDispatchGuardTest.cpp" \
  -o "$build_dir/VulkanDispatchGuardTest"
"$build_dir/VulkanDispatchGuardTest"
echo "PASS: optional Vulkan extension requires a valid dispatcher entrypoint"
python3 "$project_dir/tests/test_runtime_verifier.py"
