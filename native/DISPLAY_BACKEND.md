# Standalone Android graphical backend: implementation status

## Why the real game launch stops

On an S24 Ultra running Android 16, vanilla Rock Band 4 CUSA02084 v02.21
reaches shadPS4's SDL video initialization and exits before guest execution:

```
Failed to initialize SDL video subsystem: No available video device
```

The pinned shadPS4 Linux/glibc binary was built with Linux SDL3. It requires
an X11/Wayland service before its Vulkan window can exist. The earlier Android
Vulkan clear-frame test proves the Adreno GPU and Android SurfaceView work, but
does not supply the Linux window or connect its Vulkan dispatch.

## Independent embedded-X11 build

`display-runtime.yml` compiles a source-pinned standalone embedded Android X11
server engine, `mekhontsev/magicdesk-x11` at
`702b1736ca2d32de2932f6be72c6b7506a0c0da5`, licensed under GPL-3.0.
It is a fork of Termux:X11 with an explicit native embedding contract.

This workflow deliberately runs **separately** from the working shadPS4/FEX
runtime, so failed window-integration work cannot overwrite or needlessly
recompile the game emulator. It publishes the native engine smoke host, an
Android ARM64 `rb4dx-x11-server` process executable, license and API header.
The server prints `RB4DX_X11_READY` only after its Xorg socket is ready.
The Android build now downloads this artifact, bundles the native server under
its own executable native-library directory, creates an app-private Xauthority
cookie, and starts the server before a bounded vanilla game attempt. SDL gets
the actual `DISPLAY`/`XAUTHORITY` environment, not a dummy driver.
The app still has no X11-to-SurfaceView presenter or Vulkan proxy; a successful
server handshake cannot be interpreted as a complete game boot.

## Remaining engineering, in dependency order

1. Verify the bundled X11 server can create its authenticated display and
   that Linux SDL3 can connect to that display without `SDL_Init` failing. The
   startup process currently uses a child process managed by the bounded
   diagnostic rather than a persistent Android Service.
2. Supply the native display renderer and attach/detach its `ANativeWindow`
   to the app's live Android `SurfaceView`; prove a Linux SDL3 Vulkan window
   actually opens through the local X11 service, independent of the game.
3. Build the Android side of the existing Vulkan proxy. The current pinned
   Vortek *client* in the Linux runtime cannot work without a matching
   Android-side server and protocol revision; the server's original source
   depends on Winlator/adrenotools and cannot simply be loaded by name.
4. Start shadPS4 with validated `DISPLAY`, `XAUTHORITY`, Vulkan ICD, host
   library and socket settings, preserving the existing glibc rseq workaround
   and read-only Rock Band 4 executable access.
5. After the guest reaches `Running`, connect controller input, audio and
   managed stop/recovery. Then enable Deluxe plugin discovery and verify it
   *inside* the game.

**Do not set `SDL_VIDEODRIVER=dummy` or `offscreen` as a Play fix.** Those
drivers cannot replace a real Vulkan-presentable Android display for this
Linux build. Do not declare Play ready based on compilation or probe labels.
