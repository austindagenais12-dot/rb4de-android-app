# RB4DX Preview Failure Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the optional Rock Band 4 final-frame preview explain precisely where it failed, in a separate installable v0.12.12 diagnostic APK.

**Architecture:** Keep the existing Vortek hardware-buffer preview algorithm. Add a plain Java stage recorder with a stable report schema and feed it from the already-existing checks and draw calls. Surface the recorder's report in GameBootProbe and package the diagnostic build under a new package ID with Turnip selected by default.

**Tech Stack:** Java 17, Android SurfaceView/HardwareBuffer, Gradle/Kotlin DSL, bash regression tests, GitHub Actions Android SDK/NDK.

**Spec:** `docs/superpowers/specs/2026-11-05-preview-failure-diagnostics-design.md`

## Global Constraints

- No edits to the existing v0.12.10 or v0.12.11 APK packages or game files.
- Do not change shadPS4 guest execution, Vortek frame queues, or frame presentation semantics in this build.
- Preserve `android_final_frame_preview_posted` for compatibility.
- Report only diagnostic stage names and exception class, never private paths or buffer contents.
- A successful CI build does not prove Android preview success; phone A/B tests remain necessary.

## Review Focus

- A window was never updated: report `NO_WINDOW_UPDATE` without touching an Android Surface.
- A surface is invalid: report `SURFACE_INVALID`, not generic failure.
- A native buffer or wrapped bitmap is missing: report those stages separately.
- A canvas operation throws: preserve the first exception's stage, not an exception from cleanup.
- A successful preview posts: report `POSTED` and keep the old boolean `true`.

---

### Task 1: Stage Recorder and Preview Integration

**Files:**
- Create: `app/src/main/java/com/winlator/xenvironment/components/FramePreviewTrace.java`
- Create: `tests/FramePreviewTraceTest.java`
- Modify: `tests/run.sh`
- Modify: `app/src/main/java/com/winlator/xenvironment/components/VortekRendererComponent.java`
- Modify: `app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java`

**Interfaces:**
- `FramePreviewTrace` is pure Java, with `recordAttempt(String)`, `recordSuccess(String)`, `fail(String, Throwable)`, `posted()`, and `toReportLines()`.
- `VortekRendererComponent.previewDiagnosticReport()` returns `toReportLines()` for its last preview attempt.
- GameBootProbe appends the trace immediately after `android_final_frame_preview_posted`.

- [ ] **Step 1: Write failing JVM tests** for success, missing surface/window/buffer/bitmap, first-exception preservation, and sanitized exception class in `FramePreviewTraceTest.java`; wire the test into `tests/run.sh`.
- [ ] **Step 2: Verify RED** with `bash tests/run.sh` (fails because `FramePreviewTrace` does not exist).
- [ ] **Step 3: Implement** the recorder, instrument existing preview code with no intended rendering changes, and append its report to GameBootProbe.
- [ ] **Step 4: Verify GREEN** with `bash tests/run.sh` (all tests pass).
- [ ] **Step 5: Review** diff to ensure no native code or guest execution behavior changed.
- [ ] **Step 6: Commit** as `feat: report final-frame preview failure stages`.

### Task 2: Isolated Preview Diagnostic APK

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/dev/rb4dx/standalone/MainActivity.java`
- Modify: `app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java`
- Modify: `.github/workflows/android.yml`
- Create: `tests/test_preview_diagnostic_package.py`
- Modify: `tests/run.sh`

**Interfaces:**
- Debug app ID: `dev.rb4dx.standalone.linuxvk.turnip.previewdiag`; app label: `RB4DX Preview Diagnostic`.
- Version: `0.12.12-preview-diagnostics`; first-run `gpu_turnip_enabled` default: `true` in both UI and runtime probe, while retaining the existing radio selection.
- New Git branch: `feature/v01212-preview-failure-diagnostics`, based on v0.12.11 commit `dddabdea`.

- [ ] **Step 1: Write failing package-configuration checks** for separate app ID/version/Turnip default and Android workflow branch + pinned native runtime branch.
- [ ] **Step 2: Verify RED** with `python3 tests/test_preview_diagnostic_package.py`.
- [ ] **Step 3: Update** Gradle variant, UI/runtime default preferences, and workflow trigger/native runtime lookup.
- [ ] **Step 4: Verify GREEN** with `bash tests/run.sh` (includes new package checks).
- [ ] **Step 5: Review** full diff against `dddabdea`; ensure existing app IDs are not repurposed.
- [ ] **Step 6: Commit** as `build: isolate v0.12.12 preview diagnostics`.

### Task 3: CI APK and Handoff

**Files:** none additional.

**Interfaces:** GitHub Actions uploads its debug APK artifact from the new branch.

- [ ] **Step 1: Verify** clean commits and latest full tests locally.
- [ ] **Step 2: Push** only the new branch `feature/v01212-preview-failure-diagnostics` (the user has granted standing approval for routine builds).
- [ ] **Step 3: Verify** the matching Android workflow succeeds; if it fails, inspect the job logs without updating the working branches.
- [ ] **Step 4: Download** the matching APK artifact; inspect archive integrity and package identity before offering it.
- [ ] **Step 5: Save** the APK as a durable user artifact and supply a **direct APK link**, not only GitHub.
- [ ] **Step 6: Explain** that the phone test must compare `android_preview_*` fields with/without rendering the blue frame first.
