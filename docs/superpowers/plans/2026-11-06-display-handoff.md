# RB4DX Display Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** build an update-compatible v0.12.25 APK with correct Vulkan/Android image ownership handoff.
**Architecture:** retain the existing Vortek command protocol and SurfaceControl producer/consumer ring; add real release/acquire command buffers around imported AHB images. Preserve pinned emulator runtime and .24 app features.
**Tech Stack:** C11, Vulkan 1.3, Android API31/36, Java17, GitHub Actions.
**Spec:** docs/superpowers/specs/2026-11-06-display-handoff-design.md

## Global Constraints
- Base exact commit 7f1b7f037e50fea6d1af200a71543bf09bb70d76.
- Emulator runtime run42241434598 unchanged.
- Package dev.rb4dx.standalone.linuxvk.turnip.inputbridge; versionCode31.
- No user-game writes, no firmware requests, no new diagnostic-only APK.
- Source/tests/build verification cannot establish actual phone rendering.

## Review Focus
- First image acquisition and repeated acquire/release preserve valid image layouts and full pixel contents.
- Producer GPU wait and compositor release fences both protect reuse.
- GPU errors and timeouts never lead to early resource destruction or buffer publication.
- All patched server entrypoints retain wire compatibility and valid queue synchronization.
- APK signing and runtime hashes match the installed .24 lineage.

## Task 1: Foreign image ownership
Files: create native/patches/rb4dx-vortek-foreign-sync.patch and focused host behavior tests; helper source/header under native/vortek if necessary. Existing upstream base is /workspace/scratch/dd1b1e4c52fe/vortek-server; apply current rb4dx-vortek-frame-index.patch before the new patch.
Interfaces: acquireNextImage returns a reserved guest index only with correct GPU ownership; present emits Android update only after successful release command completion.
- [x] Add behavioral regression coverage that fails against .24: inspect actual VkSubmitInfo and image barriers emitted at acquire/present boundaries with deterministic fake Vulkan driver endpoints.
- [x] Implement explicit image ownership and layout transfer; preserve timeout safety, cancellation and resource lifetime.
- [x] Verify repeated three-image cycles, initial acquire, submit failure, delayed completion, and patch applicability.
- [x] Review the complete change independently and resolve significant findings.

## Task 2: Integrate and build
Files: .github/workflows/android.yml, app/build.gradle.kts, app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java, tests/run.sh, applicable package checks.
- [x] Apply the new patch to the pinned Vortek source in CI and run behavioral tests.
- [x] Keep Play register dumps off while diagnostic probes retain explicit register reporting; preserve process error logging.
- [x] Set the successor version metadata and update package expectations.
- [x] Run tests/run.sh, git diff --check, Android CI compile/package checks.
- [x] Verify produced APK version/package, signing certificate vs .24, and embedded runtime hashes.
- [ ] Save final reviewed APK and engineering findings; report exact tested scope and hardware limitation.


## Recovered execution and review
- Prior session stopped before implementation; recovered exact .24 source and plans.
- Three initial red/green regressions: missing ownership command, PRESENT layout passed to ordinary imported image, and unsafe destruction after failed release submit.
- Independent review identified two Important failure paths: IPC rings left open after renderer failure and retryable error after partially submitted acquisition. Both gained observed-failing regressions and were fixed in one pass. Failed contexts now wake guest send/receive; partial acquisition returns terminal device loss and retains resources.
- Initial CI run 42293682234 succeeded and .24 signing/runtime identity matched. Final build must include the review fixes; do not distribute the initial artifact.
- Deferred minor review findings: test output says “preserve contents” while verifying barrier/layout metadata, not pixels; event-based layout adapters are not exercised by the new host harness. Main legacy/synchronization2 barriers are exercised.
- Explicit verification boundary: no phone visual test, compositor load test, or playability claim. General render-pass PRESENT translation is outside the pinned emulator's barrier/dynamic-rendering path and this change.
