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

The `native-runtime.yml` workflow and `scripts/build-native-runtime.sh` now
provide the pinned core build procedure and integrity checks. They have not
yet produced an Android-loadable backend. This separates cross-build failures
from the later Android process/graphics integration work.

Do **not** reuse Bachata's modified/resigned APKs or its package name. Its
Android frontend is not supplied in the public core repository. Using its
prebuilt runtime blindly is not a reliable replacement for building and
integrating the backend.

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

