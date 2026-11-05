// SPDX-License-Identifier: GPL-3.0-or-later
// Connect the ARM64 glibc X11 client to the Android X server's private
// filesystem socket. The Debian Xtrans client uses a compiled-in /tmp path,
// while our Android X server creates sockets beneath its private TMPDIR.
// Intercept only the selected X11 display; other sockets are untouched.
#define _GNU_SOURCE
#include <errno.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <unistd.h>

static int real_connect(int fd, const struct sockaddr *address, socklen_t length) {
    return (int)syscall(SYS_connect, fd, address, length);
}

int connect(int fd, const struct sockaddr *address, socklen_t length) {
    static const char prefix[] = "/tmp/.X11-unix/X";
    const size_t prefix_length = sizeof(prefix) - 1;
    const size_t offset = offsetof(struct sockaddr_un, sun_path);

    if (address == NULL || address->sa_family != AF_UNIX || length <= offset) {
        return real_connect(fd, address, length);
    }

    const struct sockaddr_un *original = (const struct sockaddr_un *)address;
    size_t bytes = (size_t)length - offset;
    if (bytes > sizeof(original->sun_path)) {
        return real_connect(fd, address, length);
    }

    // Linux Xtrans tries abstract UNIX sockets and pathname sockets.
    const char *name = original->sun_path;
    if (name[0] == '\0') {
        ++name;
        --bytes;
    }
    if (bytes < prefix_length + 1 || memcmp(name, prefix, prefix_length) != 0) {
        return real_connect(fd, address, length);
    }

    const char *digits = name + prefix_length;
    const size_t remaining = bytes - prefix_length;
    size_t count = 0;
    while (count < remaining && digits[count] >= '0' && digits[count] <= '9') {
        ++count;
    }
    if (count == 0 || count > 4 ||
            (count < remaining && digits[count] != '\0')) {
        return real_connect(fd, address, length);
    }

    const char *directory = getenv("RB4DX_X11_SOCKET_DIR");
    const char *selected = getenv("RB4DX_X11_DISPLAY_NUM");
    if (directory == NULL || directory[0] != '/' ||
            selected == NULL || strlen(selected) != count ||
            memcmp(selected, digits, count) != 0) {
        return real_connect(fd, address, length);
    }

    struct sockaddr_un target = {0};
    target.sun_family = AF_UNIX;
    int n = snprintf(target.sun_path, sizeof(target.sun_path),
            "%s/X%.*s", directory, (int)count, digits);
    if (n <= 0 || (size_t)n >= sizeof(target.sun_path)) {
        errno = ENAMETOOLONG;
        return -1;
    }

    int result = real_connect(fd, (const struct sockaddr *)&target,
            (socklen_t)(offset + (size_t)n + 1));
    static const char success[] = "RB4DX_X11_PRIVATE_SOCKET_CONNECTED\n";
    static const char failure[] = "RB4DX_X11_PRIVATE_SOCKET_CONNECT_FAILED\n";
    if (result == 0) {
        (void)write(STDERR_FILENO, success, sizeof(success) - 1);
    } else {
        (void)write(STDERR_FILENO, failure, sizeof(failure) - 1);
    }
    return result;
}
