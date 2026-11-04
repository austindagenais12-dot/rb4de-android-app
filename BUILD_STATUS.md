# Verified in this workspace

- Created a new Android app package: `dev.rb4dx.standalone`.
- Added an Android document picker for game folder and RB4DX ZIP.
- Added private-folder importer and shadPS4 custom-module placement.
- Added file-count / unpacked-size limits, entry normalization, zip traversal
  rejection, and staged import with backups.
- Java-only smoke tests pass using OpenJDK 17:

  `PASS: import, plugin placement, backup, traversal rejection`
- Added a separate ARM64 Linux backend build workflow that uses the pinned
  open-source shadPS4/FEX source and its maintained runtime build scripts.
- Added a native-runtime ZIP and SHA-256 manifest verifier, with a positive
  test and a tampering rejection test. Both pass in this workspace.
- Native build shell script passes `bash -n` syntax check.

## Not yet verified

- The Android project has **not** been built as an APK in this environment;
  Android SDK/Gradle tools are not installed here.
- No ARM64 PS4 emulation backend has been integrated.
- The native-runtime CI workflow has not yet run to completion; output binaries
  were not produced here because the cross-build toolchain is unavailable.
- Game URI to native filesystem bridging is unfinished.
- The app has not been tested on a Samsung S24 Ultra.
- Rock Band 4 Deluxe playback is **not** working in this stage.

This is an honest milestone, not a finished emulator.
