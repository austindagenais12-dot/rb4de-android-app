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
- [x] Run host suite and diff checks; obtain fresh independent review and resolve findings.
- [x] Publish source branch, build APK, verify signing and embedded payload lineage.
- [x] Save artifact and exact continuation evidence with hardware verification limitation.

## Observed local verification
- Baseline production shader inspector: first format41 (UINT), repeated format39 (USCALED), requested39; shader bytes changed. Test failed on first/repeated mismatch.
- Patched production inspector: both format39; shader bytes identical; 8,000 concurrent uses, capability fallback and driver-error cases pass.
- Full host suite passes; AF_UNIX socket integration is explicitly skipped by the local sandbox and remains required in CI.
- v0.12.25 phone graphics remain broken. This candidate corrects a demonstrated bridge defect; association with the complete visual failure is still unverified.

## Final build and review
- Independent review: no Critical or Important findings. Extra temporary reviewer coverage passed all 12 unsupported formats, differing formats, mixed/null stages, immutable wrapper fields. Minor follow-up: retain differing-format coverage in the permanent harness.
- APK source GitHub commit176a661add0ea9e518f7aaab5478f53ddfe182a8, treeb54f3648914470c62fd87dba2dbe6c57128f4878. CI run42298906999 succeeded.
- APK SHA256 47c96bfd00a526f3137d369fa3691893fa8b9c87c7c92fc2a711027a47bc09a7; version32 / 0.12.26-native-shaders; v2 signature verified and same .25 certificate.
- Runtime ZIP/manifest, both drivers and 11 native libraries identical to .25; only libvortekrenderer.so changed among native libraries. Actual .26 phone graphics unverified.

- Real AF_UNIX lifecycle integration also passed in CI (not skipped).
- Saved APK ID libfile_8be575dedd488191812c8007ea41d081; saved continuation ID libfile_1620418772c881918ee7f29c0b3752bd.
