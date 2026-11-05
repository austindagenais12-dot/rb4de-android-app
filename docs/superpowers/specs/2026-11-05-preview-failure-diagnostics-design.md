# RB4DX Preview Failure Diagnostics — Short Design

## Purpose

Identify why the Rock Band 4 still-frame preview returns `false` without
first pressing **Render one blue Vulkan test frame**, while preserving
the existing emulator and APK milestones.

## Observed behavior

- With Mesa Turnip selected, the real-game startup probe reaches guest
  Running, but still reports guest `SIGSEGV` and `WAIT_REG_MEM` stalls.
- Without the blue-frame test, `android_final_frame_preview_posted=false`.
- After the blue-frame test, the same field is `true` and the warning-screen
  still frame appears.
- `VortekRendererComponent.stopAndRenderFinalFrame()` returns `false` for
  several different failure points without reporting which one occurred.

## Approaches

1. **Stage-by-stage preview diagnostics (selected).** Track the preview
   conditions, the exact failed operation, and sanitized exception type.
   Preserve the rendering algorithm. Low risk; the user can compare the
   two known test sequences on the phone.
2. **Automatically draw a blue frame first.** May hide the underlying
   surface/BufferQueue defect and adds an unnecessary GPU operation.
3. **Implement continuous Android rendering now.** Larger, independent
   milestone; impossible to verify until the preview failure is isolated.

## Scope and constraints

- Add a structured diagnostic status covering: updated window available,
  surface validity, native hardware buffer retrieval, bitmap wrapping,
  hardware-canvas locking, drawing, and posting.
- Keep `android_final_frame_preview_posted` for backward compatibility.
- Include a concise failure stage and exception class in the exported
  startup report, without logging private paths or game data.
- Preserve the current resource cleanup and the distinction between
  still-frame preview and live gameplay.
- Package as a separate `v0.12.12` Android diagnostic application so the
  v0.12.10 and v0.12.11 installations and their private data stay intact.
- Make the new diagnostic app default to its bundled Mesa Turnip backend;
  retain an explicit user-selectable System Adreno option.
- Do not change shadPS4 guest code, Vortek frame queues, game files,
  Deluxe plugin loading, or emulator timing in this iteration.
- Run the existing regression suite plus a new pure-Java diagnostic-state
  test. Build and verify the APK through the established GitHub Actions
  workflow; a host-only test cannot prove Android behavior.

## Acceptance criteria

- With and without the blue-frame test, the report names the stage where
  the preview succeeded or stopped; no more unexplained `false`.
- Both pre-existing APKs remain installed and untouched.
- Existing tests and the Android build complete successfully.
- The Android trial confirms the real cause before any automatic display
  initialization or live-frame behavior change is attempted.
