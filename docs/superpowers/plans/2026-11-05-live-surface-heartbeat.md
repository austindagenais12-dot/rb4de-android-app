# RB4DX Live Surface Heartbeat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a separate v0.12.14 diagnostic APK that verifies Vortek's live present callbacks can draw a status heartbeat to Android's `SurfaceView`.

**Architecture:** A small pure-Java `LiveHeartbeatTracker` provides rate limiting and counters. `VortekRendererComponent.updateWindowContent` invokes it and, when due, paints a clearly labeled Android Canvas heartbeat with no reads of live GPU AHBs. `GameBootProbe` exports counters in its report; a new experimental package ID preserves all older builds.

**Tech Stack:** Android Java 17, Android `Surface`, `Canvas`, Vortek JNI, Gradle, shell/JVM tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-05-live-surface-heartbeat-design.md`

## Global Constraints

- Never sample shared GPU game buffers during an active Vulkan producer.
- Draw heartbeat no more frequently than every 750 milliseconds.
- A heartbeat is NOT live game video; retain `android_surface_attached=false`.
- Preserve the v0.12.13 native runtime, 18-second timeout, game preflight, Deluxe disabled, and post-stop still-frame preview.
- v0.12.14 must use an isolated app package and display label.

## Review Focus

- Invalid Surface: tracker records failure and JNI callback does not throw.
- Frequent callbacks: repeated events within 750ms cause no extra drawing.
- Surface disappearing during a draw: exception is caught and counted.
- No Vortek callbacks: zero heartbeat posted and no false success.
- Post-probe final-frame preview: existing path still runs after the Vulkan server stops.

---

### Task 1: Bounded Diagnostic State

**Files:** Create `app/src/main/java/com/winlator/xenvironment/components/LiveHeartbeatTracker.java`; create `tests/LiveHeartbeatTrackerTest.java`; modify `tests/run.sh`.

**Interfaces:** `boolean tryBegin(long uptimeMs)`, `void recordPosted()`, `void recordFailed(String reason)`, `int attempted()`, `int posted()`, `int failed()`, `String lastFailure()`.

- [ ] Write tests covering initial draw, 749/750ms threshold, failed draw counting, no-success-without-post.
- [ ] Run tests and confirm they fail because `LiveHeartbeatTracker` is absent.
- [ ] Implement tracker with synchronized counter methods and throttle.
- [ ] Run targeted tests, then all existing tests.
- [ ] Commit the tested task.

### Task 2: Android Callback-To-Surface Heartbeat

**Files:** Modify `VortekRendererComponent.java`, `GameBootProbe.java`, `MainActivity.java`; create `tests/test_live_heartbeat_wiring.py`.

**Interfaces:** `VortekRendererComponent.heartbeatReport()` returns `android_live_transport_heartbeat_*` fields; `updateWindowContent(int windowId, int imageIndex)` remains JNI compatible.

- [ ] Write source-wiring tests proving the heartbeat is triggered by native present callback, uses an Android canvas, reports status, and never reads live AHBs.
- [ ] Run tests and confirm missing capability fails.
- [ ] Add throttled canvas draw; catch all expected display errors; keep existing final-frame preview.
- [ ] Export counters from GameBootProbe and mark the heartbeat diagnostic in MainActivity copy.
- [ ] Run targeted wiring tests and full test suite.
- [ ] Commit the tested task.

### Task 3: Isolated APK and CI Artifact

**Files:** Modify `app/build.gradle.kts`, `.github/workflows/android.yml`; create `tests/test_live_heartbeat_package.py`; modify `tests/run.sh`.

**Interfaces:** `versionCode=20`, `versionName=0.12.14-live-heartbeat`, `applicationIdSuffix=.linuxvk.turnip.liveheartbeat`, branch `feature/v01214-live-surface-heartbeat`; reuse native branch `feature/v01213-gpu-wait-trace`.

- [ ] Add packaging tests for version, isolated package ID, CI branch and runtime pin.
- [ ] Run test and confirm fail before applying the packaging change.
- [ ] Update Gradle/CI verification and run tests.
- [ ] Publish the branch and verify GitHub Actions actually produced a signed APK.
- [ ] Download and verify the artifact; provide the APK file directly.
