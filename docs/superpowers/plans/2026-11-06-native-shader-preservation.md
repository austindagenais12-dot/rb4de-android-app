# Native Shader Preservation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** correct a reproducible shader/vertex-format inconsistency and deliver an update-compatible candidate without claiming unverified phone results.
**Architecture:** retain pinned Vortek wire protocol; select immutable native shader modules for shadPS4 when capability checks permit. Keep the existing compatibility path otherwise.
**Tech Stack:** C11, Vulkan, Java17, Android, GitHub Actions.
**Spec:** docs/superpowers/specs/2026-11-06-native-shader-preservation.md

## Global Constraints
- Preserve native runtime run42241434598, drivers, audio/input and display handoff.
- Package dev.rb4dx.standalone.linuxvk.turnip.inputbridge; versionCode32; versionName0.12.26-native-shaders.
- No game/save mutations. Host tests cannot verify the user's rendered game.

## Review Focus
- Shared shaders with differing/repeated vertex layouts preserve native inputs.
- Unsupported scaled formats and missing clip-distance support retain fallback.
- Native driver module failures propagate without exposing an invalid handle.
- Concurrent pipeline construction does not mutate the shared shader.
- Play diagnostics report the actual register-capture configuration.

## Task 1: Preserve native shaders
Files: native/patches/rb4dx-vortek-native-shaders.patch; tests/VortekNativeShadersTest.c; tests/test_vortek_native_shaders.py; tests/vortek_shader_host/*.
- [x] Reproduce format inconsistency using the pinned production shader inspector and captured driver inputs.
- [x] Add guarded native shader creation and immutable handle unwrapping.
- [x] Verify repeated/concurrent use, unsupported capabilities and driver failure.

## Task 2: Integrate, review and build
Files: .github/workflows/android.yml; app/build.gradle.kts; GameBootProbe.java; tests/run.sh and package expectations.
- [x] Apply patch and focused tests in CI, bump version, correct diagnostic field.
- [ ] Run host suite and diff checks; obtain fresh independent review and resolve findings.
- [ ] Publish source branch, build APK, verify signing and embedded payload lineage.
- [ ] Save artifact and exact continuation evidence with hardware verification limitation.

## Observed local verification
- Baseline production shader inspector: first format41 (UINT), repeated format39 (USCALED), requested39; shader bytes changed. Test failed on first/repeated mismatch.
- Patched production inspector: both format39; shader bytes identical; 8,000 concurrent uses, capability fallback and driver-error cases pass.
- Full host suite passes; AF_UNIX socket integration is explicitly skipped by the local sandbox and remains required in CI.
- v0.12.25 phone graphics remain broken. This candidate corrects a demonstrated bridge defect; association with the complete visual failure is still unverified.
