# Native backend plan: Rock Band 4 only

This document records the real work remaining before Play can be enabled.
There is deliberately no placeholder fake emulation code in the Android app.

## Dependency boundary

- Standalone Android UI and importer: original code in this project.
- PS4 emulation: upstream GPL-licensed
  `https://github.com/zenithblue-oss/shadps4-arm64`, inspected at
  `be6bc2e9c60799e071dd2fafa6216e8d80ec619c`.
- PS4 guest x86-64 execution on the Snapdragon ARM64 host: FEX translator or
  equivalent. It cannot be omitted merely because one game is targeted.
- Linux/glibc runtime and graphics/audio/input bridge: needed by this upstream
  architecture; must be built from permissible, reproducible sources.

The `native-runtime.yml` workflow and `scripts/build-native-runtime.sh` have
successfully produced the verified ARM64 Linux runtime. The Android runtime-stage
APK bundles this output and checks every file while extracting it into private
storage. This does not make the Linux binaries Android-loadable or executable;
the native process, graphics and input bridge is still missing.

Do **not** reuse Bachata's modified/resigned APKs or its package name. Its
Android frontend is not supplied in the public core repository. Using its
prebuilt runtime blindly is not a reliable replacement for building and
integrating the backend.

## Completed integration milestone

- GitHub Actions builds and caches the ARM64 shadPS4/FEX and Vortek runtime.
- The Android workflow fetches the latest successful runtime automatically.
- The app provisions the runtime without root or access to another app's data.
- Every file is SHA-256 checked against its manifest; ZIP traversal and
  duplicate or missing entries are rejected.
- The Play button remains disabled until a real Android launch bridge works.

## First on-device CPU execution experiment

The debug APK has a separate application ID so it can be installed alongside
the working importer without deleting the user's Deluxe data. It bundles the
AArch64 glibc dynamic loader in Android's native-library directory and offers
a manual test sequence: loader --version, loader --version with glibc rseq
disabled, FEXCore guest CPU smoke with rseq disabled, and a linker trace if the
CPU test fails. Each step has a 12-second timeout and its own captured exit
status and subprocess output, with a Copy Report button. This is a genuine CPU
process execution experiment, not PS4 game emulation. A successful smoke run
does not establish Vulkan graphics, game-directory access, or RB4DX support.

## Minimum backend contract

```text
Engine.start(
  gameFolder = <native-readable mount of selected Android tree URI>,
  emulatorUserDir = <this app>/files/shadps4/user,
  executable = eboot.bin,
  titleId = CUSA02084 or CUSA02901,
  firmwareModulesDir = <user-supplied legal dump, if required>,
  graphicsSurface = <Android Surface>
)
```

This is an architectural contract, **not** an existing implemented API.

## Exact file layout prepared by this importer

```text
<private files>/shadps4/user/
  data/<CUSA>/GoldHEN/
    RB4DX/ps4/...       (Deluxe asset files)
    plugins/RB4DX-Plugin.prx
    plugins.ini
  custom_modules/<CUSA>/
    RB4DX-Plugin.prx    (auto-start custom module)
```

shadPS4's public `src/core/linker.cpp` enumerates
`GetUserPath(PathType::CustomModulesDir) / GameSerial()` and loads files there.
This is why the importer stages the `.prx` into `custom_modules` as well as
retaining the original GoldHEN copy.

## Acceptance criteria before Play is enabled

1. The native emulator starts a *legally dumped* vanilla Rock Band 4 build
   2.21 with a real Vulkan surface and working touch/controller input.
2. The title ID detected from the game matches the selected US/EU region.
3. Logs show `RB4DX-Plugin.prx` discovered and started, without linker errors.
4. The mod's `GoldHEN/RB4DX` rawfiles can be read through the emulated
   `/data/GoldHEN/RB4DX/` mount.
5. Deluxe-specific UI renders and a song can load without a freeze.
6. Broken installs are recoverable without deleting the user's game or the
   separate original Bachata app.

Only then should the Play button be enabled in the distributed APK.

## Out of scope

- Compatibility for other PS4 games
- Other console emulators or an app store
- Cover-art scraping, game library, patches, cheats, theme stores
- Downloading copyrighted game, song, firmware, or license material
- Modifying any installed Bachata S4 package or its private files

