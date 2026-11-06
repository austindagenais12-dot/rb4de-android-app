# RB4DX Bachata Compatibility Design

## Intent

Bring Rock Band 4 on an unrooted Samsung S24 Ultra from a timed startup probe to an independently launchable full-screen session, with BachataS4 as the working comparison. Preserve the existing probe and its Android display/Turnip path for rollback. The user has played vanilla Rock Band 4 in BachataS4 and explicitly authorized implementing all compatibility improvements.

## Goals

- Offer system Adreno, the existing pinned Turnip 27 driver, and a verified Android/Bionic-compatible Turnip from the official BachataS4 v0.2.5 APK.
- Cache Mesa/Turnip shaders persistently per game, without purporting to precompile unseen game shaders.
- Record actual framebuffer pixel statistics from the stopped producer to distinguish black source frames from presentation failures; preserve the live direct-buffer path pending evidence for a more invasive X11 framebuffer-copy replacement.
- Record exact controller transition masks while keeping neutral pad snapshots.
- Serve the shadPS4 Bachata PCM stream over its existing AF_UNIX framed protocol to Android AudioTrack, reporting successful connection and processed bytes.
- Offer full-screen landscape Play with touch/gamepad controls, Stop, no 120-second probe timeout, bounded logging, and cleanup on Activity destruction. Preserve all diagnostic buttons and the bounded startup probe.

## Non-goals / Caveats

- No claim of playable performance before testing on the S24 Ultra; a successfully built APK cannot verify gameplay.
- No automatic Rock Band 4 Deluxe activation or plugin installation in this step.
- No transplant of glibc Turnip 26.1.0 into an Android/Bionic Vulkan loader. The official v0.2.5 APK has a separate Bionic-compatible Turnip v26.3.0-R5 package for comparison.
- No Vulkan render/present hack that reads an AHardwareBuffer while the GPU may still be writing to it.
- Do not erase preexisting app data, mod imports, saved game choices, or current user changes.

## Approach

Selected by the user's blanket implementation approval: **incremental compatibility switches and observability on the current app**. Considered alternatives: directly replace RB4DX with BachataS4 (loses dedicated app and mod architecture), or rewrite the emulator renderer without a source-frame diagnosis (high regression risk). Reuse supported upstream protocols and make every new behavior reversible.

## Boundaries

- Android app session controller owns emulator, Vortek, X11, audio socket, stop token, and log lifetime.
- Driver selection is an app preference; the actual selected native path must appear in logs.
- Shader cache path is private app storage and survives sessions; app and child process must agree on it.
- Audio framing accepts only bounded code=4 prepare and code=5 PCM, rejecting malformed/oversized packets.
- Pixel analysis is on a stopped GPU producer's software bitmap copy only.
- Play mode is foreground-only; ending the Activity stops the emulator, and its logs are size-capped.

## Verification

Pure-JVM tests for path derivation, input traces, audio frame parsing, log retention, and pixel classification. Pure-C socket tests. CI Android build must package both ARM64 Bionic Turnip libraries and link the audio JNI. Final on-device acceptance requires visible Rock Band 4 menu, audible game sound, controller navigation, and a clean Stop; CI success alone is not enough.
