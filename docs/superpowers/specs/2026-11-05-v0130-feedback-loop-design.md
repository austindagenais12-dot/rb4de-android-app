# v0.12.10 Optional Vulkan Dispatch Guard

## Goal and evidence

Prevent the reproducible Rock Band 4 ARM64 GPU-thread null execution without changing game files or falsely claiming gameplay works. The v0.12.9 trace records `pc=0`, `lr=0x776180bc04`, and the emulator text base `0x7760610000`. The relative caller `0x11fbc04` resolves to `Vulkan::DynamicState::Commit`; the previous instruction at `0x11fbc00` is `blr x8`. The final Vulkan command in the pinned function is `vkCmdSetAttachmentFeedbackLoopEnableEXT`.

The evidence strongly indicates a missing optional dispatch function despite the backend advertising the matching extension. It does not establish whether further failures occur after this call is skipped.

## Approaches

- **Chosen: capability gate during device initialization.** After Vulkan-Hpp loads device commands, treat the attachment feedback-loop capability as unsupported if the extension is advertised but `vkCmdSetAttachmentFeedbackLoopEnableEXT` is null. This follows the existing vertex-input fallback and lets renderer callers keep using one capability getter.
- **Alternative: skip only the failing command.** Would avoid the immediate branch but could leave the renderer believing feedback-loop state was applied when it was not.
- **Alternative: change the Vortek proxy's extension list.** More fundamental but requires inspecting all extension negotiation and interception behavior, adding much larger regression risk for this targeted crash.

## Implementation boundaries

- Use a tiny independently testable C++ helper to check an advertised extension and its function pointer together; compile its tests without Android or game assets.
- Place the helper in `native/patches`; stage it into the pinned emulator checkout before applying its existing Vulkan compatibility patch.
- Gate the optional feedback-loop feature after `VULKAN_HPP_DEFAULT_DISPATCHER.init(*device)`, recording a warning on fallback. Leave supported backends unchanged.
- Bump the native emulator build cache key; an old cached emulator binary must never be reused under the changed patch.
- Build on a separate feature branch, leaving `main` and v0.12.9 intact. Android packaging must select the successful native runtime produced on that feature branch.

## Verification and limits

- Red/green compile-and-run tests cover supported command, missing function, and extension absent.
- `git apply --check` validates compatibility against the exact pinned emulator sources.
- GitHub native-runtime build validates ARM64 compilation; Android CI validates the selected runtime and signed APK.
- A successful APK build is not proof of in-game startup; a fresh S24 Ultra probe is still required.
