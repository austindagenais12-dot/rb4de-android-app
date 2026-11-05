# RB4DX Standalone for Android

**Status: experimental Android control-channel bridge, not a playable emulator yet.**

This is an independent, single-game Android project for running a legally owned
PS4 copy of **Rock Band 4 (update 2.21)** with **Rock Band 4 Deluxe**. It does
not include Sony firmware, Rock Band 4, music, keys, or the Deluxe asset pack.
It does not modify or share storage with Bachata S4.

## What the first stage does

* Select the existing Rock Band 4 game folder through Android's document picker.
* Remember that document access through Android's Storage Access Framework.
* Import the user's unmodified `RB4DX-PS4.zip` safely, without rooting Android.
* Stage Deluxe assets under the new app's own private
  `files/shadps4/user/data/<CUSA>/GoldHEN/` directory.
* Place `RB4DX-Plugin.prx` at
  `files/shadps4/user/custom_modules/<CUSA>/RB4DX-Plugin.prx` so shadPS4's
  explicit custom-module loading code can find it.
* Show the selected game/mod status. The Play button remains disabled until
  a real emulator backend is integrated and verified.
* Automatically unpack the successfully built Linux ARM64 runtime into this
  app's private storage on first launch, verifying every file's SHA-256 against
  the build manifest. This works without Shizuku or root. The bridge probe can
  now launch the Linux emulator for a controlled invalid-game startup test;
  launching a real game is not supported yet.
* Previous phone probes verified that the glibc loader, FEXCore CPU translation,
  and the real shadPS4 command-line executable run under Android 16. The new
  bridge-test APK uses `dev.rb4dx.standalone.bridge`, installing alongside the
  original importer and older diagnostic apps so imported assets are preserved.
* The new **Test native control channel** button binds a real Android filesystem
  Unix-domain socket (JNI/NDK), starts shadPS4 with its upstream
  `--bachata-socket` control-protocol option, and records the emulator's own
  startup/error/shutdown messages. The probe supplies a deliberately missing
  game file and expects `CONTENT_INVALID` and exit code 1. This expected error
  counts as a **PASS only when the socket events are received**. No game or
  Deluxe files are opened; no import is needed. **Copy bridge report** exports
  the full diagnostic output for troubleshooting.

The original RB4DX desktop directions also mention placing the plugin in
`sce_module`; that is *not* enough on Android, and this app uses the open-source
core's dedicated custom-modules directory instead.

## What is still needed for an actual playable APK

1. Integrate Android graphics/Vulkan surfaces, audio, controller input and a
   full emulator lifecycle. The ARM64 core, FEX and startup control protocol
   have preliminary Android tests, but game compatibility remains untested.
2. Bridge the Android document URI for the game directory to the native
   filesystem used by the emulator without duplicating large game assets.
3. Implement real game launch, on-screen controls and robust logs.
4. Verify the Rock Band 4 executable, version, plugin loading, and Deluxe menus
   on the user's Snapdragon S24 Ultra. This is experimental emulator work.

The **Play** button is intentionally disabled until stage 2+ are implemented;
the APK is not presented as a working emulator.

## Build the Android runtime-stage app

Requires JDK 17, Android SDK platform 35, Android Gradle Plugin 8.9.0 and
Gradle 8.11.1. GitHub Actions workflow android.yml selects the latest
successful native runtime, verifies its ZIP and manifest, stages both as
Android assets, stages the glibc ARM64 loader as a native library, and builds
the runtime-stage debug APK automatically.
On the phone, the app extracts and verifies the runtime on first launch.
For local Gradle builds, supply both native runtime assets under
app/src/main/assets/runtime first. Do not commit the large binary to Git.

## Build the open-source ARM64 backend separately

The `.github/workflows/native-runtime.yml` workflow downloads the public
`zenithblue-oss/shadps4-arm64` source at a pinned revision. It uses the
upstream FEX/shadPS4 and Debian/Vortek build scripts, then verifies and exports
`runtime.zip` and `manifest.json` as a separate CI artifact. The output is a
**Linux ARM64 runtime bundle**, not an Android app or runnable APK.

The native workflow starts automatically when its build scripts or workflow
change on `main`, and it can also be started manually from GitHub Actions. A
new fix cancels an older build on the same branch. Before compiling, CI checks
the compiler and Vulkan development headers. The completed ARM64 emulator and
FEXCore stage is cached *before* Vortek and packaging run. If a later step
fails, the next run can reuse the compiled core instead of spending another
half-hour compiling it. Cache entries can expire or be evicted, so a future
run may occasionally need to rebuild. The successful core is also attached to
the run as a diagnostic artifact retained for seven days.

The Android control-channel bridge now has an experimental JNI socket
implementation, but Vulkan surface transport and native game-directory mounts
are **not implemented yet**. See BACKEND_PLAN.md for remaining work.
Native shadPS4 compilation and Linux runtime packaging have succeeded in
GitHub Actions. The diagnostic bridge is not a working Play button or evidence
of game compatibility.

Android release package: `dev.rb4dx.standalone` (independent of Bachata).
Current test build package: `dev.rb4dx.standalone.bridge`.

## Test the Deluxe archive installer on a PC

Run `bash tests/run.sh`; it compiles the Java-only installer with `javac`
and checks normal import, plugin placement, ZIP traversal protection, and
native-runtime manifest/hash validation.

## Legal / source notes

This launcher code is GPL-2.0-or-later. When the upstream shadPS4 core is
integrated, comply with its GPL license and provide the corresponding source.
RB4DX is an independent fan project: https://rb4dx.milohax.org/

