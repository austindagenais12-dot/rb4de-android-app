// SPDX-License-Identifier: GPL-2.0-or-later
#include <jni.h>
#include <android/native_window_jni.h>

#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <vulkan/vulkan.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* One real Vulkan clear + swapchain present. This is not shadPS4 rendering. */
JNIEXPORT jstring JNICALL
Java_dev_rb4dx_standalone_NativeVulkanFrameProbe_presentFrame(
        JNIEnv* env, jclass ignored, jobject java_surface) {
    (void)ignored;
    char report[4096] = "RB4DX_VULKAN_FRAME_V1\n";
    char failure[160] = "Unknown initialization error";
    bool passed = false;
    ANativeWindow* window = NULL;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkCommandPool pool = VK_NULL_HANDLE;
    VkSemaphore image_ready = VK_NULL_HANDLE;
    VkSemaphore render_done = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    VkPhysicalDevice* devices = NULL;
    VkImage* images = NULL;
    VkSurfaceFormatKHR* surface_formats = NULL;
    VkQueueFamilyProperties* queues = NULL;
    PFN_vkDestroySurfaceKHR destroy_surface = NULL;
    PFN_vkDestroySwapchainKHR destroy_swapchain = NULL;
    PFN_vkCreateSwapchainKHR create_swapchain = NULL;
    PFN_vkGetSwapchainImagesKHR get_swapchain_images = NULL;
    PFN_vkAcquireNextImageKHR acquire_next_image = NULL;
    PFN_vkQueuePresentKHR queue_present = NULL;

#define FAIL(...) do { snprintf(failure, sizeof(failure), __VA_ARGS__); goto cleanup; } while (0)
#define NOTE(...) do { size_t used = strlen(report); if (used < sizeof(report) - 1) \
    snprintf(report + used, sizeof(report) - used, __VA_ARGS__); } while (0)

    if (java_surface == NULL) FAIL("No Android SurfaceView");
    window = ANativeWindow_fromSurface(env, java_surface);
    if (window == NULL) FAIL("No ANativeWindow available");
    int width = ANativeWindow_getWidth(window);
    int height = ANativeWindow_getHeight(window);
    if (width <= 0 || height <= 0) FAIL("Display surface has zero size");
    NOTE("window=%dx%d\n", width, height);

    const char* instance_exts[] = {
        VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
    };
    VkApplicationInfo application = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "RB4DX One Frame Vulkan Test",
        .apiVersion = VK_API_VERSION_1_0
    };
    VkInstanceCreateInfo instance_ci = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &application,
        .enabledExtensionCount = 2,
        .ppEnabledExtensionNames = instance_exts
    };
    VkResult rc = vkCreateInstance(&instance_ci, NULL, &instance);
    if (rc != VK_SUCCESS) FAIL("vkCreateInstance=%d", rc);
    PFN_vkCreateAndroidSurfaceKHR create_android_surface =
        (PFN_vkCreateAndroidSurfaceKHR)vkGetInstanceProcAddr(
            instance, "vkCreateAndroidSurfaceKHR");
    destroy_surface = (PFN_vkDestroySurfaceKHR)vkGetInstanceProcAddr(
            instance, "vkDestroySurfaceKHR");
    PFN_vkGetPhysicalDeviceSurfaceSupportKHR get_support =
        (PFN_vkGetPhysicalDeviceSurfaceSupportKHR)vkGetInstanceProcAddr(
            instance, "vkGetPhysicalDeviceSurfaceSupportKHR");
    PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR get_caps =
        (PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR)vkGetInstanceProcAddr(
            instance, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
    PFN_vkGetPhysicalDeviceSurfaceFormatsKHR get_formats =
        (PFN_vkGetPhysicalDeviceSurfaceFormatsKHR)vkGetInstanceProcAddr(
            instance, "vkGetPhysicalDeviceSurfaceFormatsKHR");
    if (create_android_surface == NULL || destroy_surface == NULL || get_support == NULL
            || get_caps == NULL || get_formats == NULL) FAIL("Android Vulkan WSI unavailable");
    VkAndroidSurfaceCreateInfoKHR surface_ci = {
        .sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
        .window = window
    };
    rc = create_android_surface(instance, &surface_ci, NULL, &surface);
    if (rc != VK_SUCCESS) FAIL("vkCreateAndroidSurfaceKHR=%d", rc);

    uint32_t device_count = 0;
    rc = vkEnumeratePhysicalDevices(instance, &device_count, NULL);
    if (rc != VK_SUCCESS || device_count == 0 || device_count > 32)
        FAIL("No physical Vulkan device: %d", rc);
    devices = calloc(device_count, sizeof(*devices));
    if (devices == NULL) FAIL("Out of memory for physical devices");
    rc = vkEnumeratePhysicalDevices(instance, &device_count, devices);
    if (rc != VK_SUCCESS && rc != VK_INCOMPLETE) FAIL("GPU enumeration=%d", rc);

    VkPhysicalDevice selected = VK_NULL_HANDLE;
    uint32_t queue_family = UINT32_MAX;
    for (uint32_t i = 0; i < device_count && selected == VK_NULL_HANDLE; i++) {
        uint32_t count = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &count, NULL);
        if (count == 0 || count > 128) continue;
        queues = calloc(count, sizeof(*queues));
        if (queues == NULL) FAIL("Out of memory for Vulkan queues");
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &count, queues);
        for (uint32_t q = 0; q < count; q++) {
            VkBool32 present = VK_FALSE;
            if ((queues[q].queueFlags & VK_QUEUE_GRAPHICS_BIT)
                    && get_support(devices[i], q, surface, &present) == VK_SUCCESS
                    && present == VK_TRUE) {
                selected = devices[i];
                queue_family = q;
                break;
            }
        }
        free(queues);
        queues = NULL;
    }
    if (selected == VK_NULL_HANDLE) FAIL("No graphics queue can present to this surface");
    VkPhysicalDeviceProperties props;
    vkGetPhysicalDeviceProperties(selected, &props);
    NOTE("gpu=%s queue_family=%u\n", props.deviceName, queue_family);

    VkSurfaceCapabilitiesKHR caps;
    rc = get_caps(selected, surface, &caps);
    if (rc != VK_SUCCESS) FAIL("Surface capability query=%d", rc);
    if (!(caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT))
        FAIL("This surface does not support transfer-destination clear frames");
    uint32_t format_count = 0;
    rc = get_formats(selected, surface, &format_count, NULL);
    if (rc != VK_SUCCESS || format_count == 0 || format_count > 128)
        FAIL("Cannot query swapchain formats=%d", rc);
    surface_formats = calloc(format_count, sizeof(*surface_formats));
    if (surface_formats == NULL) FAIL("Out of memory for swapchain formats");
    rc = get_formats(selected, surface, &format_count, surface_formats);
    if (rc != VK_SUCCESS && rc != VK_INCOMPLETE) FAIL("Swapchain format read=%d", rc);
    VkSurfaceFormatKHR chosen = surface_formats[0];
    for (uint32_t i = 0; i < format_count; i++) {
        if (surface_formats[i].format == VK_FORMAT_R8G8B8A8_UNORM
                || surface_formats[i].format == VK_FORMAT_B8G8R8A8_UNORM) {
            chosen = surface_formats[i];
            break;
        }
    }
    if (chosen.format == VK_FORMAT_UNDEFINED)
        chosen.format = VK_FORMAT_R8G8B8A8_UNORM;
    VkFormatProperties format_props;
    vkGetPhysicalDeviceFormatProperties(selected, chosen.format, &format_props);
    if (!(format_props.optimalTilingFeatures & VK_FORMAT_FEATURE_TRANSFER_DST_BIT))
        FAIL("Chosen surface format does not support transfer clears");

    float priority = 1.0f;
    VkDeviceQueueCreateInfo queue_ci = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = queue_family,
        .queueCount = 1,
        .pQueuePriorities = &priority
    };
    const char* device_exts[] = { VK_KHR_SWAPCHAIN_EXTENSION_NAME };
    VkDeviceCreateInfo device_ci = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1,
        .pQueueCreateInfos = &queue_ci,
        .enabledExtensionCount = 1,
        .ppEnabledExtensionNames = device_exts
    };
    rc = vkCreateDevice(selected, &device_ci, NULL, &device);
    if (rc != VK_SUCCESS) FAIL("vkCreateDevice=%d", rc);
    create_swapchain = (PFN_vkCreateSwapchainKHR)vkGetDeviceProcAddr(
            device, "vkCreateSwapchainKHR");
    destroy_swapchain = (PFN_vkDestroySwapchainKHR)vkGetDeviceProcAddr(
            device, "vkDestroySwapchainKHR");
    get_swapchain_images = (PFN_vkGetSwapchainImagesKHR)vkGetDeviceProcAddr(
            device, "vkGetSwapchainImagesKHR");
    acquire_next_image = (PFN_vkAcquireNextImageKHR)vkGetDeviceProcAddr(
            device, "vkAcquireNextImageKHR");
    queue_present = (PFN_vkQueuePresentKHR)vkGetDeviceProcAddr(device, "vkQueuePresentKHR");
    if (create_swapchain == NULL || destroy_swapchain == NULL
            || get_swapchain_images == NULL || acquire_next_image == NULL
            || queue_present == NULL) FAIL("Swapchain device entrypoints unavailable");

    VkExtent2D extent = caps.currentExtent;
    if (extent.width == UINT32_MAX) {
        extent.width = (uint32_t)width;
        extent.height = (uint32_t)height;
        if (extent.width < caps.minImageExtent.width) extent.width = caps.minImageExtent.width;
        if (extent.width > caps.maxImageExtent.width) extent.width = caps.maxImageExtent.width;
        if (extent.height < caps.minImageExtent.height) extent.height = caps.minImageExtent.height;
        if (extent.height > caps.maxImageExtent.height) extent.height = caps.maxImageExtent.height;
    }
    uint32_t image_count = caps.minImageCount + 1;
    if (caps.maxImageCount && image_count > caps.maxImageCount)
        image_count = caps.maxImageCount;
    VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    if (!(caps.supportedCompositeAlpha & alpha)) {
        const VkCompositeAlphaFlagBitsKHR choices[] = {
            VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
        };
        for (size_t i = 0; i < sizeof(choices) / sizeof(choices[0]); i++) {
            if (caps.supportedCompositeAlpha & choices[i]) { alpha = choices[i]; break; }
        }
    }
    VkSwapchainCreateInfoKHR swap_ci = {
        .sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
        .surface = surface,
        .minImageCount = image_count,
        .imageFormat = chosen.format,
        .imageColorSpace = chosen.colorSpace,
        .imageExtent = extent,
        .imageArrayLayers = 1,
        .imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT,
        .imageSharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .preTransform = caps.currentTransform,
        .compositeAlpha = alpha,
        .presentMode = VK_PRESENT_MODE_FIFO_KHR,
        .clipped = VK_TRUE
    };
    rc = create_swapchain(device, &swap_ci, NULL, &swapchain);
    NOTE("vkCreateSwapchainKHR=%d extent=%ux%u\n", rc, extent.width, extent.height);
    if (rc != VK_SUCCESS) FAIL("Swapchain creation failed: %d", rc);
    uint32_t swap_image_count = 0;
    rc = get_swapchain_images(device, swapchain, &swap_image_count, NULL);
    if (rc != VK_SUCCESS || swap_image_count == 0 || swap_image_count > 16)
        FAIL("Swapchain image count=%d", rc);
    images = calloc(swap_image_count, sizeof(*images));
    if (images == NULL) FAIL("Out of memory for swapchain images");
    rc = get_swapchain_images(device, swapchain, &swap_image_count, images);
    if (rc != VK_SUCCESS && rc != VK_INCOMPLETE) FAIL("Swapchain image query=%d", rc);

    VkCommandPoolCreateInfo pool_ci = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .queueFamilyIndex = queue_family
    };
    rc = vkCreateCommandPool(device, &pool_ci, NULL, &pool);
    if (rc != VK_SUCCESS) FAIL("vkCreateCommandPool=%d", rc);
    VkCommandBuffer command;
    VkCommandBufferAllocateInfo command_ai = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = pool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1
    };
    rc = vkAllocateCommandBuffers(device, &command_ai, &command);
    if (rc != VK_SUCCESS) FAIL("vkAllocateCommandBuffers=%d", rc);
    VkSemaphoreCreateInfo semaphore_ci = { .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO };
    rc = vkCreateSemaphore(device, &semaphore_ci, NULL, &image_ready);
    if (rc != VK_SUCCESS) FAIL("Image-available semaphore=%d", rc);
    rc = vkCreateSemaphore(device, &semaphore_ci, NULL, &render_done);
    if (rc != VK_SUCCESS) FAIL("Render-complete semaphore=%d", rc);
    VkFenceCreateInfo fence_ci = { .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO };
    rc = vkCreateFence(device, &fence_ci, NULL, &fence);
    if (rc != VK_SUCCESS) FAIL("vkCreateFence=%d", rc);

    uint32_t image_index = 0;
    rc = acquire_next_image(device, swapchain, 2000000000ULL, image_ready,
            VK_NULL_HANDLE, &image_index);
    NOTE("vkAcquireNextImageKHR=%d\n", rc);
    if ((rc != VK_SUCCESS && rc != VK_SUBOPTIMAL_KHR) || image_index >= swap_image_count)
        FAIL("Unable to acquire display image: %d", rc);
    VkCommandBufferBeginInfo begin = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
    rc = vkBeginCommandBuffer(command, &begin);
    if (rc != VK_SUCCESS) FAIL("vkBeginCommandBuffer=%d", rc);
    VkImageMemoryBarrier barrier = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask = 0,
        .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
        .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = images[image_index],
        .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 }
    };
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1, &barrier);
    VkClearColorValue color = { .float32 = { 0.08f, 0.28f, 0.95f, 1.0f } };
    vkCmdClearColorImage(command, images[image_index], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            &color, 1, &barrier.subresourceRange);
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barrier.dstAccessMask = 0;
    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, NULL, 0, NULL, 1, &barrier);
    rc = vkEndCommandBuffer(command);
    if (rc != VK_SUCCESS) FAIL("vkEndCommandBuffer=%d", rc);

    VkQueue graphics_queue;
    vkGetDeviceQueue(device, queue_family, 0, &graphics_queue);
    VkPipelineStageFlags wait_stage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    VkSubmitInfo submit = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .waitSemaphoreCount = 1,
        .pWaitSemaphores = &image_ready,
        .pWaitDstStageMask = &wait_stage,
        .commandBufferCount = 1,
        .pCommandBuffers = &command,
        .signalSemaphoreCount = 1,
        .pSignalSemaphores = &render_done
    };
    rc = vkQueueSubmit(graphics_queue, 1, &submit, fence);
    if (rc != VK_SUCCESS) FAIL("vkQueueSubmit=%d", rc);
    rc = vkWaitForFences(device, 1, &fence, VK_TRUE, 2000000000ULL);
    if (rc != VK_SUCCESS) FAIL("GPU clear frame wait=%d", rc);

    VkPresentInfoKHR present = {
        .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
        .waitSemaphoreCount = 1,
        .pWaitSemaphores = &render_done,
        .swapchainCount = 1,
        .pSwapchains = &swapchain,
        .pImageIndices = &image_index
    };
    rc = queue_present(graphics_queue, &present);
    NOTE("vkQueuePresentKHR=%d\n", rc);
    if (rc != VK_SUCCESS && rc != VK_SUBOPTIMAL_KHR)
        FAIL("Unable to present Vulkan frame: %d", rc);
    vkQueueWaitIdle(graphics_queue);
    /* Keep the blue test frame briefly visible before releasing the swapchain. */
    struct timespec visible = { .tv_sec = 1, .tv_nsec = 0 };
    nanosleep(&visible, NULL);
    passed = true;
    snprintf(failure, sizeof(failure), "A Vulkan GPU clear frame was submitted and presented");

cleanup:
    NOTE("result=%s\n", failure);
    if (device != VK_NULL_HANDLE) vkDeviceWaitIdle(device);
    if (fence != VK_NULL_HANDLE) vkDestroyFence(device, fence, NULL);
    if (render_done != VK_NULL_HANDLE) vkDestroySemaphore(device, render_done, NULL);
    if (image_ready != VK_NULL_HANDLE) vkDestroySemaphore(device, image_ready, NULL);
    if (pool != VK_NULL_HANDLE) vkDestroyCommandPool(device, pool, NULL);
    if (swapchain != VK_NULL_HANDLE && destroy_swapchain != NULL)
        destroy_swapchain(device, swapchain, NULL);
    if (device != VK_NULL_HANDLE) vkDestroyDevice(device, NULL);
    if (surface != VK_NULL_HANDLE && destroy_surface != NULL)
        destroy_surface(instance, surface, NULL);
    if (instance != VK_NULL_HANDLE) vkDestroyInstance(instance, NULL);
    if (window != NULL) ANativeWindow_release(window);
    free(queues);
    free(images);
    free(surface_formats);
    free(devices);
    char final_report[4352];
    snprintf(final_report, sizeof(final_report), "%s\n%s",
            passed ? "RB4DX_VULKAN_FRAME_PASS" : "RB4DX_VULKAN_FRAME_FAILED", report);
    return (*env)->NewStringUTF(env, final_report);
#undef FAIL
#undef NOTE
}
