# RB4DX v0.12.25 display handoff design

Goal: repair the Vulkan-to-Android ownership contract in the live display path on the user's S24 Ultra while retaining the exact v0.12.24 emulator, audio, controller, driver choice and package identity.

## Evidence
The user's v0.12.24 APK SHA256 is ca0f609a4935be753f8c4f5af63eaf9413b29f9446b1c05637e67d928b165972, identical to Android run 42273525123 at commit 7f1b7f037e50fea6d1af200a71543bf09bb70d76. Its native runtime is runtime-90598e48aa33, run 42241434598 at c7232ec3548c5a1ce3e1a71f5122f7e5220ba14a.

The live Vortek patch submits an empty VkSubmitInfo and waits for its fence before publishing an imported AHardwareBuffer through SurfaceControl. That waits for execution but performs no image ownership release to Android. The imported images use exclusive sharing. Android compositor consumers are foreign devices for Vulkan AHB ownership purposes.
- https://docs.vulkan.org/spec/latest/chapters/memory.html#memory-external-android-hardware-buffer
- https://docs.vulkan.org/spec/latest/chapters/synchronization.html
- https://source.android.com/docs/core/graphics/implement-vulkan

The screenshots show selective missing/clipped game drawing. This ownership gap is a confirmed code defect, not proof that it is the only cause of those pixels. The first logged 1920x1080 render frame is not evidence of a mismatched AHB: the host swapchain is consistently 1280x720.

## Chosen approach
Implement explicit Vulkan FOREIGN queue ownership release/acquire around the existing SurfaceControl display path, including image layout handling. Keep compositor release-fence protection. The alternatives considered were a complete native Android WSI rewrite (larger protocol/lifecycle change) and rebuilding/changing shader behavior (no demonstrated Turnip cause); neither is included.

## Contract
1. Before Android receives an image, a real image barrier must release GPU writes from the producer queue to VK_QUEUE_FAMILY_FOREIGN_EXT in an external-compatible layout. A completed fence is necessary but not sufficient.
2. Reacquisition happens only after compositor release, and ownership returns before the acquire semaphore/fence is signalled to the guest.
3. Imported AHB images are ordinary Vulkan images; translate virtual PRESENT_SRC_KHR to the appropriate real layout in the server, covering the barrier forms used by the pinned emulator. Do not silently discard rendered contents.
4. Failed GPU submissions or unresolved fences never publish/recycle a buffer. Resource cleanup must not destroy in-flight command pools, buffers or fences.
5. Keep the .24 runtime and native input/audio code unchanged. No resolution or shader guessing, no device test request needed to perform implementation.
6. Performance: disable opt-in register dumping during normal Play, because the .24 log contains 17,101 Bachata.Crash lines while execution continues. Preserve bounded diagnostic behavior and real process exit/error reporting.
7. APK must retain applicationId dev.rb4dx.standalone.linuxvk.turnip.inputbridge and an update-compatible signing certificate. Use versionCode31 and versionName0.12.25-display-sync.
8. Verify the patch against the exact pinned server df8183df5c2b024f116a5f796a9e4147aa696cd0. Use host command-capture regression tests plus full existing suite and Android compilation. Device visual correctness remains explicitly unverified.

## Boundaries
Do not modify user game files or saves, enable Deluxe, roll back audio/input/fullscreen, switch driver by default, or claim playability from a frame count. Format-changing swapchain cache and shader parity gaps are recorded research findings, not speculative fixes bundled into this build.
