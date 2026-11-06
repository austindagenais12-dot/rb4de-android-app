# RB4DX Android controller input diagnostic

**Project:** Rb4 mobile, isolated v0.12.15 experimental diagnostic APK.

The existing v0.12.14 graphics/startup path remains untouched except for adding a bounded, optional input sender during the 18-second game startup probe. Existing app data, imported Deluxe files, pinned native runtime, and prior APKs must remain safe.

## Goal

Support Android Bluetooth/USB gamepads and a touch-screen controller through the pinned native shadPS4 `BACHATA/1 INPUT` snapshot protocol. Produce honest per-run Android socket-send diagnostics, without implying a guest acknowledged or used the input, and no claim of playable Rock Band 4.

## UX

- Physical gamepad: standard Android buttons, D-pad/hat, dual analog sticks, triggers. Recognize it without requiring Bluetooth pairing within the app.
- Touch-screen: hideable, responsive, multi-touch controller with colorful face buttons and D-pad; customizable touch opacity and a selectable experimental five-fret/strum view. Distinguish standard PS4 pad mapping from genuine guitar/drum instrument input.
- During a startup probe, hold buttons while the test runs. The report indicates snapshots sent and whether the socket connected, not whether the game acted on presses.
- Closing the overlay, releasing buttons or ending a run must clear held input. No file access or changes to game files.

## Technical boundaries

- Native fork already has `RuntimeClient::StartInputReader` and `ApplyRemoteState` and expects `BACHATA/1 INPUT slot=0 seq=<uint64> buttons=0x<hex> lx=<0..255> ly=<0..255> rx=<0..255> ry=<0..255> l2=<0..255> r2=<0..255> touch=<0|1> tx=<0..1919> ty=<0..1079>\n`.
- Android `NativeRuntimeSocket` currently accepts/reads a session but cannot transmit. Add a session-scoped, nonblocking writer protected against close races.
- Android Java state owns merging touch and hardware input and emits full immutable snapshots at a bounded interval while the process is alive; no change to the native runtime payload.
- Standard gamepad input alone may not satisfy Rock Band 4 instrument classes; do not label the guitar view as working hardware emulation until verified.

## Acceptance

- JVM tests cover button merging, analog priority, release, valid exact snapshots, sequence, and end-of-run neutral reset.
- JNI writer cannot send to a different listener or after disconnect; an Android build compiles it.
- Existing emulator diagnostics/tests still pass, the APK builds, and the previous v0.12.14 APK is retained.
