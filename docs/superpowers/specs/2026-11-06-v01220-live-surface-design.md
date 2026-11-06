# v0.12.20 Experimental Live Frame Display

## Intent

Display actual Rock Band 4 frames on the Android 16 SurfaceView during an existing bounded startup probe. Keep v0.12.19's GPU backoff, controller input, final-frame preview, and user data unchanged.

## Root cause and verified behavior

v0.12.19 presents >2200 frames through Vortek and successfully posts Android heartbeat frames, but its Java callback paints only diagnostic text. Pinned Vortek's virtual swapchain always acquires image 0 and its queue-present handler does not wait for Vulkan's present semaphores or GPU completion. Displaying this shared buffer without producer and consumer ownership would be unsafe.

## Selected approach

Use Android 16's API 36 `ASurfaceTransaction_setBufferWithRelease`, resolved at runtime to preserve the Android-31 build floor. A bounded indexed image ring prevents acquisition of buffers still held by the compositor. On each Vortek present, submit a queue operation waiting on the present semaphores and use a **bounded GPU fence wait** before handing the buffer to SurfaceControl; the compositor release fence must signal before re-acquisition. A timed-out producer frame stays reserved because it may still be executing. The API's release callback can run on any thread and must not touch freed per-session memory. Use a child SurfaceControl attached to the existing SurfaceView. The producer blocks briefly under backpressure; no unsynchronized buffer is ever displayed.

## Safety boundaries

- Experimental startup-probe mode only; no Play button or Deluxe plugin claim.
- Keep v0.12.19's stopped-producer screenshot on success or failure; if API 36 is absent or the display layer cannot attach, preserve heartbeat-only behavior.
- Never return a consumer-owned image to Vortek until its release fence signals.
- Never mark a GPU image safe before the guest's present waits and bounded GPU fence succeed.
- Per-image identity and leases are tied to a session generation, protecting restart from stale callbacks.
- Limit to 2 or 3 in-flight buffers; fail closed on timeouts rather than racing buffers.
- All counters (attached, produced, presented, released, dropped, fence timeout) accurately describe actual operations, not hardcoded placeholders.

## Alternatives considered

- Repeatedly wrapping the active hardware buffer in a Bitmap: rejected because Vulkan could overwrite it before Android consumes it.
- CPU copies after waiting for GPU idle: potentially workable as a slow exploratory path but changes AHardwareBuffer allocation support and adds heavy copies; deferred.
- Full Winlator compositor port: much larger and would replace working X11 integration; deferred.

## Verification

Host tests validate ring backpressure, generation mismatch, cancellation, and timeout behavior; pinned Vortek patch application and native Android CI must compile. Real gameplay cannot be claimed until S24 Ultra reports nonzero `android_live_game_frames_posted` and the user sees changing Rock Band frames. An APK that passes CI but has no device proof remains experimental.
