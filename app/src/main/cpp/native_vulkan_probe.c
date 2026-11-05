// SPDX-License-Identifier: GPL-2.0-or-later
#include <jni.h>
#include <android/native_window_jni.h>

#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <vulkan/vulkan.h>

#include <stdbool.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void append(char* output, size_t capacity, const char* format, ...) {
    size_t used = strlen(output);
    if (used >= capacity - 1) return;
    va_list args;
    va_start(args, format);
    vsnprintf(output + used, capacity - used, format, args);
    va_end(args);
}

JNIEXPORT jstring JNICALL
Java_dev_rb4dx_standalone_NativeVulkanSurfaceProbe_run(
        JNIEnv* env, jclass ignored, jobject java_surface) {
    (void)ignored;
    char report[8192] = "RB4DX_ANDROID_VULKAN_SURFACE_V1\n";
    char reason[128] = "not tested";
    bool passed = false;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    ANativeWindow* window = NULL;
    VkExtensionProperties* instance_extensions = NULL;
    VkPhysicalDevice* devices = NULL;

    if (java_surface == NULL) {
        snprintf(reason, sizeof(reason), "Android Surface is null");
        goto cleanup;
    }

    uint32_t api_version = VK_API_VERSION_1_0;
    PFN_vkEnumerateInstanceVersion enumerate_version =
            (PFN_vkEnumerateInstanceVersion)vkGetInstanceProcAddr(
                    VK_NULL_HANDLE, "vkEnumerateInstanceVersion");
    if (enumerate_version != NULL) enumerate_version(&api_version);
    append(report, sizeof(report), "instance_api=%u.%u.%u\n",
            VK_API_VERSION_MAJOR(api_version), VK_API_VERSION_MINOR(api_version),
            VK_API_VERSION_PATCH(api_version));

    uint32_t extension_count = 0;
    VkResult rc = vkEnumerateInstanceExtensionProperties(NULL, &extension_count, NULL);
    if (rc != VK_SUCCESS || extension_count == 0 || extension_count > 1024) {
        snprintf(reason, sizeof(reason), "Cannot enumerate instance extensions: %d", rc);
        goto cleanup;
    }
    instance_extensions = calloc(extension_count, sizeof(*instance_extensions));
    if (instance_extensions == NULL) {
        snprintf(reason, sizeof(reason), "Out of memory reading Vulkan extensions");
        goto cleanup;
    }
    rc = vkEnumerateInstanceExtensionProperties(NULL, &extension_count, instance_extensions);
    if (rc != VK_SUCCESS && rc != VK_INCOMPLETE) {
        snprintf(reason, sizeof(reason), "Vulkan extension query failed: %d", rc);
        goto cleanup;
    }
    bool has_surface = false;
    bool has_android_surface = false;
    for (uint32_t i = 0; i < extension_count; ++i) {
        if (strcmp(instance_extensions[i].extensionName, VK_KHR_SURFACE_EXTENSION_NAME) == 0)
            has_surface = true;
        if (strcmp(instance_extensions[i].extensionName,
                VK_KHR_ANDROID_SURFACE_EXTENSION_NAME) == 0)
            has_android_surface = true;
    }
    append(report, sizeof(report), "khr_surface=%s android_surface=%s\n",
            has_surface ? "yes" : "no", has_android_surface ? "yes" : "no");
    if (!has_surface || !has_android_surface) {
        snprintf(reason, sizeof(reason), "Required Android Vulkan WSI extension missing");
        goto cleanup;
    }

    const char* extensions[] = {
            VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
    };
    VkApplicationInfo application_info = {
            .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
            .pApplicationName = "RB4DX Native Display Probe",
            .apiVersion = VK_API_VERSION_1_0
    };
    VkInstanceCreateInfo create_info = {
            .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
            .pApplicationInfo = &application_info,
            .enabledExtensionCount = 2,
            .ppEnabledExtensionNames = extensions
    };
    rc = vkCreateInstance(&create_info, NULL, &instance);
    append(report, sizeof(report), "vkCreateInstance=%d\n", rc);
    if (rc != VK_SUCCESS) {
        snprintf(reason, sizeof(reason), "Vulkan instance creation failed");
        goto cleanup;
    }

    window = ANativeWindow_fromSurface(env, java_surface);
    if (window == NULL) {
        snprintf(reason, sizeof(reason), "Could not obtain ANativeWindow from SurfaceView");
        goto cleanup;
    }
    append(report, sizeof(report), "native_window=%dx%d\n",
            ANativeWindow_getWidth(window), ANativeWindow_getHeight(window));
    PFN_vkCreateAndroidSurfaceKHR create_android_surface =
            (PFN_vkCreateAndroidSurfaceKHR)vkGetInstanceProcAddr(
                    instance, "vkCreateAndroidSurfaceKHR");
    PFN_vkGetPhysicalDeviceSurfaceSupportKHR get_surface_support =
            (PFN_vkGetPhysicalDeviceSurfaceSupportKHR)vkGetInstanceProcAddr(
                    instance, "vkGetPhysicalDeviceSurfaceSupportKHR");
    PFN_vkGetPhysicalDeviceSurfaceFormatsKHR get_surface_formats =
            (PFN_vkGetPhysicalDeviceSurfaceFormatsKHR)vkGetInstanceProcAddr(
                    instance, "vkGetPhysicalDeviceSurfaceFormatsKHR");
    PFN_vkDestroySurfaceKHR destroy_surface =
            (PFN_vkDestroySurfaceKHR)vkGetInstanceProcAddr(
                    instance, "vkDestroySurfaceKHR");
    if (create_android_surface == NULL || get_surface_support == NULL
            || get_surface_formats == NULL || destroy_surface == NULL) {
        snprintf(reason, sizeof(reason), "Vulkan Android surface function unavailable");
        goto cleanup;
    }
    VkAndroidSurfaceCreateInfoKHR surface_info = {
            .sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
            .window = window
    };
    rc = create_android_surface(instance, &surface_info, NULL, &surface);
    append(report, sizeof(report), "vkCreateAndroidSurfaceKHR=%d\n", rc);
    if (rc != VK_SUCCESS) {
        snprintf(reason, sizeof(reason), "Android Vulkan surface creation failed");
        goto cleanup;
    }

    uint32_t device_count = 0;
    rc = vkEnumeratePhysicalDevices(instance, &device_count, NULL);
    if (rc != VK_SUCCESS || device_count == 0 || device_count > 64) {
        snprintf(reason, sizeof(reason), "No usable Vulkan physical device: %d", rc);
        goto cleanup;
    }
    devices = calloc(device_count, sizeof(*devices));
    if (devices == NULL) {
        snprintf(reason, sizeof(reason), "Out of memory reading Vulkan devices");
        goto cleanup;
    }
    rc = vkEnumeratePhysicalDevices(instance, &device_count, devices);
    if (rc != VK_SUCCESS && rc != VK_INCOMPLETE) {
        snprintf(reason, sizeof(reason), "Vulkan device query failed: %d", rc);
        goto cleanup;
    }
    for (uint32_t i = 0; i < device_count; ++i) {
        VkPhysicalDeviceProperties props;
        vkGetPhysicalDeviceProperties(devices[i], &props);
        append(report, sizeof(report), "device_%u=%s api=%u.%u\n", i,
                props.deviceName, VK_API_VERSION_MAJOR(props.apiVersion),
                VK_API_VERSION_MINOR(props.apiVersion));
        uint32_t queue_count = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queue_count, NULL);
        if (queue_count == 0 || queue_count > 128) continue;
        VkQueueFamilyProperties* queues = calloc(queue_count, sizeof(*queues));
        if (queues == NULL) continue;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queue_count, queues);
        uint32_t formats = 0;
        rc = get_surface_formats(devices[i], surface, &formats, NULL);
        bool supports_swapchain = false;
        uint32_t device_extension_count = 0;
        if (vkEnumerateDeviceExtensionProperties(devices[i], NULL,
                &device_extension_count, NULL) == VK_SUCCESS
                && device_extension_count > 0 && device_extension_count <= 1024) {
            VkExtensionProperties* device_extensions =
                    calloc(device_extension_count, sizeof(*device_extensions));
            if (device_extensions != NULL) {
                if (vkEnumerateDeviceExtensionProperties(devices[i], NULL,
                        &device_extension_count, device_extensions) == VK_SUCCESS) {
                    for (uint32_t j = 0; j < device_extension_count; ++j) {
                        if (strcmp(device_extensions[j].extensionName,
                                VK_KHR_SWAPCHAIN_EXTENSION_NAME) == 0) supports_swapchain = true;
                    }
                }
                free(device_extensions);
            }
        }
        bool presenting_queue = false;
        for (uint32_t q = 0; q < queue_count; ++q) {
            VkBool32 supported = VK_FALSE;
            if ((queues[q].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0
                    && get_surface_support(devices[i], q, surface, &supported) == VK_SUCCESS
                    && supported == VK_TRUE) presenting_queue = true;
        }
        append(report, sizeof(report),
                "device_%u_presenting_graphics_queue=%s surface_formats=%u swapchain=%s\n",
                i, presenting_queue ? "yes" : "no", rc == VK_SUCCESS ? formats : 0,
                supports_swapchain ? "yes" : "no");
        if (presenting_queue && formats > 0 && supports_swapchain) passed = true;
        free(queues);
    }
    snprintf(reason, sizeof(reason), "%s", passed ? "Android Vulkan WSI is available"
            : "No device has a presenting graphics queue, formats and swapchain");

cleanup:
    append(report, sizeof(report), "result=%s\n", reason);
    if (surface != VK_NULL_HANDLE && instance != VK_NULL_HANDLE) {
        PFN_vkDestroySurfaceKHR destroy = (PFN_vkDestroySurfaceKHR)
                vkGetInstanceProcAddr(instance, "vkDestroySurfaceKHR");
        if (destroy != NULL) destroy(instance, surface, NULL);
    }
    if (window != NULL) ANativeWindow_release(window);
    if (instance != VK_NULL_HANDLE) vkDestroyInstance(instance, NULL);
    free(devices);
    free(instance_extensions);
    char final_report[8448];
    snprintf(final_report, sizeof(final_report), "%s\n%s",
            passed ? "RB4DX_VULKAN_SURFACE_PASS" : "RB4DX_VULKAN_SURFACE_FAILED", report);
    return (*env)->NewStringUTF(env, final_report);
}
