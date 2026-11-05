# RB4DX Live Surface Heartbeat — Diagnostic Design

## Intent

Verify the missing connection from the real Vortek `updateWindowContent` callback to the Android `SurfaceView` while Rock Band 4 is running. The v0.12.13 log shows repeated guest video flips and hundreds of Vortek callbacks, but Android displays only a final still preview after the producer stops. This diagnostic isolates display connection behavior before anyone samples live GPU-backed game buffers.

## Goals

- Show a clearly labeled, changing **diagnostic heartbeat** in the existing `SurfaceView` during the 18-second real-game startup probe, without touching game image buffers.
- Report callback count, attempted/posted heartbeat frames, and surface errors so a failed experiment is actionable.
- Preserve the reliable post-stop final-frame preview and the same game preflight, Turnip setting, native runtime, and PS4 game data.
- Build an installable experimental v0.12.14 APK separately from previous versions.

## Explicit Non-goals

- The heartbeat is **not** Rock Band gameplay, a live game-frame capture, a rendering fix, Deluxe support, or an alteration to shadPS4 guest GPU synchronization.
- Do not inspect or draw any shared `HardwareBuffer` while Vulkan may be writing to it.

## Approaches

1. **Chosen: Draw a throttled status frame on the Android surface when Vortek calls `updateWindowContent`.** This tests the Java callback → SurfaceView route, without shared-buffer races. The tradeoff is an extra diagnostic APK before showing actual game pixels.
2. Directly draw the last Vortek `HardwareBuffer` on every callback. Rejected: there is no verified GPU/compositor fence handoff and this risks corrupted frames or crashes.
3. Keep measuring callbacks without drawing. Rejected: the existing log has already proven callbacks; it does not test the missing display boundary.

## Constraints

- Diagnostic rate limit: no more than one draw attempt every 750 milliseconds; render using only a self-contained Android `Canvas` with no shared game textures.
- Do not let canvas exceptions escape the JNI callback; record last failure and keep the emulator alive.
- A false surface/no window update must not be reported as success.
- Distinguish diagnostic heartbeat from a connected guest renderer: retain `android_surface_attached=false` until real game frames are safely presented.
- Preserve the 18-second timeout and optional stop-and-render preview; never overwrite user game data.
- Reuse v0.12.13's verified native runtime; the new work is Java/UI/packaging only.
