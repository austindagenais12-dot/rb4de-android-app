# RB4DX Standalone for Android

**Status: experimental Linux glibc game-file access and Vulkan frame presentation
tests, not a playable emulator yet.**

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
  and the real shadPS4 command-line executable run under Android 16. The
  v0.8.1 Linux/Vulkan test APK uses `dev.rb4dx.standalone.linuxvk`, installing
  **alongside** the older `.bridge` and importer packages. Users should keep
  the older app installed; the new package can run both tests without importing
  Deluxe again. Saved folder access must be selected separately in the new app.
* The new **Test native control channel** button binds a real Android filesystem
  Unix-domain socket (JNI/NDK), starts shadPS4 with its upstream
  `--bachata-socket` control-protocol option, and records the emulator's own
  startup/error/shutdown messages. The probe supplies a deliberately missing
  game file and expects `CONTENT_INVALID` and exit code 1. This expected error
  counts as a **PASS only when the socket events are received**. No game or
  Deluxe files are opened; no import is needed. **Copy bridge report** exports
  the full diagnostic output for troubleshooting.
* **Inspect game folder (read only)** opens the saved Android document tree,
  checks `eboot.bin`, parses `sce_sys/param.sfo` to show the game title ID and
  version, and compares the game region. It separately checks whether that
  document-tree folder is directly readable by a Linux pathname. This matters
  because access through Android's document picker does *not* inherently give
  native Linux code permission to traverse the same directory. This inspection
  does not copy or modify the game.
* **Direct native game-file access** offers an **optional** button to open
  Android's All files access permission settings, followed by a read-only
  `open`/`fstat`/`pread` diagnostic on the selected `eboot.bin`. The permission
  allows this app broad access to shared storage, not solely the selected game;
  the user must explicitly enable it in Android Settings and can revoke it at
  any time. This does **not** bypass Android's protected `Android/data` folders
  or copy the game. The native diagnostic only runs when permission is granted
  and a user-selected game folder can be mapped to primary shared storage.
* **Linux glibc game-file check** runs a tiny AArch64 Linux program through
  the packaged glibc dynamic loader, from the same host runtime as shadPS4.
  It uses only `open`, `fstat` and a 16-byte `pread` of the existing `eboot.bin`,
  then exits. This verifies Linux-process path access beyond the previous
  Android Bionic/JNI check. It does **not** load or execute PS4 game code.
* **Test Vulkan surface and GPU** uses a real Android `SurfaceView`, converts it
  into an `ANativeWindow`, creates a Vulkan `VkSurfaceKHR`, and checks for a
  present-capable graphics queue, surface formats and swapchain support. It
  does not render the PS4 game yet, and it does not prove the Linux emulator
  can use the Android Vulkan surface.
* **Render one blue Vulkan test frame** creates a real swapchain, records a
  GPU clear command, and submits/presents one frame to the Android SurfaceView.
  This is an independent Android renderer milestone, **not** shadPS4 graphics.
  The Linux emulator still needs to hand off its output to that display.

The original RB4DX desktop directions also mention placing the plugin in
`sce_module`; that is *not* enough on Android, and this app uses the open-source
core's dedicated custom-modules directory instead.

## What is still needed for an actual playable APK

1. Integrate the emulator's Linux/Vortek renderer with the native Android
   Vulkan display surface, audio, controller input and a full emulator
   lifecycle. Hardware/surface inspection is only the first graphics check.
2. Bridge the Android document URI for the game directory to the native
   filesystem used by the emulator without duplicating large game assets. An
   optional shared-storage direct-path test may remove this requirement for
   games located in accessible directories such as Downloads; the actual Linux
   shadPS4 process still needs a separate full-game launch test.
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
The workflow generates a scoped, non-production **debug signing key** into
`.ci-signing` on the first build and stores it in a CI cache for subsequent
`.linuxvk` builds. Future test packages can update each other while that
cache remains available. The previous `.bridge` debug build used a different
ephemeral signature, which is why `.linuxvk` is a separate install. This key
is for debugging only, not for a future production release.
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

The Android control-channel bridge has a working on-device JNI socket test;
direct game access and Android GPU presentation now have separate probes.
**Emulator Vulkan surface transport and native game-directory mounts remain
unimplemented.** See BACKEND_PLAN.md for remaining work.
Native shadPS4 compilation and Linux runtime packaging have succeeded in
GitHub Actions. The diagnostic bridge is not a working Play button or evidence
of game compatibility.

Android release package: `dev.rb4dx.standalone` (independent of Bachata).
Current test build package: `dev.rb4dx.standalone.linuxvk`.

### Timed real-game startup diagnostic (v0.9)

After the already-passing Linux glibc file-read and Vulkan frame tests, the
diagnostic app offers **Test real Rock Band 4 initialization (18 sec)**. It
automatically rechecks the selected game's title ID and version `02.21`, then
starts the actual ARM64 shadPS4 program with its original `--game` option and
the existing Android Unix-domain lifecycle bridge. Only the original vanilla
game executable is requested; no Deluxe plugin injection is attempted.

The subprocess is stopped after at most 18 seconds, and the app reports its
console output and lifecycle frames, including a best-effort startup checkpoint.
In v0.9.1 the app creates the private portable user directory
`<files>/shadps4/user/home/1000` and starts the Linux emulator with working
directory `<files>/shadps4`. Upstream `path_util.cpp` then chooses its portable
`user` directory instead of the uncreated `.local/share/shadPS4` fallback.
This aligns paths with the existing Deluxe importer without touching shared
game files or deleting existing app data.
This experiment can fail before SDL/Vulkan window creation because the Linux
emulator does not yet have a working Android graphics window service. A
completed diagnostic is **not** proof of a playable game. No separate Deluxe
import is required, and the prior app is left installed. A selected folder,
All files access permission and verified runtime are required.

## Test the Deluxe archive installer on a PC

Run `bash tests/run.sh`; it compiles the Java-only installer with `javac`
and checks normal import, plugin placement, ZIP traversal protection, and
native-runtime manifest/hash validation.

## Legal / source notes

This launcher code is GPL-2.0-or-later. When the upstream shadPS4 core is
integrated, comply with its GPL license and provide the corresponding source.
RB4DX is an independent fan project: https://rb4dx.milohax.org/

