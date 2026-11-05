// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.view.Surface;

/** Queries the actual Android Vulkan driver against a live SurfaceView. */
final class NativeVulkanSurfaceProbe {
    static {
        System.loadLibrary("rb4dx_bridge");
    }

    private NativeVulkanSurfaceProbe() {}

    static native String run(Surface surface);
}
