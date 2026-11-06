# Extended Startup Diagnostic Design

## Intent

Give Rock Band 4 Android testers enough time to observe actual emulator startup without mistaking a Vortek heartbeat or retained still image for live gameplay. Keep a quick diagnostic and preserve all existing input, Turnip and logging functionality.

## Requirements

- Choose 18, 60 or 120 seconds; default to 60 seconds for extended tests.
- One visible Stop button immediately requests cancellation, even while the emulator is still connecting to its control socket.
- Cleanup stops emulator, input pump, native listener, X11 and Vortek, whether exit, timeout, Stop or Activity destruction initiates it.
- Reports state the selected time, manual cancellation, and timeout separately.
- A future actual live renderer remains the goal, but no diagnostic heartbeat or post-stop preview is labeled gameplay.
- Update the existing controller app in place (same debug package suffix), retain historical APK downloads, and continue pinning the known successful ARM64 native runtime and helpers.

## Approaches

1. Increase one constant: fastest, but native listener rejects 120 seconds and no quick Stop exists. Rejected.
2. Add an independent system-wide background service: can outlive the Activity but substantially expands architecture and safety risks. Deferred until actual gameplay mode.
3. **Selected:** bounded choices and a per-run cancellation token, with short native socket polling and process cleanup in the existing probe. Minimal new integration while preserving 18-second regression tests.

## Boundaries

This does not fix the observed `WAIT_REG_MEM` GPU stall, add native game-frame presentation, enable Deluxe patches, or prove that guest gamepad input works. Those require subsequent emulator/renderer investigation.
