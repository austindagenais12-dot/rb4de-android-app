# Controller Input Diagnostic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver an isolated RB4DX v0.12.15 APK that routes real Android gamepad and touchscreen state into the existing shadPS4 input snapshot protocol.

**Architecture:** A plain-Java input model merges hardware and multitouch inputs, formats fixed-size PS4 snapshots, and is sent at a bounded interval over the existing local control socket. An Android controller mapper and a hideable custom touch panel feed the model. The startup diagnostic tracks socket deliveries separately from guest input acknowledgement.

**Tech Stack:** Android Java 17, C JNI POSIX socket, existing Gradle/GitHub Actions, local JVM tests.

**Spec:** `docs/superpowers/specs/2026-11-05-controller-input.md`.

## Global Constraints

- Keep v0.12.14 intact and preserve imported game/Deluxe data. Use an isolated package for the new experimental probe.
- Retain the pinned known-good v0.12.13 ARM64 native runtime and existing Turnip preference.
- No misleading gameplay/instrument support claims. Never write or modify game files.

## Review Focus

1. Simultaneous hardware and touch holds: releasing one source must retain the other's button.
2. D-pad hat/keys and gamepad face buttons: mapping must use native OrbisPad constants.
3. Analog neutral: sticks 128, triggers 0, always clamped 0..255.
4. Closing a touch overlay must release held controls.
5. Socket connection/close race: no invalid-FD writes or stale input across runs.

---

### Task 1: Wire-format and merged input model

**Files:** Create `app/src/main/java/dev/rb4dx/standalone/Ps4InputState.java`; create `tests/Ps4InputStateTest.java`; edit `tests/run.sh`.

**Interfaces:** `setHardwareButtons(long,boolean)`, `setHardwareAxes(int,int,int,int,int,int)`, `setTouchButtons(long)`, `setTouchSticks(int,int,int,int,boolean,boolean)`, `clearTouch()`, `reset()`, `nextFrame()`.

- [ ] Write JVM tests for native bit constants, exact wire frame, sequence and cross-source priority; run and observe expected compile failure.
- [ ] Implement model and rerun tests; commit.

### Task 2: Native transport and Android hardware input

**Files:** Edit `app/src/main/cpp/native_runtime_socket.c`, `NativeRuntimeSocket.java`, `GameBootProbe.java`, `MainActivity.java`; create `AndroidGamepadInput.java`.

**Interfaces:** `NativeRuntimeSocket.sendInputFrame(int serverFd, String frame)` returns bytes, 0 if not connected, or negative errno. `GameBootProbe.run(..., Ps4InputState input)` emits and reports bounded snapshots during launch.

- [ ] Write failing integration checks for frame routing and delivery counters.
- [ ] Implement FD-safe JNI writer and Android key/axis mapper; bridge game-start lifecycle, report non-acknowledged socket delivery; build-check and commit.

### Task 3: Touch UX, regression and deliverable

**Files:** Create `TouchControllerPanel.java`, update `MainActivity.java`, `app/build.gradle.kts`, `.github/workflows/android.yml`, package tests.

- [ ] Write failing checks for overlay multi-touch release, isolated package config and pinned runtime selection.
- [ ] Build hideable touch panel with colorful standard pad plus experimental guitar layout; add hardware status and selectable opacity; rerun entire test suite.
- [ ] Build through GitHub Actions, verify APK package and SHA, deliver direct downloadable APK, and commit.
