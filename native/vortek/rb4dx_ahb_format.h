#ifndef RB4DX_AHB_FORMAT_H
#define RB4DX_AHB_FORMAT_H

#include <stdbool.h>
#include <stdint.h>

// Android's public NDK header exposes RGBA8888 (1) but not the BGRA8888 (5)
// enum in its vendor-compatible VNDK header. Both constants are platform
// defined; see frameworks/native/libs/nativewindow/include/vndk/hardware_buffer.h.
// Vulkan's external-memory import REQUIRES the AHB and VkImage formats to match.
#define RB4DX_AHB_FORMAT_RGBA_8888 1u
#define RB4DX_AHB_FORMAT_BGRA_8888 5u

static inline uint32_t rb4dx_ahb_format_for_swapchain(bool use_bgra) {
    return use_bgra ? RB4DX_AHB_FORMAT_BGRA_8888 : RB4DX_AHB_FORMAT_RGBA_8888;
}

#endif
