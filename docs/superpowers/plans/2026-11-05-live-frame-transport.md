# Live Frame Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Safely advance from the verified stopped-producer Rock Band 4 image to continuous Android presentation without racing the Vortek GPU producer.

**Architecture:** Preserve the v0.12.10 startup probe. First establish a small native frame-lease state machine, then integrate genuine per-image buffers and notifications in pinned Vortek, then a fenced Android SurfaceControl consumer. Each stage must have its own tests and rollback path.

**Tech Stack:** C11 and NDK/Android hardware buffers, pinned JICA98 Vortek C source, Java/Android SurfaceHolder, GitHub Actions, host C/JVM tests.

**Spec:** `docs/superpowers/specs/2026-11-05-live-frame-transport-design.md`

## Global Constraints

- minSdk 31, targetSdk 35, ARM64, NDK 26.3.11579264.
- Preserve existing still-frame probe and keep live rendering experimental.
- Never sample a GPU buffer while Vortek might write into it.
- Queue is bounded to two or three slots; never overwrite a consumer-owned slot.
- No user data deletion, root requirement, or change to the main branch.

## Review Focus

- All slots occupied: acquisition must fail without overwriting.
- Duplicate completion/present/release: reject invalid transitions.
- Late release from a previous generation: reject the stale token.
- Invalid capacity and null state: return failure safely.
- Shutdown during an in-flight frame: reject all new operations and leave actual buffer destruction to the owner.

---

### Task 1: Testable frame-ownership state machine

**Files:**
- Create: `native/vortek/rb4dx_frame_ring.h`
- Create: `native/vortek/rb4dx_frame_ring.c`
- Create: `tests/VortekFrameRingTest.c`
- Modify: `tests/run.sh`

**Interfaces:**
- `Rb4dxFrameRing` stores fixed 3-slot state and monotonically increasing generation tokens.
- `Rb4dxFrameLease` contains `uint8_t slot` and `uint64_t token`.
- `rb4dx_frame_ring_init(ring, capacity)` accepts only capacities 2 and 3.
- `rb4dx_frame_ring_acquire(ring, &lease)` leases a free producer slot.
- `rb4dx_frame_ring_gpu_complete(ring, lease)` marks producer completion.
- `rb4dx_frame_ring_present(ring, lease)` transfers a ready slot to consumer ownership.
- `rb4dx_frame_ring_release(ring, lease)` returns a consumer-owned slot to free.
- `rb4dx_frame_ring_close(ring)` prevents all future operations without freeing native allocations.

- [ ] **Step 1:** Write `tests/VortekFrameRingTest.c` for every Review Focus case and for a valid complete frame lifecycle.
- [ ] **Step 2:** Compile against the missing implementation and verify RED (feature absent).
- [ ] **Step 3:** Implement the C11 header and source and register host compilation/execution in `tests/run.sh`.
- [ ] **Step 4:** Run `bash tests/run.sh` and a sanitizers-enabled ring test; verify all tests GREEN.
- [ ] **Step 5:** Inspect `git diff --check` and commit the isolated implementation.

### Task 2: Vortek image-index path (later milestone)

**Files:**
- Create: `native/patches/rb4dx-vortek-frame-index.patch`
- Modify: `native/vortek/vortek_bridge.c`
- Modify: `app/src/main/java/com/winlator/xenvironment/components/VortekRendererComponent.java`
- Modify: `.github/workflows/android.yml`
- Test: `tests/VortekFrameRingTest.c` and pinned-source patch application check.

**Interfaces:**
- Pinned Vortek swapchain image `i` obtains a distinct AHardwareBuffer for `(windowId, i)`.
- `vkQueuePresentKHR` forwards its `pImageIndices[i]` into the Java callback.
- Native bridge retains the latest completed image index and preserves the still-frame display after Vortek shutdown.

- [ ] **Step 1:** Add tests for per-window/per-image identity and latest-presented-index selection.
- [ ] **Step 2:** Verify tests fail on the single-buffer implementation.
- [ ] **Step 3:** Patch the pinned native source and JNI callback, retaining a compatibility path for the old probe.
- [ ] **Step 4:** Validate patch application and Android CI build; confirm the stopped-producer screenshot still works on device.
- [ ] **Step 5:** Commit only after successful tests and device evidence.

### Task 3: Fenced SurfaceControl consumer (later milestone)

**Files:**
- Create: `native/vortek/rb4dx_live_presenter.c`
- Create: `native/vortek/rb4dx_live_presenter.h`
- Modify: `app/src/main/java/com/winlator/xenvironment/components/VortekRendererComponent.java`
- Modify: `app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java`
- Modify: `native/vortek/CMakeLists.txt`
- Test: host state-machine tests, Android CI, explicit device probe.

**Interfaces:**
- Producer completion fence is attached to each presented AHardwareBuffer.
- Android SurfaceControl release notification is required before the ring frees a frame.
- The experimental surface path reports produced, presented, released, dropped and timed-out frames without blocking the app UI.

- [ ] **Step 1:** Add host tests for fence-required transitions, lost-surface shutdown and late release callbacks.
- [ ] **Step 2:** Verify RED before adding JNI and SurfaceControl code.
- [ ] **Step 3:** Implement native output and guarded Android integration; do not enable Play yet.
- [ ] **Step 4:** Run host/CI tests and verify both the blue Vulkan probe and live frame probe on the S24 Ultra.
- [ ] **Step 5:** Commit the proven implementation and retain the previous APK as fallback.
