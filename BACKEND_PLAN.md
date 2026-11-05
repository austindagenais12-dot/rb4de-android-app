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
successfully produced the verified ARM64 Linux runtime. The Android APK bundles
this output, verifies every extracted file, and packages the glibc loader in
Android's executable native-library directory. On-device FEXCore CPU and
shadPS4 `--help` commands work. Android graphics and input remain unbridged.

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
- ARM64 loader, FEXCore CPU and native shadPS4 CLI verified on the user's phone.
- Experimental NDK `AF_UNIX` server receives shadPS4 lifecycle events when
  the `--bachata-socket` upstream protocol flag is used. Its standalone probe
  launches with an intentionally absent game file, expecting
  `HELLO`, `Starting`, `ERROR CONTENT_INVALID`, then `Stopped`.
- On-device control probe returned `RB4DX_CONTROL_BRIDGE_PASS`, confirming all
  four lifecycle frames arrived and the intentionally invalid game was
  rejected with exit code 1.
- The next launch-readiness APK adds read-only SAF game inspection with PSF
  parsing and an independent Android Vulkan SurfaceView probe. Passing these
  checks does not connect the Linux renderer to the Android display.
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

## Native control-channel experiment

The Android diagnostic app adds `librb4dx_bridge.so`, implemented
independently with NDK JNI. A native filesystem Unix-domain socket listens in
this app's own private files directory while the verified glibc loader launches
the shadPS4 ARM64 executable. It deliberately passes a nonexistent eboot path.
The expected exit code 1 is only accepted as a successful connection test if
all four lifecycle messages arrive through the socket. A separate **Copy bridge
report** button gathers these results. It does not open Rock Band 4 or the mod.
The protocol option retains its upstream `--bachata-socket` spelling; this
does not use the Bachata Android APK or another application's storage.

## Game-file and GPU display probes

The game-file probe enumerates the user's selected Android DocumentProvider
folder, opens only the first bytes of `eboot.bin`, reads and parses
`sce_sys/param.sfo`, and validates `TITLE_ID` and `APP_VER`. It separately
probes whether the physical folder is readable through a direct `/storage/`
Linux path. A failure there is expected on many scoped-storage Android
versions and indicates the need for a native filesystem bridge. It does not
copy game assets. The Vulkan probe uses Android `SurfaceView` and NDK Vulkan
to create a genuine platform `VkSurfaceKHR`, enumerate devices, check a
present-capable graphics queue and `VK_KHR_swapchain`. It does not create
an emulation renderer or integrate the existing Vortek stack.

## Optional direct shared-storage access experiment

The user selected Option 1 for accessing the existing game without copying it.
The Android manifest declares `MANAGE_EXTERNAL_STORAGE` and the app exposes a
manual Settings button rather than granting access itself. This is a broad
shared-storage permission and must be enabled or revoked by the user. It does
not grant access to another application's protected `Android/data` directory.

For a selected game in primary shared storage, the app maps the document-tree
ID to a normalized path under the primary external-storage directory. The new
JNI probe uses Android-native `open(O_RDONLY | O_NOFOLLOW)`, `fstat` and a
16-byte `pread` of `eboot.bin` to test **actual POSIX path access** without
modifying/copying the game. If this passes, Linux shadPS4 still needs a direct
game-boot probe; the Android-native test does not establish Linux glibc access,
shader rendering or Deluxe plugin initialization.

## Linux glibc file access and actual Vulkan frame

The next APK builds an isolated AArch64 GNU/Linux program with the cross-GCC
toolchain and packages it as an executable ELF (under the Android native-library
directory). The app starts it with the same verified glibc loader, library
directory and rseq workaround used by the real `shadps4-arm64-fex` process.
It reads the existing game `eboot.bin` header with `open`/`fstat`/`pread`,
then quits without booting PS4 code. A pass proves the Linux glibc process
can access the user-selected raw path; it does not establish shadPS4 ELF/SELF
validation, a launchable guest, or Deluxe plugin support.

Separately, the Android side can create a Vulkan swapchain for its live
`SurfaceView`, acquire an image, record a GPU clear-to-blue command, and submit
and present that frame. A successful test establishes actual Android-side GPU
presentation. It does **not** attach the Linux/Vortek renderer to the Android
surface, which remains an architectural task.

## Minimum backend contract

### Controlled real-title startup probe

Following on-device `RB4DX_GLIBC_GAME_ACCESS_PASS` and
`RB4DX_VULKAN_FRAME_PASS`, the next diagnostic runs the **real** ARM64 shadPS4
binary with the selected `CUSA02084` / `CUSA02901` 2.21 `eboot.bin`. It uses
the tested Android control socket, an 18-second hard timeout, automatic
read-only title/version validation, and a vanilla `--ignore-game-patch` launch.
The report records lifecycle messages, console errors and a checkpoint. This
does not yet connect shadPS4's Linux SDL/Vulkan presentation to Android's
SurfaceView and does not enable Play or inject Deluxe.

First on-device real game attempt exited 33 before the emulator's Run marker:
upstream user settings could not create `<files>/.local/share/shadPS4/home/1000`
because the parent directory was absent. The next test prepares
`<files>/shadps4/user/home/1000` and launches with cwd `<files>/shadps4`, so
upstream chooses its existing portable `user` folder (also used by our Deluxe
importer). This is an app-private, non-destructive directory fix.


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

