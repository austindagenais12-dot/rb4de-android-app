# RB4DX Vulkan Frame Correctness (S24 Ultra)

## Intent

Make Rock Band 4's already-running audio/menu session render with less corruption,
without disturbing its working Turnip and audio integrations. Preserve complete
diagnostics after the user presses Stop.

## Evidence

- The patched Vortek server's `getSurfaceFormats()` advertises BGRA first,
  and shadPS4 selects the first supported UNORM format. Its Android bridge
  allocates RGBA regardless of the `prefer_bgra` callback. This contradicts
  Vulkan's Android hardware buffer format-equivalence requirements.
- The shadPS4 swapchain requests opaque composition. The direct SurfaceControl
  transport does not set an opaque layer, unlike a normal swapchain.
- `ProcessLogDrain.capture` loses the tail and propagates `Stream Closed`
  when Stop destroys the emulator process.
- Current visuals also show symptoms that may require upstream emulator
  texture, shader, X11-window-sizing, and font improvements. These are not
  established by the present Android-only evidence and are out of scope.

## Design and alternatives

- Chosen: honor BGRA/RGBA requests with matching AHardwareBuffer formats,
  explicitly request opaque composition, and finalize logs on intentional
  shutdown. This fixes confirmed local protocol errors with no runtime-core
  update and keeps current driver/AHB presentation architecture.
- Not chosen: force shadPS4 to RGBA through a separate native Vortek rebuild;
  changes both sides of the protocol without first checking Android's BGRA
  capability.
- Not chosen: replace live output with Bachata's CPU copy/X11 compositor;
  considerably slower and would require replacing our X11 implementation.

## Constraints and validation

- No game binary, Deluxe or game patch modifications. No automatic cache deletion.
- Retain per-title persistent Mesa shader cache and current Turnip selection.
- Tests must prove BGRA/RGBA mapping and bounded head/tail log preservation.
- Build an Android APK and use the S24 Ultra for final graphical verification.
