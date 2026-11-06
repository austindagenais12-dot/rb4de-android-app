#include <assert.h>
#include <stdint.h>
#include "rb4dx_ahb_format.h"

int main(void) {
    // These are Android platform pixel formats, not the Vulkan VkFormat enum.
    assert(rb4dx_ahb_format_for_swapchain(false) == 1u);
    assert(rb4dx_ahb_format_for_swapchain(true) == 5u);
    return 0;
}
