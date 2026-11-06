/* Real request-thread exit and real guest vt_recv; only ring I/O/JNI are fake. */
#include "swapchain_host.h"
#include <assert.h>
#include <stdio.h>
#define RING_STATUS_EXIT 4
#define HEADER_SIZE 8
typedef struct { int status; int request; bool queued; } RingBuffer;
typedef int MemoryPool;
typedef struct { int size; char* data; } ExtraDataRequest;
typedef struct {
 JMethods jmethods; int status; RingBuffer* serverRing; RingBuffer* clientRing;
 char* inputBuffer; int inputBufferSize; MemoryPool memoryPool;
} VkContext;
typedef void (*HandleRequestFunc)(VkContext*);
static int handled,detached;
static inline void RingBuffer_setStatus(RingBuffer* ring,int status) { ring->status|=status; }
static bool RingBuffer_read(RingBuffer* ring,void* out,int size) {
 if(ring->status&RING_STATUS_EXIT) return false;
 assert(ring->queued && "test would block forever on an open empty ring");
 assert(size==8);int header[]={ring->request,0};memcpy(out,header,8);ring->queued=false;return true;
}
static void* vt_alloc(MemoryPool* p,int n) { (void)p;return malloc(n); }
static void vt_free(MemoryPool* p) { (void)p; }
#include "receive_under_test.c"
static jint detach(JavaVM* vm) { (void)vm;detached++;return 0; }
static const struct JNIInvokeInterface_ vm_table={.DetachCurrentThread=detach};
static JavaVM vm=&vm_table;
static void loadJMethods(JMethods* methods) { methods->jvm=&vm; }
static ExtraDataRequest* waitForExtraDataRequest(VkContext* ctx,uint16_t id) { (void)ctx;(void)id;return NULL; }
static void failed_present(VkContext* ctx) { handled++;ctx->status=VK_ERROR_DEVICE_LOST; }
static HandleRequestFunc getHandleRequestFunc(int code) { assert(code==1);return failed_present; }
#include "thread_under_test.c"
int main(void) {
 RingBuffer client={0},server={.request=1,.queued=true};
 VkContext ctx={.serverRing=&server,.clientRing=&client};
 requestHandlerThread(&ctx);
 assert(handled==1 && detached==1);
 assert((client.status&RING_STATUS_EXIT) && (server.status&RING_STATUS_EXIT) && "failed present must wake both guest IPC rings");
 char* out=NULL;int size=0;MemoryPool pool=0;
 assert(vt_recv(&client,&out,&size,&pool)==VK_ERROR_DEVICE_LOST);
 /* A request transport disconnect takes the same wake-up path. */
 client=(RingBuffer){0};server=(RingBuffer){.status=RING_STATUS_EXIT};ctx.status=0;
 requestHandlerThread(&ctx);assert(client.status&RING_STATUS_EXIT);
 assert(vt_recv(&client,&out,&size,&pool)==VK_ERROR_DEVICE_LOST);
 puts("PASS: failed present and transport disconnect wake guest with device-lost instead of hanging");
}
