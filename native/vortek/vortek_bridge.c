// SPDX-License-Identifier: LGPL-2.1-or-later
// App-private AF_UNIX endpoint for the pinned Vortek Android JNI renderer.
// The backend is real; its GPU images are not yet presented on an Android view.
#define _GNU_SOURCE
#include <jni.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <errno.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#define TAG "RB4DX.Vortek.Server"
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define MAX_WINDOWS 32

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
};

typedef struct WindowBuffer {
    int id;
    int width;
    int height;
    AHardwareBuffer* buffer;
} WindowBuffer;

static pthread_mutex_t buffer_mutex = PTHREAD_MUTEX_INITIALIZER;
static WindowBuffer buffers[MAX_WINDOWS];

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

static void* connection_thread(void* arg) {
    Connection* conn = arg;
    Server* server = conn->server;
    JNIEnv* env = NULL;
    jlong context = 0;
    bool attached = (*server->vm)->AttachCurrentThread(server->vm, &env, NULL) == JNI_OK;

    if (attached) {
        uint32_t header[2];
        while (read_all(conn->fd, header, sizeof(header))) {
            uint32_t request = header[0];
            uint32_t length = header[1];
            if (request == 1 && length == 0 && context == 0) {
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
        JNIEnv* env, jobject component, jint window_id, jboolean prefer_bgra,
        jint width, jint height) {
    (void)env; (void)component; (void)prefer_bgra;
    if (width < 1 || height < 1 || width > 8192 || height > 8192) return 0;
    pthread_mutex_lock(&buffer_mutex);
    int free_slot = -1;
    for (int i = 0; i < MAX_WINDOWS; ++i) {
        if (buffers[i].buffer && buffers[i].id == window_id &&
                buffers[i].width == width && buffers[i].height == height) {
            jlong result = (jlong)(uintptr_t)buffers[i].buffer;
            pthread_mutex_unlock(&buffer_mutex);
            return result;
        }
        if (!buffers[i].buffer) free_slot = i;
    }
    jlong result = 0;
    if (free_slot >= 0) {
        AHardwareBuffer_Desc desc = {
            .width = (uint32_t)width, .height = (uint32_t)height, .layers = 1,
            .format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
            .usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
                     AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT
        };
        AHardwareBuffer* buffer = NULL;
        if (AHardwareBuffer_allocate(&desc, &buffer) == 0 && buffer) {
            buffers[free_slot] = (WindowBuffer){
                .id = window_id, .width = width, .height = height, .buffer = buffer
            };
            result = (jlong)(uintptr_t)buffer;
        }
    }
    pthread_mutex_unlock(&buffer_mutex);
    return result;
}

JNIEXPORT void JNICALL
Java_com_winlator_xenvironment_components_VortekRendererComponent_releaseNativeBuffers(
        JNIEnv* env, jobject component) {
    (void)env; (void)component;
    pthread_mutex_lock(&buffer_mutex);
    for (int i = 0; i < MAX_WINDOWS; ++i) {
        if (buffers[i].buffer) AHardwareBuffer_release(buffers[i].buffer);
        memset(&buffers[i], 0, sizeof(buffers[i]));
    }
    pthread_mutex_unlock(&buffer_mutex);
}
