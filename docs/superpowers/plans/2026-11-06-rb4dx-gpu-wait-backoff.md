# GPU Wait Backoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce RB4DX's repeated GPU wait busy polling without weakening emulated GPU synchronization.

**Architecture:** A source-only diagnostic patch tracks coroutine yields during prolonged `WAIT_REG_MEM` and lets the GPU dispatcher wait briefly on its existing condition variable only when exactly one pending submit is blocked. The original wait remains authoritative.

**Tech Stack:** C++20, shadPS4 ARM64 native runtime, GitHub Actions Android CI, Python/C++ unit tests.

**Spec:** `docs/superpowers/specs/2026-11-06-rb4dx-gpu-wait-backoff-design.md`

## Global Constraints

- Do not change the guest value, expected reference, comparison, or signal paths.
- Do not back off if any other GPU work could advance the queue.
- Preserve working v0.12.18 as a comparison baseline.

## Review Focus

- Zero, one, and multiple queued submissions: backoff only for one.
- Pending commands and non-wait yields: never back off.
- Short waits: avoid a sleep until a threshold of failed comparisons.
- New submitted work: condition-variable signal interrupts bounded wait.
- Metrics must report resolution as distinct from persistent blocking.

---

### Task 1: Host-testable eligibility policy and native patch

**Files:** Create `tests/gpu_wait_backoff_policy.cpp`, `native/patches/rb4dx_gpu_wait_backoff.h`, `native/patches/rb4dx-gpu-wait-backoff.patch`. Modify `tests/run.sh`.

**Interfaces:** `AmdGpu::Rb4dx::ShouldBackoffGpuWait(bool yielded, uint64_t polls, unsigned int submits, unsigned int commands)` returns true only for a prolonged memory-wait yield with one submitted task and zero commands.

- [ ] Write and run a failing host C++ test for the positive case and each exclusion.
- [ ] Add minimal production eligibility header; confirm test passes.
- [ ] Create GPU coroutine patch using that header, timed `submit_cv.wait_for` and `RB4DX_WAIT_RESOLVED` timing output.
- [ ] Confirm patch applies after existing `rb4dx-gpu-wait-trace.patch` and run entire test suite.

### Task 2: Native build and Android handoff

**Files:** Modify `.github/workflows/native-runtime.yml`, `.github/workflows/android.yml`, `app/build.gradle.kts`, corresponding pinning tests.

- [ ] Add a distinct native runtime source patch and cache key under a new feature branch.
- [ ] Publish branch and verify native CI build; don't substitute the old binary.
- [ ] Pin Android to the new successful native artifact, update version, and build APK.
- [ ] Report measured build state and request 60-second side-by-side phone probe before claiming better runtime performance.
