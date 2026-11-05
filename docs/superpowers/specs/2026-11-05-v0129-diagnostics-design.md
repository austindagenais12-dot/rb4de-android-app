# v0.12.9 GPU Crash Diagnostics Design

## Intent

Reduce phone-side trial and error after v0.12.8 reached guest execution but crashed in the ARM64 emulator's Vulkan shader HLE path. The newest Android/Vortek handshake and pinned runtime remain intact.

## Approach

- **Selected: improve evidence without changing GPU behavior.** Keep a bounded full startup log in app-private storage, let the user explicitly export it through Android's document picker, summarize key fatal-signal fields and the ARM64 module-relative caller address, and run CI tests before every APK.
- Rejected for this version: disable shader HLE or bypass Vulkan commands without proof. That could conceal an emulator correctness problem.
- Deferred: guest-side Vulkan dispatch tracing and shader HLE changes, until the complete startup log identifies the first failed Vulkan operation.

## Requirements

- Preserve game content, saves, selected root, GPU selection, and the parallel `.linuxvk.turnip` application identity.
- Continue using the existing 18-second test; do not claim live gameplay or Deluxe support.
- Retain at most 8 MiB of the most recent test log so storage is bounded. A truncation marker must be explicit. A failed log write must not replace the original startup error or crash data.
- Export through `ACTION_CREATE_DOCUMENT` to a user-selected location; no root, Shizuku, additional storage permission, or user-visible app-private path needed.
- Include process exit and fatal-signal summary in the clipboard report; when possible compute the module-relative `shadps4-arm64-fex` caller address from the captured mapping.
- Run JVM regression tests and ZIP manifest verification in GitHub Actions before signing and uploading the APK.

## Testing

Local pure-Java tests: small logs, oversized logs, replacement, stream export, missing file; crash summary with `pc=0`, ARM64 module mapping, no-crash, malformed input. GitHub Actions validates APK package and native libraries. Actual S24 Ultra GPU stability remains a device test, not a CI claim.
