# High-signal RB4DX Startup Logs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve valuable mid-run Vulkan, crash, and presentation evidence when controller polling floods the native startup log.

**Architecture:** A new streaming filtering entry point in `StartupLogStore` writes a temporary reduced log, then delegates to the existing byte-bounded atomic retention function. `GameBootProbe` exports the filtered file and honestly reports its raw source size and omitted polling counts.

**Tech Stack:** Java 17, existing Android app, plain Java smoke tests, GitHub Actions APK build.

**Spec:** `docs/superpowers/specs/2026-11-05-high-signal-startup-logs-design.md`

## Global Constraints

- Keep the APK application ID and saved app data unchanged.
- Never modify the game files or skip GPU synchronization.
- Preserve uncommon pad lines and all other emulator output.
- The user already opted out of per-build approval; continue within this scope.

## Review Focus

- Raw logs without polling must export byte-identically.
- A `joypad_reader` error unrelated to the four known calls must remain.
- The first example of each suppressed call must remain, along with suppressed counts.
- The original source log must not be modified.
- Even after filtering, oversized logs must respect the 8 MB limit.

### Task 1: Filter known repetitive poll lines

**Files:** Modify `StartupLogStore.java`; modify `StartupLogStoreTest.java`.

**Interface:** Add `retainReadableDiagnostics(File, Path, int)` returning `Retention` with raw size, saved size, truncation, and suppressed polling counts. Preserve `retainHeadAndTail` for byte-level fallback and existing tests.

- [x] Write a failing Java test using a synthetic large log with a unique Vulkan stall in the middle and repetitive known pad lines before and after it; require the stall, one sample of each call, count summary, and original source size to survive.
- [x] Run the targeted test, confirm the expected failure.
- [x] Implement a streaming filter using a temporary file next to the destination, with cleanup on success/failure; apply existing bounded retention to the filtered file.
- [x] Run targeted test and full `bash tests/run.sh`.

### Task 2: Wire exported diagnostic

**Files:** Modify `GameBootProbe.java`, optionally update existing package-wiring tests.

- [x] Call the new retention method and expose the number of suppressed polling lines in the startup report.
- [x] Verify existing JVM smoke suite; Android compile will be verified in CI after push.
- [ ] Commit and build a same-package APK, preserving the working v0.12.17 APK as a fallback.
