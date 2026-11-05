# RB4DX Live Frame Transport Design

## Intent and evidence

Rock Band 4 now renders its ESRB notice into a Vortek Android hardware buffer;
v0.12.10's stopped-producer still-frame preview displays that image. The blue
Android Vulkan surface probe also passes. The remaining display work is a live,
correctly synchronized producer/consumer connection, not a generic Vulkan
driver replacement.

The pinned Android Vortek server currently returns one AHardwareBuffer per
window, aliases every swapchain image to that buffer, always acquires index 0,
and calls `updateWindowContent(windowId)` without an acquire fence. The Android
client deliberately stops the producer before sampling this buffer. Simply
repeating `Bitmap.wrapHardwareBuffer` while the emulator is running would be
unsafe.

## Goal and sequencing

Build a live Vulkan-to-Android frame transport in independently verifiable
milestones. Preserve the existing 18-second startup probe, its final still
frame and v0.12.10 APK as the regression baseline.

1. Define and unit-test a bounded frame-slot lease protocol: producer acquire,
   GPU completion, consumer present, compositor release, and shutdown. Do not
   attach unprotected consumer reads to the active producer.
2. Adapt the pinned Vortek swapchain to genuinely distinct per-image Android
   hardware buffers, carry the image index through present notifications, and
   track the selected final preview frame. Verify the old stopped-producer
   screenshot before attempting live sampling.
3. Connect the queue to Android SurfaceControl with real GPU-completion
   acquire fences and compositor-release notification. At no point may a
   producer overwrite a buffer still owned by the consumer.
4. Add an explicitly experimental, bounded on-screen real-game test with
   counters for produced, displayed, dropped and fence-timeout frames.
5. Only after live video is verified, address audio, touch/controller input,
   focus, lifetime, performance and an enabled Play flow.

## Approaches evaluated

- **Poll the existing single buffer:** shortest code, but producer and Android
  GPU may access the same allocation concurrently. Rejected for reliability.
- **Port Winlator's full Java XServer and OpenGL compositor:** known integration
  pattern but duplicates our embedded authenticated X11 server, substantially
  expanding scope and lifecycle complexity. Keep as fallback.
- **Fenced multi-buffer transport using the existing Vortek socket:** chosen.
  It reuses the working backend, allows incremental tests and defines ownership
  across both GPUs. NDK SurfaceControl supports hardware buffers and acquire
  fences; exact release semantics must be validated against our API/NDK floor.

## Constraints

- Device: Android 16 Samsung S24 Ultra, ARM64, app minSdk 31; Android CI uses
  NDK 26.3.11579264 and targetSdk 35.
- Respect app-private storage and X11 authentication; never require root or
  unrestricted Android/data writes.
- No unchanged shared buffer may be sampled during Vortek writes.
- No working probe, game data, user settings, or main branch should be replaced
  by an experimental live-rendering build.
- Retain no more than three producer/consumer slots per window initially; use
  explicit backpressure instead of overwriting a busy slot.
- Unit-test all state transitions and stale callback handling before JNI.

## Milestone 1 frame-slot contract

An independent, allocation-free C state machine owns three fixed slots.
Acquiring returns a free slot plus a unique nonzero generation token; calling
code must not hand its underlying buffer to the GPU until acquisition succeeds.
Submission marks the matching slot ready only after the producer's completion
fence has been observed. Present marks a ready slot consumer-owned; only a
matching consumer release token frees it. A stale callback, invalid ordering,
or duplicate operation must not transfer ownership. Closing the queue blocks
future acquisitions and discards outstanding tokens; no Android buffer is freed
by this state machine itself.

Milestone 1 has no new APK or claim of live gameplay. It is a verified
component that later native/Vortek integration can depend on.
