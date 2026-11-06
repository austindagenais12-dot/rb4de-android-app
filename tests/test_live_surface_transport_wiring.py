#!/usr/bin/env python3
"""Prevent accidental unfenced display or reintroduction of hardcoded probe flags."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
bridge = (ROOT / "native/vortek/vortek_bridge.c").read_text()
patch = (ROOT / "native/patches/rb4dx-vortek-frame-index.patch").read_text()
java = (ROOT / "app/src/main/java/com/winlator/xenvironment/components/VortekRendererComponent.java").read_text()
probe = (ROOT / "app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java").read_text()
cmake = (ROOT / "native/vortek/CMakeLists.txt").read_text()

# Guest acquire -> producer lease -> GPU wait -> compositor -> release callback.
assert "acquireWindowImage" in patch
assert "VK_TIMEOUT" in patch
assert "vkWaitForFences" in patch
assert "recordWindowGpuPresentFailure" in patch
assert "pWaitSemaphores" in patch
assert "cancelWindowImage" in patch
assert "nativeAcquireWindowImage" in bridge
assert "rb4dx_frame_ring_gpu_complete" in bridge
assert "rb4dx_frame_ring_present" in bridge
assert "rb4dx_frame_ring_release" in bridge
assert "ASurfaceTransaction_setBufferWithRelease" in bridge
assert '"rb4dx_surface_control_compat.h"' in bridge
assert "release_fence_fd" in bridge
assert "pthread_cond_broadcast" in bridge
assert "rb4dx_frame_ring.c" in cmake
assert "private int acquireWindowImage" in java
assert "private void cancelWindowImage" in java
assert "nativeLivePublish" in java
assert "android_live_gpu_fence_timeouts" in bridge
assert "android_live_game_frame_posted=false" not in java
assert 'append("android_surface_attached=false' not in probe
print("PASS: fenced Android 16 live-surface transport is wired end-to-end")
