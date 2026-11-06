/* Compiles the production swapchain and present handler, replacing only JNI,
 * AHardwareBuffer and Vulkan driver boundaries. An empty GPU submit cannot pass. */
#include "swapchain_host.h"
#include "xwindow_swapchain.h"
#include <assert.h>
#include <stdarg.h>
#include <stdio.h>

#define H(type, n) ((type)(uintptr_t)(n))
static unsigned next_handle = 32, submits, publications, cancellations, failures;
static unsigned destroyed_pools, destroyed_images, destroyed_fences, barriers;
static int reserve_index;
static VkResult submit_result = VK_SUCCESS, wait_result = VK_SUCCESS;
static VkImageMemoryBarrier recorded[1024], last_barrier;
static VkPipelineStageFlags recorded_src[1024], recorded_dst[1024];
static VkSubmitInfo last_submit;
static VkSemaphore last_signal, last_wait;
static VkFence last_fence, pending_release_fence;
static bool delay_release_only;
static VkImageCreateInfo created_image;
static AHardwareBuffer buffers[3] = {{0}, {1}, {2}};
static bool reject_publish = false;
static jclass get_class(JNIEnv* e, jobject o) { (void)e;(void)o; return H(jclass, 1); }
static jmethodID get_method(JNIEnv* e, jclass c, const char* n, const char* sig) {
    (void)e;(void)c;(void)sig;
    return H(jmethodID, !strcmp(n,"acquireWindowImage") ? 10 : !strcmp(n,"cancelWindowImage") ? 11 : 12);
}
static void delete_ref(JNIEnv* e, jobject o) { (void)e;(void)o; }
static jint call_int(JNIEnv* e, jobject o, jmethodID m, ...) {
    (void)e;(void)o;
    if (m == H(jmethodID,1)) return 1280;
    if (m == H(jmethodID,2)) return 720;
    return reserve_index;
}
static jlong call_long(JNIEnv* e, jobject o, jmethodID m, ...) {
    (void)e;(void)o;(void)m;
    va_list a; va_start(a,m); (void)va_arg(a,int); int index=va_arg(a,int); va_end(a);
    assert(index>=0 && index<3); return (jlong)(uintptr_t)&buffers[index];
}
static void call_void(JNIEnv* e, jobject o, jmethodID m, ...) {
    (void)e;(void)o;
    if (m==H(jmethodID,4)) {
        assert(!reject_publish);
        assert(last_barrier.dstQueueFamilyIndex==VK_QUEUE_FAMILY_FOREIGN_EXT);
        assert(last_barrier.oldLayout==VK_IMAGE_LAYOUT_GENERAL);
        assert(last_barrier.newLayout==VK_IMAGE_LAYOUT_GENERAL);
        assert(last_barrier.srcAccessMask & VK_ACCESS_MEMORY_WRITE_BIT);
        publications++;
    } else if(m==H(jmethodID,11)) cancellations++; else failures++;
}
static const struct JNINativeInterface_ jni = {
 .GetObjectClass=get_class, .GetMethodID=get_method, .DeleteLocalRef=delete_ref,
 .CallIntMethod=call_int, .CallLongMethod=call_long, .CallVoidMethod=call_void
};
static JNIEnv env=&jni;
void AHardwareBuffer_describe(const AHardwareBuffer* b, AHardwareBuffer_Desc* d) {
    (void)b; d->width=1280; d->height=720;
}
uint32_t getMemoryTypeIndex(uint32_t b, VkFlags f) { (void)b;(void)f; return 0; }
static VkResult ahb(VkDevice d,const AHardwareBuffer* b,VkAndroidHardwareBufferPropertiesANDROID* p) {
 (void)d;(void)b;p->allocationSize=1280*720*4;p->memoryTypeBits=1;return VK_SUCCESS;
}
static VkResult allocate(VkDevice d,const VkMemoryAllocateInfo* i,const VkAllocationCallbacks* a,VkDeviceMemory* m) {
 (void)d;(void)i;(void)a;*m=H(VkDeviceMemory,next_handle++);return VK_SUCCESS;
}
static VkResult bind(VkDevice d,VkImage i,VkDeviceMemory m,VkDeviceSize o) {
 (void)d;(void)i;(void)m;(void)o;return VK_SUCCESS;
}
static VkResult create_image(VkDevice d,const VkImageCreateInfo* i,const VkAllocationCallbacks* a,VkImage* out) {
 (void)d;(void)a;created_image=*i;*out=H(VkImage,next_handle++);return VK_SUCCESS;
}
static void get_queue(VkDevice d,uint32_t f,uint32_t i,VkQueue* q) { (void)d;assert(f==2);assert(i==0);*q=H(VkQueue,5); }
static void destroy_image(VkDevice d,VkImage i,const VkAllocationCallbacks* a) { (void)d;(void)i;(void)a;destroyed_images++; }
static void free_memory(VkDevice d,VkDeviceMemory m,const VkAllocationCallbacks* a) { (void)d;(void)m;(void)a; }
static VkResult create_fence(VkDevice d,const VkFenceCreateInfo* i,const VkAllocationCallbacks* a,VkFence* f) {
 (void)d;(void)i;(void)a;*f=H(VkFence,next_handle++);return VK_SUCCESS;
}
static void destroy_fence(VkDevice d,VkFence f,const VkAllocationCallbacks* a) { (void)d;(void)f;(void)a;destroyed_fences++; }
static VkResult wait_fences(VkDevice d,uint32_t n,const VkFence* f,VkBool32 all,uint64_t t) {
 (void)d;(void)f;assert(n>0 && all);assert(t>0 && t<=500000000ULL);return delay_release_only && *f != pending_release_fence ? VK_SUCCESS : wait_result;
}
static VkResult reset_fences(VkDevice d,uint32_t n,const VkFence* f) { (void)d;(void)n;(void)f;return VK_SUCCESS; }
static VkResult create_pool(VkDevice d,const VkCommandPoolCreateInfo* i,const VkAllocationCallbacks* a,VkCommandPool* p) {
 (void)d;(void)a;assert(i->queueFamilyIndex==2);*p=H(VkCommandPool,next_handle++);return VK_SUCCESS;
}
static void destroy_pool(VkDevice d,VkCommandPool p,const VkAllocationCallbacks* a) { (void)d;(void)p;(void)a;destroyed_pools++; }
static VkResult allocate_commands(VkDevice d,const VkCommandBufferAllocateInfo* i,VkCommandBuffer* b) {
 (void)d;for(uint32_t x=0;x<i->commandBufferCount;x++) b[x]=H(VkCommandBuffer,next_handle++);return VK_SUCCESS;
}
static VkResult begin_command(VkCommandBuffer b,const VkCommandBufferBeginInfo* i) { (void)b;(void)i;return VK_SUCCESS; }
static VkResult end_command(VkCommandBuffer b) { (void)b;return VK_SUCCESS; }
static void barrier(VkCommandBuffer b,VkPipelineStageFlags s,VkPipelineStageFlags d,VkDependencyFlags f,
 uint32_t nm,const VkMemoryBarrier* m,uint32_t nb,const VkBufferMemoryBarrier* bm,uint32_t ni,const VkImageMemoryBarrier* im) {
 (void)f;(void)nm;(void)m;(void)nb;(void)bm; assert(ni==1);assert((uintptr_t)b<1024);
 recorded[(uintptr_t)b]=*im; recorded_src[(uintptr_t)b]=s;recorded_dst[(uintptr_t)b]=d;barriers++;
}
static VkResult submit(VkQueue q,uint32_t n,const VkSubmitInfo* s,VkFence f) {
 assert(q==H(VkQueue,5));assert(n==1);submits++;last_submit=*s;last_fence=f;
 last_signal=s->signalSemaphoreCount?s->pSignalSemaphores[0]:VK_NULL_HANDLE;
 last_wait=s->waitSemaphoreCount?s->pWaitSemaphores[0]:VK_NULL_HANDLE;
 if(s->commandBufferCount) {
   assert(s->commandBufferCount==1);
   uintptr_t cb=(uintptr_t)s->pCommandBuffers[0];last_barrier=recorded[cb];
   if(last_barrier.dstQueueFamilyIndex==VK_QUEUE_FAMILY_FOREIGN_EXT) pending_release_fence=f;
   assert(recorded_src[cb]!=0 && recorded_dst[cb]!=0);
 }
 if(s->waitSemaphoreCount) assert(s->pWaitDstStageMask[0]==VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
 return submit_result;
}
TestVulkanWrapper vulkanWrapper={
 .vkGetAndroidHardwareBufferPropertiesANDROID=ahb,.vkAllocateMemory=allocate,.vkBindImageMemory=bind,
 .vkCreateImage=create_image,.vkGetDeviceQueue=get_queue,.vkDestroyImage=destroy_image,.vkFreeMemory=free_memory,
 .vkQueueSubmit=submit,.vkCreateFence=create_fence,.vkDestroyFence=destroy_fence,.vkWaitForFences=wait_fences,
 .vkResetFences=reset_fences,.vkCreateCommandPool=create_pool,.vkDestroyCommandPool=destroy_pool,
 .vkAllocateCommandBuffers=allocate_commands,.vkBeginCommandBuffer=begin_command,.vkEndCommandBuffer=end_command,
 .vkCmdPipelineBarrier=barrier
};
/* Exact production request handler is included by the runner. */
typedef struct { void* inputBuffer; int memoryPool; int status; } VkContext;
static void vt_unserialize_VkPresentInfoKHR(VkPresentInfoKHR* p,void* in,int* pool) { (void)pool;*p=*(VkPresentInfoKHR*)in; }
#include "present_handler_under_test.c"

static XWindowSwapchain* make_swapchain(void) {
 static JMethods jm={.env=&env,.obj=H(jobject,1),.getWindowWidth=H(jmethodID,1),
 .getWindowHeight=H(jmethodID,2),.getWindowHardwareBuffer=H(jmethodID,3),.updateWindowContent=H(jmethodID,4)};
 VkSwapchainCreateInfoKHR ci={.sType=VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
 .minImageCount=3,.imageFormat=VK_FORMAT_B8G8R8A8_UNORM,.imageExtent={1280,720},
 .imageUsage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT};
 XWindowSwapchain* sc=XWindowSwapchain_create(H(VkDevice,1),2,&ci,&jm,7);
 assert(sc);assert(created_image.sharingMode==VK_SHARING_MODE_EXCLUSIVE);return sc;
}
static void acquire(XWindowSwapchain* sc,int index) {
 reserve_index=index;uint32_t out=99;unsigned before=submits;
 assert(XWindowSwapchain_acquireNextImage(sc,1,H(VkSemaphore,6),VK_NULL_HANDLE,&out)==VK_SUCCESS);
 assert(out==(uint32_t)index);assert(submits>before);
 assert(last_submit.commandBufferCount==1 && "acquire must transfer ownership, not submit an empty wait");
 assert(last_barrier.srcQueueFamilyIndex==VK_QUEUE_FAMILY_FOREIGN_EXT);
 assert(last_barrier.dstQueueFamilyIndex==2);assert(last_barrier.newLayout==VK_IMAGE_LAYOUT_GENERAL);
 assert(last_barrier.image==sc->images[index].image);assert(last_signal==H(VkSemaphore,6));
}
static void present(XWindowSwapchain* sc,uint32_t index) {
 VkSwapchainKHR sw=(VkSwapchainKHR)sc;VkSemaphore sem=H(VkSemaphore,7);
 VkPresentInfoKHR p={.sType=VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,.waitSemaphoreCount=1,
 .pWaitSemaphores=&sem,.swapchainCount=1,.pSwapchains=&sw,.pImageIndices=&index};
 VkContext ctx={.inputBuffer=&p};vt_handle_vkQueuePresentKHR(&ctx);
}
int main(void) {
 XWindowSwapchain* sc=make_swapchain();
 for(int cycle=0;cycle<4;cycle++) for(int i=0;i<3;i++) {
  acquire(sc,i);
  assert(last_barrier.oldLayout==(cycle?VK_IMAGE_LAYOUT_GENERAL:VK_IMAGE_LAYOUT_UNDEFINED));
  unsigned before=publications;present(sc,i);assert(publications==before+1);
  assert(last_wait==H(VkSemaphore,7));assert(last_submit.commandBufferCount==1);
 }
 XWindowSwapchain_destroy(H(VkDevice,1),sc);assert(destroyed_pools==1);assert(destroyed_images==3);
 puts("PASS: three-image cycles transfer FOREIGN ownership and preserve reused image contents");
 sc=make_swapchain();reserve_index=-1;uint32_t out=99;unsigned before=submits;
 assert(XWindowSwapchain_acquireNextImage(sc,0,H(VkSemaphore,6),VK_NULL_HANDLE,&out)==VK_TIMEOUT);
 assert(submits==before && out==99);
 reserve_index=0;submit_result=VK_ERROR_OUT_OF_DEVICE_MEMORY;before=cancellations;
 assert(XWindowSwapchain_acquireNextImage(sc,1,H(VkSemaphore,6),VK_NULL_HANDLE,&out)==VK_ERROR_OUT_OF_DEVICE_MEMORY);
 assert(cancellations==before+1);submit_result=VK_SUCCESS;
 acquire(sc,0);assert(last_barrier.oldLayout==VK_IMAGE_LAYOUT_UNDEFINED);
 before=publications;reject_publish=true;submit_result=VK_ERROR_OUT_OF_DEVICE_MEMORY;
 present(sc,0);assert(publications==before);assert(failures>0);submit_result=VK_SUCCESS;
 unsigned prior_pools=destroyed_pools, prior_images=destroyed_images;
 XWindowSwapchain_destroy(H(VkDevice,1),sc);
 assert(destroyed_pools==prior_pools && destroyed_images==prior_images && "failed release leaves guest rendering potentially in flight");
 reject_publish=false;present(sc,0);XWindowSwapchain_destroy(H(VkDevice,1),sc);
 puts("PASS: unavailable slots and submission failures never expose an unfinished image");
 sc=make_swapchain();acquire(sc,0);before=publications;
 wait_result=VK_TIMEOUT;delay_release_only=true;reject_publish=true;unsigned pools=destroyed_pools, images=destroyed_images;
 present(sc,0);assert(publications==before);
 XWindowSwapchain_destroy(H(VkDevice,1),sc);
 assert(destroyed_pools==pools && destroyed_images==images);
 /* Driver completion arrives later; retry teardown safely in this harness. */
 wait_result=VK_SUCCESS;XWindowSwapchain_destroy(H(VkDevice,1),sc);reject_publish=false;
 assert(destroyed_pools==pools+1);
 puts("PASS: delayed GPU completion retains command pools and images until safe destruction");
 return 0;
}
