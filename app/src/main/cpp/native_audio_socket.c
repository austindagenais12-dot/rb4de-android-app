// SPDX-License-Identifier: GPL-2.0-or-later
#include <jni.h>

#include <errno.h>
#include <poll.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

JNIEXPORT jint JNICALL
Java_dev_rb4dx_standalone_BachataAudioSocket_nativeOpen(
        JNIEnv* env, jclass ignored, jstring socket_path) {
    (void)ignored;
    if (socket_path == NULL) return -EINVAL;
    const char* path = (*env)->GetStringUTFChars(env, socket_path, NULL);
    if (path == NULL) return -ENOMEM;
    size_t length = strlen(path);
    struct sockaddr_un address = {0};
    if (length == 0 || path[0] != '/' || length >= sizeof(address.sun_path)) {
        (*env)->ReleaseStringUTFChars(env, socket_path, path);
        return -ENAMETOOLONG;
    }
    address.sun_family = AF_UNIX;
    memcpy(address.sun_path, path, length + 1);
    (*env)->ReleaseStringUTFChars(env, socket_path, path);
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -errno;
    if (bind(fd, (struct sockaddr*)&address,
            (socklen_t)(offsetof(struct sockaddr_un, sun_path) + length + 1)) != 0
            || listen(fd, 4) != 0) {
        int code = errno;
        close(fd);
        return -code;
    }
    return fd;
}

JNIEXPORT jint JNICALL
Java_dev_rb4dx_standalone_BachataAudioSocket_nativeAccept(
        JNIEnv* env, jclass ignored, jint server_fd) {
    (void)env;
    (void)ignored;
    if (server_fd < 0) return -EINVAL;
    struct pollfd p = { .fd = server_fd, .events = POLLIN };
    int r = poll(&p, 1, 250);
    if (r == 0) return -EAGAIN;
    if (r < 0) return -errno;
    if (!(p.revents & POLLIN)) return -EIO;
    int client = accept4(server_fd, NULL, NULL, SOCK_CLOEXEC);
    return client < 0 ? -errno : client;
}

JNIEXPORT jint JNICALL
Java_dev_rb4dx_standalone_BachataAudioSocket_nativeRead(
        JNIEnv* env, jclass ignored, jint client_fd, jbyteArray destination,
        jint offset, jint count) {
    (void)ignored;
    if (client_fd < 0 || destination == NULL || offset < 0 || count <= 0
            || count > 65536 || offset > (*env)->GetArrayLength(env, destination) - count)
        return -EINVAL;
    struct pollfd p = { .fd = client_fd, .events = POLLIN };
    int r = poll(&p, 1, 250);
    if (r == 0) return -EAGAIN;
    if (r < 0) return -errno;
    if (!(p.revents & POLLIN)) return 0;
    uint8_t bytes[65536];
    ssize_t size = recv(client_fd, bytes, (size_t)count, 0);
    if (size < 0) return -errno;
    if (size > 0) (*env)->SetByteArrayRegion(env, destination, offset,
            (jsize)size, (const jbyte*)bytes);
    return (jint)size;
}

JNIEXPORT void JNICALL
Java_dev_rb4dx_standalone_BachataAudioSocket_nativeClose(
        JNIEnv* env, jclass ignored, jint fd) {
    (void)env;
    (void)ignored;
    if (fd >= 0) close(fd);
}
