// SPDX-License-Identifier: GPL-2.0-or-later
#include <jni.h>
#include "rb4dx_input_transport.h"
#include "rb4dx_socket_cancel.h"

#include <errno.h>
#include <poll.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

enum { POLL_INTERVAL_MS = 250 };

static int64_t monotonic_ms(void) {
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) return 0;
    return (int64_t)now.tv_sec * 1000 + now.tv_nsec / 1000000;
}

static int bounded_poll_ms(int64_t deadline) {
    const int64_t remaining = deadline - monotonic_ms();
    if (remaining <= 0) return 0;
    return (int)(remaining < POLL_INTERVAL_MS ? remaining : POLL_INTERVAL_MS);
}

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
    rb4dx_socket_cancel_reset(fd);
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
    if (server_fd < 0 || timeout_ms <= 0 || timeout_ms > 120000) {
        return read_result(env, "INVALID_LISTENER", EINVAL);
    }
    // One monotonic deadline spans both accept and protocol reads. The
    // 250-ms slices let an Android Stop cancel a pre-connect listener.
    const int64_t deadline = monotonic_ms() + timeout_ms;
    int waited = 0;
    struct pollfd server_poll = { .fd = server_fd, .events = POLLIN };
    while (!rb4dx_socket_cancelled(server_fd)) {
        int interval = bounded_poll_ms(deadline);
        if (interval <= 0) break;
        waited = poll(&server_poll, 1, interval);
        if (waited < 0 && errno == EINTR) continue;
        if (waited < 0) return read_result(env, "ACCEPT_POLL_FAILED", errno);
        if (waited > 0) {
            if (!(server_poll.revents & POLLIN)) {
                return read_result(env, "ACCEPT_POLL_FAILED", EIO);
            }
            break;
        }
    }
    if (rb4dx_socket_cancelled(server_fd)) {
        return (*env)->NewStringUTF(env, "LISTENER_CANCELLED");
    }
    if (waited <= 0) return (*env)->NewStringUTF(env, "NO_CLIENT_CONNECTED");
    int client = accept4(server_fd, NULL, NULL, SOCK_CLOEXEC);
    if (client < 0) return read_result(env, "ACCEPT_FAILED", errno);

    rb4dx_input_attach(server_fd, client);

    char result[16385];
    size_t used = 0;
    while (!rb4dx_socket_cancelled(server_fd)) {
        int interval = bounded_poll_ms(deadline);
        if (interval <= 0) break;
        struct pollfd poll_client = { .fd = client, .events = POLLIN };
        int state = poll(&poll_client, 1, interval);
        if (state == 0) continue;
        if (state < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (poll_client.revents & (POLLERR | POLLNVAL)) break;
        if (poll_client.revents & POLLIN) {
            // Retain the earliest protocol events for accurate checkpoints,
            // but keep draining the socket after the 16-KiB report fills;
            // otherwise gamepad forwarding would silently stop early.
            char buffer[1024];
            ssize_t count = read(client, buffer, sizeof(buffer));
            if (count <= 0) break;
            size_t remaining = sizeof(result) - 1 - used;
            size_t copied = (size_t)count < remaining ? (size_t)count : remaining;
            if (copied > 0) {
                memcpy(result + used, buffer, copied);
                used += copied;
            }
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

JNIEXPORT void JNICALL
Java_dev_rb4dx_standalone_NativeRuntimeSocket_cancelSession(
        JNIEnv* env, jclass ignored, jint server_fd) {
    (void)env;
    (void)ignored;
    rb4dx_socket_cancel_request(server_fd);
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
