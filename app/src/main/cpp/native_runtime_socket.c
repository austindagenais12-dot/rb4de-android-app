// SPDX-License-Identifier: GPL-2.0-or-later
#include <jni.h>
#include "rb4dx_input_transport.h"

#include <errno.h>
#include <poll.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

JNIEXPORT jint JNICALL
Java_dev_rb4dx_standalone_NativeRuntimeSocket_openServer(
        JNIEnv* env, jclass ignored, jstring socket_path) {
    (void)ignored;
    if (socket_path == NULL) return -EINVAL;
    const char* path = (*env)->GetStringUTFChars(env, socket_path, NULL);
    if (path == NULL) return -ENOMEM;
    size_t length = strlen(path);
    struct sockaddr_un address = {0};
    if (length == 0 || length >= sizeof(address.sun_path) || path[0] != '/') {
        (*env)->ReleaseStringUTFChars(env, socket_path, path);
        return -ENAMETOOLONG;
    }
    address.sun_family = AF_UNIX;
    memcpy(address.sun_path, path, length + 1);
    (*env)->ReleaseStringUTFChars(env, socket_path, path);

    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -errno;
    socklen_t address_length = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + length + 1);
    if (bind(fd, (struct sockaddr*)&address, address_length) != 0 ||
            listen(fd, 1) != 0) {
        int error = errno;
        close(fd);
        return -error;
    }
    return fd;
}

static jstring read_result(JNIEnv* env, const char* message, int error) {
    char result[160];
    snprintf(result, sizeof(result), "%s errno=%d", message, error);
    return (*env)->NewStringUTF(env, result);
}

JNIEXPORT jstring JNICALL
Java_dev_rb4dx_standalone_NativeRuntimeSocket_readSession(
        JNIEnv* env, jclass ignored, jint server_fd, jint timeout_ms) {
    (void)ignored;
    if (server_fd < 0 || timeout_ms <= 0 || timeout_ms > 60000) {
        return read_result(env, "INVALID_LISTENER", EINVAL);
    }
    struct pollfd server_poll = { .fd = server_fd, .events = POLLIN };
    int waited;
    do {
        waited = poll(&server_poll, 1, timeout_ms);
    } while (waited < 0 && errno == EINTR);
    if (waited == 0) return (*env)->NewStringUTF(env, "NO_CLIENT_CONNECTED");
    if (waited < 0 || !(server_poll.revents & POLLIN)) {
        return read_result(env, "ACCEPT_POLL_FAILED", errno);
    }
    int client = accept4(server_fd, NULL, NULL, SOCK_CLOEXEC);
    if (client < 0) return read_result(env, "ACCEPT_FAILED", errno);

    rb4dx_input_attach(server_fd, client);

    char result[16385];
    size_t used = 0;
    int elapsed = 0;
    while (used < sizeof(result) - 1 && elapsed < timeout_ms) {
        const int interval = 1000;
        struct pollfd poll_client = { .fd = client, .events = POLLIN };
        int state = poll(&poll_client, 1, interval);
        elapsed += interval;
        if (state == 0) continue;
        if (state < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (poll_client.revents & (POLLERR | POLLNVAL)) break;
        if (poll_client.revents & POLLIN) {
            ssize_t count = read(client, result + used, sizeof(result) - 1 - used);
            if (count <= 0) break;
            used += (size_t)count;
        } else if (poll_client.revents & POLLHUP) {
            break;
        }
    }
    rb4dx_input_detach(server_fd, client);
    close(client);
    result[used] = '\0';
    if (!used) return (*env)->NewStringUTF(env, "CLIENT_CONNECTED_NO_FRAMES");
    return (*env)->NewStringUTF(env, result);
}

JNIEXPORT jint JNICALL
Java_dev_rb4dx_standalone_NativeRuntimeSocket_sendInputFrame(
        JNIEnv* env, jclass ignored, jint server_fd, jstring frame) {
    (void)ignored;
    if (frame == NULL) return -EINVAL;
    const char* bytes = (*env)->GetStringUTFChars(env, frame, NULL);
    if (bytes == NULL) return -ENOMEM;
    int sent = rb4dx_input_send(server_fd, bytes, strlen(bytes));
    (*env)->ReleaseStringUTFChars(env, frame, bytes);
    return sent;
}

JNIEXPORT void JNICALL
Java_dev_rb4dx_standalone_NativeRuntimeSocket_closeServer(
        JNIEnv* env, jclass ignored, jint server_fd) {
    (void)env;
    (void)ignored;
    if (server_fd >= 0) close(server_fd);
}
