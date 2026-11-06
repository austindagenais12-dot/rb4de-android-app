// SPDX-License-Identifier: LGPL-2.1-or-later
// App-private AF_UNIX endpoint for the pinned Vortek Android JNI renderer.
// Android 16 live output is opt-in and fenced; the stopped-producer preview remains.
#define _GNU_SOURCE
#include <jni.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <android/api-level.h>
#include <dlfcn.h>
#include <errno.h>
#include <poll.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include "bachata_vortek_protocol.h"
#include "rb4dx_window_images.h"
#include "rb4dx_frame_ring.h"
#include "rb4dx_surface_control_compat.h"
#include "vulkan_wrapper.h"

#define TAG "RB4DX.Vortek.Server"
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// The public JNI exports from the pinned upstream Vortek renderer.
extern jlong Java_com_winlator_xenvironment_components_VortekRendererComponent_createVkContext(
        JNIEnv*, jobject, jint, jobject);
extern void Java_com_winlator_xenvironment_components_VortekRendererComponent_destroyVkContext(
        JNIEnv*, jobject, jlong);
extern jboolean Java_com_winlator_xenvironment_components_VortekRendererComponent_handleExtraDataRequest(
        JNIEnv*, jobject, jlong, jint, jint);

typedef struct Server Server;
typedef struct Connection {
    Server* server;
    int fd;
    pthread_t thread;
    struct Connection* next;
} Connection;

struct Server {
    JavaVM* vm;
    jobject component;
    jobject options;
    int listen_fd;
    char socket_path[sizeof(((struct sockaddr_un*)0)->sun_path)];
    pthread_t accept_thread;
    pthread_mutex_t mutex;
    pthread_cond_t changed;
    Connection* connections;
    bool stopping;
    int accepted;
    int contexts_created;
    int handshakes_ok;
    int handshakes_failed;
};

static pthread_mutex_t buffer_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t buffer_available = PTHREAD_COND_INITIALIZER;
static Rb4dxWindowImages window_images;

// NDK 26 does not declare API 36's callback-aware buffer submission. Resolve
// it dynamically so the APK can continue to run on earlier Android releases.
typedef void (*Rb4dxOnBufferRelease)(void*, int);
typedef void (*SetBufferWithReleaseFn)(ASurfaceTransaction*, ASurfaceControl*,
        AHardwareBuffer*, int, void*, Rb4dxOnBufferRelease);
static SetBufferWithReleaseFn set_buffer_with_release;

typedef struct {
    uint64_t epoch;
    int window_id;
    Rb4dxFrameLease lease;
    AHardwareBuffer* held_buffer;
} ReleaseContext;

static ASurfaceControl* live_control;
static Rb4dxFrameRing live_ring;
static Rb4dxFrameLease image_leases[3];
static uint64_t live_epoch;
static int live_window_id;
static bool live_active;
static bool live_surface_attached;
static unsigned int live_frames_gpu_complete;
static unsigned int live_frames_posted;
static unsigned int live_frames_released;
static unsigned int live_frames_dropped;
static unsigned int live_gpu_fence_timeouts;
static unsigned int live_gpu_fence_failures;
static unsigned int live_release_fence_timeouts;
static unsigned int live_pending_callbacks;
static char live_last_error[64] = "NONE";

static void live_error_locked(const char* error) {
    snprintf(live_last_error, sizeof(live_last_error), "%s", error);
}

// Android's buffer release callback can arrive on an arbitrary Binder thread.
// The callback must wait for the compositor's fence, then and only then free
// the producer slot. The bounded timeout intentionally fails closed.
static void on_live_buffer_released(void* argument, int release_fence_fd) {
    ReleaseContext* callback = (ReleaseContext*)argument;
    bool safe_to_reuse = release_fence_fd < 0;
    if (release_fence_fd >= 0) {
        struct pollfd poll_fd = {.fd = release_fence_fd, .events = POLLIN};
        int ready;
        do { ready = poll(&poll_fd, 1, 3000); } while (ready < 0 && errno == EINTR);
        safe_to_reuse = ready > 0 && (poll_fd.revents & POLLIN) != 0;
        close(release_fence_fd);
    }
    pthread_mutex_lock(&buffer_mutex);
    if (callback->epoch == live_epoch && callback->window_id == live_window_id) {
        if (safe_to_reuse && rb4dx_frame_ring_release(&live_ring, callback->lease)) {
            live_frames_released++;
            pthread_cond_broadcast(&buffer_available);
        } else if (!safe_to_reuse) {
            live_release_fence_timeouts++;
            live_error_locked("COMPOSITOR_RELEASE_FENCE_TIMEOUT");
        }
    }
    if (live_pending_callbacks) live_pending_callbacks--;
    pthread_mutex_unlock(&buffer_mutex);
    AHardwareBuffer_release(callback->held_buffer);
    free(callback);
}

static void detach_live_control(void) {
    pthread_mutex_lock(&buffer_mutex);
    live_active = false;
    // Preserve evidence of a successful attachment for the completed probe.
    rb4dx_frame_ring_close(&live_ring);
    pthread_cond_broadcast(&buffer_available);
    ASurfaceControl* control = live_control;
    live_control = NULL;
    pthread_mutex_unlock(&buffer_mutex);
    if (control) {
        ASurfaceTransaction* tx = ASurfaceTransaction_create();
        if (tx) {
            ASurfaceTransaction_setVisibility(tx, control, RB4DX_SURFACE_HIDE);
            ASurfaceTransaction_reparent(tx, control, NULL);
            ASurfaceTransaction_apply(tx);
            ASurfaceTransaction_delete(tx);
        }
        ASurfaceControl_release(control);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativePrepareLiveSurface(
        JNIEnv* env, jobject component, jobject surface, jint image_width,
        jint image_height, jint target_width, jint target_height,
        jint offset_x, jint offset_y) {
    (void)component;
    detach_live_control();
    pthread_mutex_lock(&buffer_mutex);
    live_epoch++;
    // Tokens are never reused across sessions, even after the ring is reset.
    memset(&live_ring, 0, sizeof(live_ring));
    memset(image_leases, 0, sizeof(image_leases));
    live_window_id = INT32_MIN;
    live_surface_attached = false;
    live_frames_gpu_complete = 0;
    live_frames_posted = 0;
    live_frames_released = 0;
    live_frames_dropped = 0;
    live_gpu_fence_timeouts = 0;
    live_gpu_fence_failures = 0;
    live_release_fence_timeouts = 0;
    live_error_locked("NONE");
    pthread_mutex_unlock(&buffer_mutex);
    if (!surface || image_width <= 0 || image_height <= 0 ||
        target_width <= 0 || target_height <= 0 || android_get_device_api_level() < 36) {
        pthread_mutex_lock(&buffer_mutex);
        live_error_locked("ANDROID_16_SURFACE_REQUIRED");
        pthread_mutex_unlock(&buffer_mutex);
        return JNI_FALSE;
    }
    set_buffer_with_release = (SetBufferWithReleaseFn)
            dlsym(RTLD_DEFAULT, "ASurfaceTransaction_setBufferWithRelease");
    if (!set_buffer_with_release) {
        pthread_mutex_lock(&buffer_mutex);
        live_error_locked("API_36_RELEASE_CALLBACK_UNAVAILABLE");
        pthread_mutex_unlock(&buffer_mutex);
        return JNI_FALSE;
    }
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return JNI_FALSE;
    ASurfaceControl* control = ASurfaceControl_createFromWindow(window, "RB4DX Live Game Frame");
    ANativeWindow_release(window);
    if (!control) {
        pthread_mutex_lock(&buffer_mutex);
        live_error_locked("SURFACE_CONTROL_CREATION_FAILED");
        pthread_mutex_unlock(&buffer_mutex);
        return JNI_FALSE;
    }
    ASurfaceTransaction* tx = ASurfaceTransaction_create();
    if (!tx) {
        ASurfaceControl_release(control);
        return JNI_FALSE;
    }
    ASurfaceTransaction_setScale(tx, control, (float)target_width / (float)image_width,
            (float)target_height / (float)image_height);
    ASurfaceTransaction_setPosition(tx, control, offset_x, offset_y);
    ASurfaceTransaction_setVisibility(tx, control, RB4DX_SURFACE_SHOW);
    ASurfaceTransaction_apply(tx);
    ASurfaceTransaction_delete(tx);
    pthread_mutex_lock(&buffer_mutex);
    live_control = control;
    live_active = true;
    live_surface_attached = true;
    pthread_mutex_unlock(&buffer_mutex);
    LOG("live_surface_attached image=%dx%d target=%dx%d offset=%d,%d epoch=%llu", image_width,
            image_height, target_width, target_height, offset_x, offset_y,
            (unsigned long long)live_epoch);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeDetachLiveSurface(
        JNIEnv* env, jobject component) {
    (void)env; (void)component;
    detach_live_control();
}

JNIEXPORT jint JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeAcquireWindowImage(
        JNIEnv* env, jobject component, jint window_id, jint count, jlong timeout_ns) {
    (void)env; (void)component;
    pthread_mutex_lock(&buffer_mutex);
    // -2: legacy path, not live. -1: bounded timeout, Vulkan VK_TIMEOUT.
    if (!live_active || count < 2 || count > 3) {
        if (live_active && (count < 2 || count > 3))
            live_error_locked("UNSUPPORTED_SWAPCHAIN_IMAGE_COUNT");
        pthread_mutex_unlock(&buffer_mutex);
        return -2;
    }
    if (!live_ring.initialized) {
        if (!rb4dx_frame_ring_init_seeded(&live_ring, (uint8_t)count, live_epoch << 40)) {
            pthread_mutex_unlock(&buffer_mutex);
            return -1;
        }
        live_window_id = window_id;
    }
    if (live_window_id != window_id || live_ring.capacity != count) {
        pthread_mutex_unlock(&buffer_mutex);
        return -2;
    }
    // Keep the server stoppable even if a guest supplies UINT64_MAX.
    // UINT64_MAX is Vulkan's 'infinite' timeout and arrives as a negative
    // signed JNI long. Never turn that into an immediate acquire failure.
    uint64_t bounded_ns = timeout_ns < 0 ? 1000000000ULL : (uint64_t)timeout_ns;
    if (bounded_ns > 1000000000ULL) bounded_ns = 1000000000ULL;
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += (time_t)(bounded_ns / 1000000000ULL);
    deadline.tv_nsec += (long)(bounded_ns % 1000000000ULL);
    if (deadline.tv_nsec >= 1000000000L) { deadline.tv_sec++; deadline.tv_nsec -= 1000000000L; }
    Rb4dxFrameLease lease;
    bool acquired = rb4dx_frame_ring_acquire(&live_ring, &lease);
    while (live_active && !acquired) {
        if (bounded_ns == 0 || pthread_cond_timedwait(&buffer_available, &buffer_mutex,
                    &deadline) == ETIMEDOUT) break;
        acquired = rb4dx_frame_ring_acquire(&live_ring, &lease);
    }
    if (!live_active || live_ring.closed) {
        pthread_mutex_unlock(&buffer_mutex);
        return -1;
    }
    if (acquired) image_leases[lease.slot] = lease;
    else live_frames_dropped++;
    pthread_mutex_unlock(&buffer_mutex);
    return acquired ? (jint)lease.slot : -1;
}

JNIEXPORT void JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeCancelWindowImage(
        JNIEnv* env, jobject component, jint window_id, jint index) {
    (void)env; (void)component;
    pthread_mutex_lock(&buffer_mutex);
    if (window_id == live_window_id && index >= 0 && index < live_ring.capacity &&
        rb4dx_frame_ring_cancel(&live_ring, image_leases[index])) {
        live_frames_dropped++;
        pthread_cond_broadcast(&buffer_available);
    }
    pthread_mutex_unlock(&buffer_mutex);
}

JNIEXPORT void JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeGpuPresentFailure(
        JNIEnv* env, jobject component, jint window_id, jint image_index, jint vk_result) {
    (void)env; (void)component; (void)image_index;
    pthread_mutex_lock(&buffer_mutex);
    if (live_active && window_id == live_window_id) {
        if (vk_result == 2) live_gpu_fence_timeouts++; // VK_TIMEOUT
        else live_gpu_fence_failures++;
        snprintf(live_last_error, sizeof(live_last_error), "VK_PRESENT_WAIT_RESULT_%d", vk_result);
    }
    pthread_mutex_unlock(&buffer_mutex);
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeLivePublish(
        JNIEnv* env, jobject component, jint window_id, jint index) {
    (void)env; (void)component;
    pthread_mutex_lock(&buffer_mutex);
    if (!live_active || window_id != live_window_id || index < 0 ||
        index >= live_ring.capacity || !live_control) {
        pthread_mutex_unlock(&buffer_mutex);
        return JNI_FALSE;
    }
    const Rb4dxFrameLease lease = image_leases[index];
    if (!rb4dx_frame_ring_gpu_complete(&live_ring, lease)) {
        pthread_mutex_unlock(&buffer_mutex);
        return JNI_FALSE;
    }
    live_frames_gpu_complete++;
    AHardwareBuffer* buffer = NULL;
    for (size_t i=0; i<window_images.count; ++i) {
        if (window_images.images[i].window_id == window_id &&
            window_images.images[i].image_index == (uint8_t)index) {
            buffer = (AHardwareBuffer*)window_images.images[i].handle;
            break;
        }
    }
    ReleaseContext* context = buffer ? calloc(1, sizeof(*context)) : NULL;
    ASurfaceTransaction* tx = context ? ASurfaceTransaction_create() : NULL;
    if (!tx || !rb4dx_frame_ring_present(&live_ring, lease)) {
        if (tx) ASurfaceTransaction_delete(tx);
        free(context);
        rb4dx_frame_ring_cancel(&live_ring, lease);
        live_frames_dropped++;
        pthread_cond_broadcast(&buffer_available);
        pthread_mutex_unlock(&buffer_mutex);
        return JNI_FALSE;
    }
    ASurfaceControl* control = live_control;
    ASurfaceControl_acquire(control);
    AHardwareBuffer_acquire(buffer);
    *context = (ReleaseContext){.epoch=live_epoch,.window_id=window_id,
            .lease=lease,.held_buffer=buffer};
    live_pending_callbacks++;
    pthread_mutex_unlock(&buffer_mutex);
    set_buffer_with_release(tx, control, buffer, -1, context, on_live_buffer_released);
    ASurfaceTransaction_apply(tx);
    ASurfaceTransaction_delete(tx);
    ASurfaceControl_release(control);
    pthread_mutex_lock(&buffer_mutex);
    live_frames_posted++;
    pthread_mutex_unlock(&buffer_mutex);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeLiveReport(
        JNIEnv* env, jobject component) {
    (void)component;
    char report[650];
    pthread_mutex_lock(&buffer_mutex);
    snprintf(report, sizeof(report),
            "android_surface_attached=%s\n"
            "android_live_game_frame_posted=%s\n"
            "android_live_game_frames_gpu_complete=%u\n"
            "android_live_game_frames_posted=%u\n"
            "android_live_game_frames_released=%u\n"
            "android_live_game_frames_dropped=%u\n"
            "android_live_gpu_fence_timeouts=%u\n"
            "android_live_gpu_fence_failures=%u\n"
            "android_live_release_fence_timeouts=%u\n"
            "android_live_pending_callbacks=%u\n"
            "android_live_last_error=%s\n",
            live_surface_attached ? "true" : "false", live_frames_posted ? "true" : "false",
            live_frames_gpu_complete, live_frames_posted, live_frames_released,
            live_frames_dropped, live_gpu_fence_timeouts, live_gpu_fence_failures,
            live_release_fence_timeouts, live_pending_callbacks,
            live_last_error);
    pthread_mutex_unlock(&buffer_mutex);
    return (*env)->NewStringUTF(env, report);
}

static bool read_all(int fd, void* destination, size_t bytes) {
    unsigned char* p = destination;
    while (bytes) {
        ssize_t n = read(fd, p, bytes);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return false;
        p += n;
        bytes -= (size_t)n;
    }
    return true;
}

static bool write_all(int fd, const void* source, size_t bytes) {
    const unsigned char* p = source;
    while (bytes) {
        ssize_t n = send(fd, p, bytes, MSG_NOSIGNAL);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return false;
        p += n;
        bytes -= (size_t)n;
    }
    return true;
}

static bool handle_handshake(Connection* conn, uint32_t length) {
    Server* server = conn->server;
    BachataVortekHandshakeRequest request;
    if (length != sizeof(request) || !read_all(conn->fd, &request, sizeof(request))) {
        LOG("handshake_failed reason=invalid_request_length_or_body bytes=%u", length);
        pthread_mutex_lock(&server->mutex);
        server->handshakes_failed++;
        pthread_mutex_unlock(&server->mutex);
        return false;
    }

    const bool compatible = request.magic == BACHATA_VORTEK_MAGIC &&
            request.proto_major == BACHATA_VORTEK_PROTO_MAJOR &&
            request.proto_minor == BACHATA_VORTEK_PROTO_MINOR &&
            request.pointer_size == sizeof(void*) &&
            request.endianness == BACHATA_VORTEK_ENDIAN_LITTLE;
    const uint32_t response_header[] = {
        REQUEST_CODE_BACHATA_HANDSHAKE, sizeof(BachataVortekHandshakeResponse)
    };
    const BachataVortekHandshakeResponse response = {
        .magic = BACHATA_VORTEK_MAGIC,
        .proto_major = BACHATA_VORTEK_PROTO_MAJOR,
        .proto_minor = BACHATA_VORTEK_PROTO_MINOR,
        .status = compatible ? BACHATA_VORTEK_HANDSHAKE_OK : BACHATA_VORTEK_HANDSHAKE_MISMATCH,
        .reserved = 0
    };
    bool sent = write_all(conn->fd, response_header, sizeof(response_header)) &&
            write_all(conn->fd, &response, sizeof(response));
    pthread_mutex_lock(&server->mutex);
    if (compatible && sent) server->handshakes_ok++;
    else server->handshakes_failed++;
    pthread_mutex_unlock(&server->mutex);
    LOG("handshake_%s protocol=%u.%u pointer_size=%u endian=%u sent=%d",
            compatible && sent ? "ok" : "failed", (unsigned)request.proto_major,
            (unsigned)request.proto_minor, (unsigned)request.pointer_size,
            (unsigned)request.endianness, sent);
    return compatible && sent;
}

static void* connection_thread(void* arg) {
    Connection* conn = arg;
    Server* server = conn->server;
    JNIEnv* env = NULL;
    jlong context = 0;
    bool handshook = false;
    bool attached = (*server->vm)->AttachCurrentThread(server->vm, &env, NULL) == JNI_OK;

    if (attached) {
        uint32_t header[2];
        while (read_all(conn->fd, header, sizeof(header))) {
            uint32_t request = header[0];
            uint32_t length = header[1];
            if (request == REQUEST_CODE_BACHATA_HANDSHAKE && !handshook && !context) {
                if (!handle_handshake(conn, length)) break;
                handshook = true;
            } else if (request == 1 && length == 0 && context == 0) {
                context = Java_com_winlator_xenvironment_components_VortekRendererComponent_createVkContext(
                        env, server->component, conn->fd, server->options);
                LOG("context_created=%d", context != 0);
                if (context) {
                    pthread_mutex_lock(&server->mutex);
                    server->contexts_created++;
                    pthread_mutex_unlock(&server->mutex);
                }
                if (!context || (*env)->ExceptionCheck(env)) break;
            } else if ((request >> 16) == 2 && context && length <= (128U * 1024U * 1024U)) {
                if (!Java_com_winlator_xenvironment_components_VortekRendererComponent_handleExtraDataRequest(
                            env, server->component, context, (jint)(request & 0xffff), (jint)length)) break;
            } else {
                LOG("unsupported_request=%u bytes=%u", request, length);
                break;
            }
            if ((*env)->ExceptionCheck(env)) break;
        }
        if (context) {
            Java_com_winlator_xenvironment_components_VortekRendererComponent_destroyVkContext(
                    env, server->component, context);
        }
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        (*server->vm)->DetachCurrentThread(server->vm);
    }
    shutdown(conn->fd, SHUT_RDWR);
    close(conn->fd);

    pthread_mutex_lock(&server->mutex);
    Connection** cursor = &server->connections;
    while (*cursor && *cursor != conn) cursor = &(*cursor)->next;
    if (*cursor == conn) *cursor = conn->next;
    pthread_cond_signal(&server->changed);
    pthread_mutex_unlock(&server->mutex);
    free(conn);
    return NULL;
}

static void* accept_thread(void* arg) {
    Server* server = arg;
    for (;;) {
        int fd = accept(server->listen_fd, NULL, NULL);
        if (fd < 0) {
            if (errno == EINTR) continue;
            break;
        }
        Connection* conn = calloc(1, sizeof(*conn));
        if (!conn) {
            close(fd);
            continue;
        }
        conn->fd = fd;
        conn->server = server;
        pthread_mutex_lock(&server->mutex);
        if (server->stopping) {
            pthread_mutex_unlock(&server->mutex);
            close(fd);
            free(conn);
            break;
        }
        conn->next = server->connections;
        server->connections = conn;
        server->accepted++;
        pthread_mutex_unlock(&server->mutex);
        LOG("client_connected total=%d", server->accepted);
        if (pthread_create(&conn->thread, NULL, connection_thread, conn) != 0) {
            pthread_mutex_lock(&server->mutex);
            server->connections = conn->next;
            pthread_cond_signal(&server->changed);
            pthread_mutex_unlock(&server->mutex);
            close(fd);
            free(conn);
        } else {
            pthread_detach(conn->thread);
        }
    }
    return NULL;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_startNativeServer(
        JNIEnv* env, jobject component, jstring socket_path, jobject options) {
    if (!socket_path || !options) return 0;
    const char* path = (*env)->GetStringUTFChars(env, socket_path, NULL);
    if (!path) return 0;
    size_t len = strlen(path);
    if (len == 0 || len >= sizeof(((struct sockaddr_un*)0)->sun_path)) {
        (*env)->ReleaseStringUTFChars(env, socket_path, path);
        return 0;
    }
    Server* server = calloc(1, sizeof(*server));
    if (!server) {
        (*env)->ReleaseStringUTFChars(env, socket_path, path);
        return 0;
    }
    (*env)->GetJavaVM(env, &server->vm);
    server->component = (*env)->NewGlobalRef(env, component);
    server->options = (*env)->NewGlobalRef(env, options);
    server->listen_fd = -1;
    memcpy(server->socket_path, path, len + 1);
    (*env)->ReleaseStringUTFChars(env, socket_path, path);
    pthread_mutex_init(&server->mutex, NULL);
    pthread_cond_init(&server->changed, NULL);

    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) goto failure;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    memcpy(address.sun_path, server->socket_path, len + 1);
    // Only caller-created, app-private sockets are accepted by Java.
    if (bind(fd, (struct sockaddr*)&address,
                (socklen_t)(offsetof(struct sockaddr_un, sun_path) + len + 1)) != 0 ||
            listen(fd, 8) != 0) {
        close(fd);
        goto failure;
    }
    server->listen_fd = fd;
    if (pthread_create(&server->accept_thread, NULL, accept_thread, server) == 0) {
        LOG("ready socket=%s", server->socket_path);
        return (jlong)(uintptr_t)server;
    }
    close(fd);
    server->listen_fd = -1;
failure:
    unlink(server->socket_path);
    pthread_cond_destroy(&server->changed);
    pthread_mutex_destroy(&server->mutex);
    (*env)->DeleteGlobalRef(env, server->component);
    (*env)->DeleteGlobalRef(env, server->options);
    free(server);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeConnectedClients(
        JNIEnv* env, jobject component, jlong ptr) {
    (void)env; (void)component;
    if (!ptr) return 0;
    Server* server = (Server*)(uintptr_t)ptr;
    pthread_mutex_lock(&server->mutex);
    int accepted = server->accepted;
    pthread_mutex_unlock(&server->mutex);
    return accepted;
}

JNIEXPORT jint JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeCreatedContexts(
        JNIEnv* env, jobject component, jlong ptr) {
    (void)env; (void)component;
    if (!ptr) return 0;
    Server* server = (Server*)(uintptr_t)ptr;
    pthread_mutex_lock(&server->mutex);
    int created = server->contexts_created;
    pthread_mutex_unlock(&server->mutex);
    return created;
}

JNIEXPORT jint JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeHandshakesOk(
        JNIEnv* env, jobject component, jlong ptr) {
    (void)env; (void)component;
    if (!ptr) return 0;
    Server* server = (Server*)(uintptr_t)ptr;
    pthread_mutex_lock(&server->mutex);
    int count = server->handshakes_ok;
    pthread_mutex_unlock(&server->mutex);
    return count;
}

JNIEXPORT jint JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeHandshakesFailed(
        JNIEnv* env, jobject component, jlong ptr) {
    (void)env; (void)component;
    if (!ptr) return 0;
    Server* server = (Server*)(uintptr_t)ptr;
    pthread_mutex_lock(&server->mutex);
    int count = server->handshakes_failed;
    pthread_mutex_unlock(&server->mutex);
    return count;
}

JNIEXPORT void JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_stopNativeServer(
        JNIEnv* env, jobject component, jlong ptr) {
    (void)component;
    if (!ptr) return;
    Server* server = (Server*)(uintptr_t)ptr;
    pthread_mutex_lock(&server->mutex);
    server->stopping = true;
    pthread_mutex_unlock(&server->mutex);
    shutdown(server->listen_fd, SHUT_RDWR);
    close(server->listen_fd);
    pthread_join(server->accept_thread, NULL);
    pthread_mutex_lock(&server->mutex);
    for (Connection* c = server->connections; c; c = c->next) shutdown(c->fd, SHUT_RDWR);
    while (server->connections) pthread_cond_wait(&server->changed, &server->mutex);
    pthread_mutex_unlock(&server->mutex);
    unlink(server->socket_path);
    (*env)->DeleteGlobalRef(env, server->component);
    (*env)->DeleteGlobalRef(env, server->options);
    pthread_cond_destroy(&server->changed);
    pthread_mutex_destroy(&server->mutex);
    free(server);
}

JNIEXPORT jlong JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_acquireNativeBuffer(
        JNIEnv* env, jobject component, jint window_id, jint image_index, jboolean prefer_bgra,
        jint width, jint height) {
    (void)env; (void)component; (void)prefer_bgra;
    if (width < 1 || height < 1 || width > 8192 || height > 8192 ||
        image_index < 0 || image_index > 2) return 0;
    pthread_mutex_lock(&buffer_mutex);
    uintptr_t existing = rb4dx_window_images_lookup(&window_images, window_id,
            (uint8_t)image_index, width, height);
    jlong result = (jlong)existing;
    if (!existing && window_images.count < RB4DX_MAX_WINDOW_IMAGES) {
        AHardwareBuffer_Desc desc = {
            .width = (uint32_t)width, .height = (uint32_t)height, .layers = 1,
            .format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
            .usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
                     AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT
        };
        AHardwareBuffer* buffer = NULL;
        if (AHardwareBuffer_allocate(&desc, &buffer) == 0 && buffer) {
            if (rb4dx_window_images_record(&window_images, window_id,
                    (uint8_t)image_index, width, height, (uintptr_t)buffer)) {
                result = (jlong)(uintptr_t)buffer;
            } else {
                AHardwareBuffer_release(buffer);
            }
        }
    }
    pthread_mutex_unlock(&buffer_mutex);
    return result;
}

// Remember the exact swapchain image selected by vkQueuePresentKHR.
// This notification is not evidence that the GPU fence has signalled.
JNIEXPORT jboolean JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeNotePresentedImage(
        JNIEnv* env, jobject component, jint window_id, jint image_index) {
    (void)env; (void)component;
    if (image_index < 0 || image_index > 2) return JNI_FALSE;
    pthread_mutex_lock(&buffer_mutex);
    bool found = rb4dx_window_images_presented(&window_images, window_id, (uint8_t)image_index);
    pthread_mutex_unlock(&buffer_mutex);
    return found ? JNI_TRUE : JNI_FALSE;
}

// Convert the last presented app-owned AHardwareBuffer to a Java reference. The
// Vulkan request threads have to be stopped before calling this method for
// diagnostic display, otherwise the producer may modify the buffer while
// Android's hardware canvas is sampling it.
JNIEXPORT jobject JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_getLastPresentedNativeHardwareBuffer(
        JNIEnv* env, jobject component) {
    (void)component;
    jobject result = NULL;
    pthread_mutex_lock(&buffer_mutex);
    uintptr_t handle = 0;
    if (rb4dx_window_images_preview(&window_images, &handle)) {
        result = AHardwareBuffer_toHardwareBuffer(env, (AHardwareBuffer*)handle);
    }
    pthread_mutex_unlock(&buffer_mutex);
    return result;
}

JNIEXPORT void JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_releaseNativeBuffers(
        JNIEnv* env, jobject component) {
    (void)env; (void)component;
    pthread_mutex_lock(&buffer_mutex);
    for (size_t i = 0; i < window_images.count; ++i) {
        AHardwareBuffer_release((AHardwareBuffer*)window_images.images[i].handle);
    }
    rb4dx_window_images_clear(&window_images);
    pthread_mutex_unlock(&buffer_mutex);
}

// Vortek upstream does not reject a failed custom Vulkan driver load. Guard
// its first Vulkan call before a worker thread can call through a null pointer.
extern VulkanWrapper vulkanWrapper;
JNIEXPORT jboolean JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_nativeVulkanReady(
        JNIEnv* env, jobject component) {
    (void)env;
    (void)component;
    const bool create = vulkanWrapper.vkCreateInstance != NULL;
    const bool extensions = vulkanWrapper.vkEnumerateInstanceExtensionProperties != NULL;
    const bool instance_proc = vulkanWrapper.vkGetInstanceProcAddr != NULL;
    const bool device_proc = vulkanWrapper.vkGetDeviceProcAddr != NULL;
    if (!(create && extensions && instance_proc && device_proc)) {
        LOG("vulkan_wrapper_incomplete create_instance=%d enumerate_extensions=%d "
                "instance_proc=%d device_proc=%d", create, extensions,
                instance_proc, device_proc);
    }
    return create && extensions && instance_proc && device_proc ? JNI_TRUE : JNI_FALSE;
}
