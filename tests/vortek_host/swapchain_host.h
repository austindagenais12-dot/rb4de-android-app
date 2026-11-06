#ifndef SWAPCHAIN_HOST_H
#define SWAPCHAIN_HOST_H
#define VORTEK_H
#define VULKAN_HELPER_H
#define VK_USE_PLATFORM_ANDROID_KHR
#define VK_NO_PROTOTYPES
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))
#define MEMFREE(a) do { free(a); (a) = NULL; } while(0)
typedef struct { JavaVM* jvm; JNIEnv* env; jobject obj; jmethodID getWindowWidth, getWindowHeight, getWindowHardwareBuffer, updateWindowContent; } JMethods;
typedef struct {
    PFN_vkGetAndroidHardwareBufferPropertiesANDROID vkGetAndroidHardwareBufferPropertiesANDROID;
    PFN_vkAllocateMemory vkAllocateMemory;
    PFN_vkBindImageMemory vkBindImageMemory;
    PFN_vkCreateImage vkCreateImage;
    PFN_vkGetDeviceQueue vkGetDeviceQueue;
    PFN_vkDestroyImage vkDestroyImage;
    PFN_vkFreeMemory vkFreeMemory;
    PFN_vkQueueSubmit vkQueueSubmit;
    PFN_vkCreateFence vkCreateFence;
    PFN_vkDestroyFence vkDestroyFence;
    PFN_vkWaitForFences vkWaitForFences;
    PFN_vkResetFences vkResetFences;
    PFN_vkCreateCommandPool vkCreateCommandPool;
    PFN_vkDestroyCommandPool vkDestroyCommandPool;
    PFN_vkAllocateCommandBuffers vkAllocateCommandBuffers;
    PFN_vkBeginCommandBuffer vkBeginCommandBuffer;
    PFN_vkEndCommandBuffer vkEndCommandBuffer;
    PFN_vkCmdPipelineBarrier vkCmdPipelineBarrier;
    PFN_vkCmdPipelineBarrier2 vkCmdPipelineBarrier2;
} TestVulkanWrapper;
extern TestVulkanWrapper vulkanWrapper;
uint32_t getMemoryTypeIndex(uint32_t, VkFlags);
#endif
