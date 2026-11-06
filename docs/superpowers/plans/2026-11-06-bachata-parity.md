# Bachata Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a reversible RB4DX build with Bachata-compatible graphics-driver options, persistent shader cache, true-frame diagnostics, audio, input tracing, and a full-screen Play session.

**Architecture:** Extend the existing Android Java + ARM64 JNI project around `GameBootProbe` and `VortekRendererComponent`; preserve the original timed probe. Keep pure validation logic separate for JVM/C unit tests and install two pinned driver variants with verified hashes in CI.

**Tech Stack:** Java/Android, JNI C, CMake, GitHub Actions, Mesa Turnip, shadPS4 Bachata audio protocol.

**Spec:** `docs/superpowers/specs/2026-11-06-bachata-parity-design.md`

## Global Constraints

- Preserve the existing v0.12.22 diagnostic path and uncommitted feature-branch changes.
- Do not expose another app's files or require root.
- Validate the official APK's SHA-256 before extracting the Android/Bionic Turnip 26.3.0-R5 driver.
- Do not overwrite the pinned 27.0.0 driver. Don't bundle glibc-only driver into Android nativeLibraryDir.
- No live CPU read of an in-use GPU buffer; keep audio frames and logs bounded.

## Review Focus

1. Audio socket path exceeds 107 bytes or is reused by another session: fail with an actionable diagnostic, never trample it.
2. Short PCM writes or malformed packet length: cleanly discard/stop without spinning or crashing audio service.
3. Surface is destroyed or Android Activity stops: release emulator, X11, Vortek and audio within bounded time.
4. Invalid game selection: keep Play disabled and run existing preflight, never launch arbitrary eboot.
5. Driver load failure: report selected version and retain a working fallback, never claim an unavailable Turnip was used.

---

### Task 1: Compatibility Driver and Persistent Shader Cache

**Files:** `.github/workflows/android.yml`, `app/src/main/java/dev/rb4dx/standalone/MainActivity.java`, `GameBootProbe.java`, `ShaderCachePaths.java`, `tests/ShaderCachePathsTest.java`, `tests/run.sh`.

**Interfaces:** `ShaderCachePaths.forTitle(Path root, String title)` returns a persistent directory; `MainActivity` preference `gpu_driver_choice` controls `GameBootProbe` native path.

- [ ] Add a failing JVM shader-cache-path test and a CI packaging test for both driver variants.
- [ ] Run tests; confirm missing implementation and driver package fail.
- [ ] Add the path helper and child/Android Mesa env, driver-choice UI and compatible pinned driver extraction with checksum.
- [ ] Run focused tests and full `tests/run.sh`; commit if safe.

### Task 2: Pixel and Input Diagnostics

**Files:** `FramePixelStats.java`, `VortekRendererComponent.java`, `InputFramePump.java`, corresponding JVM tests and `tests/run.sh`.

**Interfaces:** `FramePixelStats.report(int[] pixels)` produces a bounded verdict; `InputFramePump.report()` includes button-mask transitions.

- [ ] Add failing tests for black/visible/transparent pixels and exact button-transition logs.
- [ ] Run tests to observe RED.
- [ ] Add safe stopped-producer software-bitmap pixel sampling and bounded button transition history.
- [ ] Run focused tests and full suite; commit if safe.

### Task 3: Native Audio Bridge

**Files:** `AudioProtocol.java`, `AudioSocketBridge.java`, `native_audio_socket.c`, `CMakeLists.txt`, `GameBootProbe.java`, `tests/AudioProtocolTest.java`, C socket tests, `tests/run.sh`.

**Interfaces:** `AudioSocketBridge.start(Path)` starts an AF_UNIX PCM listener; `.report()` includes connection and PCM bytes; `.close()` is idempotent.

- [ ] Add failing pure-JVM protocol tests and pure-C bind/accept/close tests.
- [ ] Run them to observe RED.
- [ ] Implement bounded wire framing (code=4 prepare, code=5 PCM), JNI sockets, Android AudioTrack backend and lifecycle wiring.
- [ ] Run focused and full tests; commit if safe.

### Task 4: Full-Screen Play and Bounded Log Capture

**Files:** `GamePlayerActivity.java`, `MainActivity.java`, `GameBootProbe.java`, `BoundedLogCapture.java`, manifest, JVM tests, `tests/run.sh`.

**Interfaces:** `GameBootProbe.play(..., ProbeStopToken)` launches until stopped; `BoundedLogCapture` retains bounded head/tail without blocking child stdout indefinitely.

- [ ] Add failing pure-JVM retention tests and Play-session option tests.
- [ ] Observe RED; implement foreground landscape Activity, surface handling, touch overlay, gamepad, explicit Stop and log cleanup.
- [ ] Run focused/full tests, static Android packaging checks, and CI APK build where available.
- [ ] Report any on-device-only verification gaps; commit if safe.

## Integration checks

- Verify the original diagnostic remains 18/60/120 seconds and preserves exact native runtime pin.
- Verify driver, cache, pixel verdict, input transitions and audio activity appear in reports.
- Check no silent success claim when mobile graphics/audio remains unverified.
