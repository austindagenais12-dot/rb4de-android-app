# RB4DX Vulkan Frame Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Remove mismatched Vulkan/Android image formats and unintended alpha blending, and retain useful Play logs when stopped.

**Architecture:** Keep the pinned shadPS4/Vortek core, map each requested swapchain format to an Android hardware buffer, use opaque SurfaceControl presentation, and make intentional process shutdown a normal end-of-stream case.

**Tech Stack:** Android NDK C11, Java 17, Android API 36 SurfaceControl, existing shell/JVM/C tests.

**Spec:** `docs/superpowers/specs/2026-11-06-rb4dx-graphics-correctness-design.md`

## Global Constraints

- Preserve pinned emulator core, audio bridge and shader cache.
- No change to game content and no automatic deletion of game data.
- Tests before production changes; Android hardware device verification separately.

## Review Focus

- BGRA request maps to Android format 5; RGBA request maps to 1.
- Unsupported BGRA allocation fails visibly instead of falling back to mismatched RGBA.
- Stop closes process stream mid-read and still saves log tail.
- Unexpected stream errors still propagate when no Stop was requested.
- SurfaceControl alpha is ignored for guest opaque swapchain frames.

### Task 1: Stop-safe bounded Play logs

**Files:** `tests/ProcessLogDrainTest.java`, `app/src/main/java/dev/rb4dx/standalone/ProcessLogDrain.java`, `GameBootProbe.java`

- [ ] Add a regression test for an input stream that throws `Stream Closed` after bytes are read; require bounded tail on Stop.
- [ ] Run tests to demonstrate the failing case.
- [ ] Add a stop-aware capture overload and call it with the probe's Stop flag.
- [ ] Verify the new test and the complete suite.
- [ ] Commit the task.

### Task 2: Correct Android surface pixel formats and alpha

**Files:** `tests/VortekAhbFormatTest.c`, `native/vortek/rb4dx_ahb_format.h`, `native/vortek/vortek_bridge.c`, `native/vortek/rb4dx_surface_control_compat.h`, `tests/run.sh`

- [ ] Add a test for RGBA/BGRA format selection; run it failing before adding the header.
- [ ] Implement selected format into AHardwareBuffer creation and log format allocation failures.
- [ ] Add opaque SurfaceControl composition for the existing guest-opaque contract.
- [ ] Run the full suite and cross-compile the native bridge in CI.
- [ ] Commit the task.

### Task 3: Build and device handoff

**Files:** `.github/workflows/android.yml`, `app/build.gradle.kts`, CI artifact

- [ ] Advance version and packaging assertions while reusing the verified native runtime.
- [ ] Verify Android workflow and build, retrieve the signed APK.
- [ ] Provide device test instructions, evidence-based expectations, and remaining known limitations.
- [ ] Commit the task.
