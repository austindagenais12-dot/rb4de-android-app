# Extended Startup Diagnostic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe, stoppable 60/120-second Rock Band 4 startup diagnostic alongside the existing 18-second check.

**Architecture:** A pure Java duration policy and synchronized cancellation token feed the existing `GameBootProbe`. Native listener polling uses a per-server cancellation flag and deadline so Stop works even before a guest connection. The Activity supplies duration selection and a Stop button.

**Tech Stack:** Android Java 17, C11 JNI, project Bash/Python/JVM/C tests, GitHub Actions Gradle build.

**Spec:** `docs/superpowers/specs/2026-11-05-extended-startup-design.md`

## Global Constraints

- Preserve prior APK and its user data; this build upgrades only the existing `.linuxvk.turnip.inputbridge` package.
- Existing pin for native runtime `42119601182`, SHA `18bbb0bc0c914f86816513cf4d10b99178984524`; helper APK `42126146668` unchanged.
- Do not claim live game video when only diagnostics/retained image are present.

## Review Focus

- Stop pressed before the socket client connects must return promptly.
- Stop pressed before process starts must prevent it persisting.
- A test that times out must show `timed_out=true` and `stopped_by_user=false`.
- Multiple Stop taps must be idempotent.
- A 120-second test must not silently disconnect controller input at 60 seconds.

---

### Task 1: Duration policy and cooperative stop

**Files:** Create `GameProbeOptions.java`, `ProbeStopToken.java` and tests; modify `tests/run.sh`.

**Interfaces:** `GameProbeOptions.allowedDurationMs(int)`; `ProbeStopToken.onStop(Runnable)`, `requestStop()`, `isStopped()`.

- [ ] Write tests for 18000, 60000, 120000, rejected out-of-range durations and token registration before/after Stop, duplicate Stop.
- [ ] Run new JVM tests and observe missing-class failure.
- [ ] Implement small pure Java classes.
- [ ] Run `bash tests/run.sh` green.
- [ ] Commit.

### Task 2: Native cancelable socket listener

**Files:** Create `rb4dx_socket_cancel.c/h`, `tests/SocketCancelTest.c`; modify `native_runtime_socket.c`, `CMakeLists.txt`, `NativeRuntimeSocket.java`, `tests/run.sh`.

**Interfaces:** `rb4dx_socket_cancel_reset(fd)`, `rb4dx_socket_cancel_request(fd)`, `rb4dx_socket_cancelled(fd)`; JNI `cancelSession(fd)`.

- [ ] Write C tests for per-server cancellation, reset and idempotent requests, and Python integration checks for monotonic bounded listener and 120-second upper bound.
- [ ] Run the tests and observe failure.
- [ ] Implement cancellable 250ms poll loops under one monotonic deadline and JNI cancellation bridge.
- [ ] Run `bash tests/run.sh` green.
- [ ] Commit.

### Task 3: Probe lifecycle and UI controls

**Files:** Modify `GameBootProbe.java`, `MainActivity.java`; create `tests/test_extended_probe_wiring.py`; modify `tests/run.sh`.

**Interfaces:** `GameBootProbe.run(..., int timeoutMs, ProbeStopToken stop)`; UI duration choices and Stop button.

- [ ] Write wiring tests for report fields, process/socket stop registration, 18/60/120 UI, lifecycle cancellation, and honest video disclaimer.
- [ ] Run the tests and observe failure.
- [ ] Implement session timeout, user stop diagnostics and safe UI lifecycle.
- [ ] Run `bash tests/run.sh` green.
- [ ] Commit.

### Task 4: Build identity and CI

**Files:** Modify `app/build.gradle.kts`, `.github/workflows/android.yml`, version assertions in historical tests.

**Interfaces:** v0.12.16, code 22, `.linuxvk.turnip.inputbridge` retained; `feature/v01216-extended-startup` CI trigger.

- [ ] Write tests for new version/trigger and pinned assets; observe failure.
- [ ] Update package and CI checks without changing package suffix or runtime/helper sources.
- [ ] Run `bash tests/run.sh` green, commit.
- [ ] Build an Android APK through CI if connector/tools permit; verify artifact before delivery.
