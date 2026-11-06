/* Exercises both real request-handler barrier entrypoints, not copied logic. */
#include "swapchain_host.h"
#include <assert.h>
#include <stdio.h>
typedef struct { void* inputBuffer; int memoryPool; } VkContext;
#define VkObject_fromId(id) ((void*)(uintptr_t)(id))
static VkImageMemoryBarrier legacy[3], seen_legacy[3];
static VkImageMemoryBarrier2 modern[3], seen_modern[3];
static void vt_unserialize_vkCmdPipelineBarrier(VkCommandBuffer id, VkPipelineStageFlags* src,
 VkPipelineStageFlags* dst,VkDependencyFlags* dep,uint32_t* mc,VkMemoryBarrier* m,
 uint32_t* bc,VkBufferMemoryBarrier* b,uint32_t* ic,VkImageMemoryBarrier* i,void* in,int* pool) {
 (void)m;(void)b;(void)in;(void)pool;
 if(id) { *(uint64_t*)id=2;*src=VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;*dst=*src;*dep=0;*mc=0;*bc=0;*ic=3; }
 if(i) memcpy(i,legacy,sizeof(legacy));
}
static void vt_unserialize_vkCmdPipelineBarrier2(VkCommandBuffer id,VkDependencyInfo* info,void* in,int* pool) {
 (void)in;(void)pool;*(uint64_t*)id=2;*info=(VkDependencyInfo){.sType=VK_STRUCTURE_TYPE_DEPENDENCY_INFO,
 .imageMemoryBarrierCount=3,.pImageMemoryBarriers=modern};
}
static void capture1(VkCommandBuffer cmd,VkPipelineStageFlags src,VkPipelineStageFlags dst,
 VkDependencyFlags dep,uint32_t mc,const VkMemoryBarrier* m,uint32_t bc,const VkBufferMemoryBarrier* b,
 uint32_t ic,const VkImageMemoryBarrier* i) {
 (void)cmd;(void)src;(void)dst;(void)dep;(void)mc;(void)m;(void)bc;(void)b;assert(ic==3);memcpy(seen_legacy,i,sizeof(seen_legacy));
}
static void capture2(VkCommandBuffer cmd,const VkDependencyInfo* info) {
 (void)cmd;assert(info->imageMemoryBarrierCount==3);memcpy(seen_modern,info->pImageMemoryBarriers,sizeof(seen_modern));
}
TestVulkanWrapper vulkanWrapper={.vkCmdPipelineBarrier=capture1,.vkCmdPipelineBarrier2=capture2};
#include "layout_handlers_under_test.c"
int main(void) {
 legacy[0]=(VkImageMemoryBarrier){.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
 .oldLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,.newLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
 .srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED,
 .dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED,.image=(VkImage)(uintptr_t)15};
 legacy[1]=legacy[0];legacy[1].oldLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;legacy[1].newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
 legacy[2]=legacy[0];legacy[2].oldLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;legacy[2].newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
 for(int i=0;i<3;i++) modern[i]=(VkImageMemoryBarrier2){.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2,
 .oldLayout=legacy[i].oldLayout,.newLayout=legacy[i].newLayout,.image=legacy[i].image,
 .srcAccessMask=legacy[i].srcAccessMask,.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED,.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED};
 VkContext ctx={0};vt_handle_vkCmdPipelineBarrier(&ctx);vt_handle_vkCmdPipelineBarrier2(&ctx);
 assert(seen_legacy[0].newLayout==VK_IMAGE_LAYOUT_GENERAL && "ordinary AHB images cannot use PRESENT_SRC_KHR");
 assert(seen_modern[0].newLayout==VK_IMAGE_LAYOUT_GENERAL);
 assert(seen_legacy[1].oldLayout==VK_IMAGE_LAYOUT_GENERAL);assert(seen_modern[1].oldLayout==VK_IMAGE_LAYOUT_GENERAL);
 assert(seen_legacy[2].oldLayout==VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);assert(seen_modern[2].newLayout==VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
 assert(seen_legacy[0].image==legacy[0].image && seen_legacy[0].srcAccessMask==VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
 assert(seen_modern[0].srcQueueFamilyIndex==VK_QUEUE_FAMILY_IGNORED);
 puts("PASS: both Vulkan barrier entrypoints translate virtual PRESENT and retain offscreen layouts");
}
