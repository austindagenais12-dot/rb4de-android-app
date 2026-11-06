# RB4DX v0.12.22 shader compatibility implementation plan

**Spec:** `docs/superpowers/specs/2026-11-06-rb4-shader-compat-design.md`
**Branch:** `feature/v01222-shader-compat`

## Task 1: Backport shader divergence and compute wait reporting

**Files:** `native/patches/rb4dx-shader-divergence.patch`, `native/patches/rb4dx-compute-wait-resolution.patch`, `tests/test_shader_compat_package.py`.

1. Add tests requiring the exact source patch hunks and unchanged barrier/compute wait semantics. Run test; expect FAIL because patches do not exist.
2. Add minimal native patches to pinned ARM64 emulator; test; expect PASS.
3. `git apply --check` each patch against the pinned source after existing trace/backoff patches; expect success.

## Task 2: Wire native/Android CI and release metadata

**Files:** `.github/workflows/native-runtime.yml`, `.github/workflows/android.yml`, `app/build.gradle.kts`, existing package tests.

1. Extend focused packaging test requiring this branch's source patches, new native cache key, and no fallback to prior native binaries. Run; expect FAIL.
2. Add v0.12.22 branch to workflows, apply patches to native source before compile, use fresh cache key, require branch-matched native workflow artifact for APK; version bump.
3. Update historical package tests' version assertions to the current version; run focused and full `tests/run.sh`; expect PASS.

## Task 3: Evidence and handoff

1. Inspect diff for unsafe cache activation, regressions, and stale artifact paths.
2. Record build requirements and testing limitations. Do not claim working gameplay until on-device verification.

## Review focus

Verify shader opcode names exist in pinned source, native patch hunk ordering, no old ABI reuse, no change to GPU wait condition, and no risky shader warmup activation.
