# RB4DX Standalone for Android

**Status: stage 2 (importer plus native-runtime build pipeline), not a playable emulator yet.**

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

The original RB4DX desktop directions also mention placing the plugin in
`sce_module`; that is *not* enough on Android, and this app uses the open-source
core's dedicated custom-modules directory instead.

## What is still needed for an actual playable APK

1. Build and integrate the [shadPS4 ARM64 core](https://github.com/zenithblue-oss/shadps4-arm64)
   as the emulation backend. The upstream project is kept as an external source
   dependency; we do not copy Bachata's Android frontend or APK.
2. Integrate an ARM64-hosted guest x86-64 CPU translator (FEX or equivalent),
   glibc host environment, Vulkan renderer, audio/input and Android lifecycle
   bridge. A single-game focus does not remove these technical requirements.
3. Bridge the Android document URI for the game directory to the native
   filesystem used by the emulator without duplicating large game assets.
4. Implement the emulator launch, on-screen controls and robust logs.
5. Verify the Rock Band 4 executable, version, plugin loading, and Deluxe menus
   on the user's Snapdragon S24 Ultra. This is experimental emulator work.

The **Play** button is intentionally disabled until stage 2+ are implemented;
the APK is not presented as a working emulator.

## Build the Android importer app

Requires JDK 17, Android SDK platform 35, Android Gradle Plugin 8.9.0 and
Gradle 8.11.1. From this directory, run `gradle :app:assembleDebug`.
GitHub Actions workflow `.github/workflows/android.yml` runs the same build.

## Build the open-source ARM64 backend separately

The manual `.github/workflows/native-runtime.yml` workflow downloads the public
`zenithblue-oss/shadps4-arm64` source at a pinned revision. It uses the
upstream FEX/shadPS4 and Debian/Vortek build scripts, then verifies and exports
`runtime.zip` and `manifest.json` as a separate CI artifact. The output is a
**Linux ARM64 runtime bundle**, not an Android app or runnable APK.

The platform bridge, Vulkan surface transport and native game-directory mount
are **not implemented yet**. See `BACKEND_PLAN.md` for the precise remaining
work. The workflow is checked for shell syntax but has not completed a real
native cross-build in this workspace.

Android package: `dev.rb4dx.standalone`, so it cannot overwrite Bachata.

## Test the Deluxe archive installer on a PC

Run `bash tests/run.sh`; it compiles the Java-only installer with `javac`
and checks normal import, plugin placement, ZIP traversal protection, and
native-runtime manifest/hash validation.

## Legal / source notes

This launcher code is GPL-2.0-or-later. When the upstream shadPS4 core is
integrated, comply with its GPL license and provide the corresponding source.
RB4DX is an independent fan project: https://rb4dx.milohax.org/

