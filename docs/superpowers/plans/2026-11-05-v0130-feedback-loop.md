# v0.12.10 Feedback-Loop Dispatch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent a null optional Vulkan feedback-loop dispatch pointer from crashing the pinned ARM64 emulator.

**Architecture:** Unit-test a small Boolean capability selector, stage the selector into the pinned source tree, and extend the existing Vulkan compatibility patch to disable feedback-loop support when the command pointer is absent. Keep native and APK builds on a separate development branch.

**Tech Stack:** C++20, Git patches, GitHub Actions, Android Gradle.

**Spec:** `docs/superpowers/specs/2026-11-05-v0130-feedback-loop-design.md`

## Global Constraints

- Keep the pinned upstream commit `be6bc2e9c60799e071dd2fafa6216e8d80ec619c`.
- Do not mutate `main`, game content, saves, or Deluxe plug-ins.
- Do not claim crash resolution without a physical S24 Ultra run.

## Review Focus

- Missing dispatch pointer must cause capability fallback, not a null call.
- Advertised extension with valid function pointer must keep the feature.
- Unadvertised extension with valid pointer must stay disabled.
- Native cache must not reuse the old emulator core.
- APK must contain the feature-branch native runtime, not a successful old `main` runtime.

---

### Task 1: Optional-command capability helper

**Files:** `tests/VulkanDispatchGuardTest.cpp`, `native/patches/rb4dx_dispatch_guard.h`, `tests/run.sh`

**Interfaces:** `RB4DX::HasUsableOptionalDispatch(bool extension_advertised, FunctionPointer command)` produces a Boolean.

- [ ] Write C++ tests covering present/present, present/missing, absent/present, absent/missing.
- [ ] Run test and observe missing-header failure.
- [ ] Implement the minimal helper header and wire the test runner.
- [ ] Run focused test and full `bash tests/run.sh`; require exit zero.
- [ ] Commit task files.

### Task 2: Pinned emulator guard and native build

**Files:** `native/patches/rb4dx-vulkan-vertex-dispatch-fallback.patch`, `.github/workflows/native-runtime.yml`

**Interfaces:** The Vulkan Instance reads its command pointer after device dispatcher initialization. The staging workflow copies the header before applying the patch.

- [ ] Extend the patch with the helper include and feedback-loop fallback after existing vertex guards.
- [ ] Add branch trigger, header staging, and new core-cache key to native workflow.
- [ ] Run `git apply --check` against pinned upstream files and full `bash tests/run.sh`.
- [ ] Commit and create a new isolated GitHub feature branch; await successful native runtime artifact.

### Task 3: Versioned Android diagnostic APK

**Files:** `app/build.gradle.kts`, `.github/workflows/android.yml`

- [ ] Increment Android version and accept feature-branch pushes only after native success.
- [ ] Select a successful native run from the matching branch for this test build.
- [ ] Run full suite and diff checks; commit and update feature branch.
- [ ] Verify CI APK integrity, app identity, signature, and native provenance.
- [ ] Deliver APK, requesting a new device startup log before claiming the game launches.
