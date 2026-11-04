#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_dir="$project_dir/tests/build"
mkdir -p "$build_dir"
sources=(
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/DeluxeInstaller.java"
  "$project_dir/tests/InstallerSmokeTest.java"
)
if command -v javac >/dev/null 2>&1; then
  javac -d "$build_dir" "${sources[@]}"
else
  java --module jdk.compiler/com.sun.tools.javac.Main -d "$build_dir" "${sources[@]}"
fi
java -cp "$build_dir" InstallerSmokeTest
python3 "$project_dir/tests/test_runtime_verifier.py"
