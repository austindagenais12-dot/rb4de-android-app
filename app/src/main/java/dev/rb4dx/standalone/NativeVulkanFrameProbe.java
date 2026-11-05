// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.view.Surface;

/** Presents a real blue Vulkan clear frame through the Android SurfaceView. */
final class NativeVulkanFrameProbe {
    static { System.loadLibrary("rb4dx_bridge"); }

    private NativeVulkanFrameProbe() {}

    static native String presentFrame(Surface surface);
}
