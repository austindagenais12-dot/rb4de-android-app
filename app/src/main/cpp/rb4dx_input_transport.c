// SPDX-License-Identifier: GPL-2.0-or-later
#include "rb4dx_input_transport.h"

#include <errno.h>
#include <pthread.h>
#include <string.h>
#include <sys/socket.h>

static pthread_mutex_t input_mutex = PTHREAD_MUTEX_INITIALIZER;
static int active_server_fd = -1;
static int active_client_fd = -1;

void rb4dx_input_attach(int server_fd, int client_fd) {
    pthread_mutex_lock(&input_mutex);
    active_server_fd = server_fd;
    active_client_fd = client_fd;
    pthread_mutex_unlock(&input_mutex);
}

void rb4dx_input_detach(int server_fd, int client_fd) {
    pthread_mutex_lock(&input_mutex);
    if (active_server_fd == server_fd && active_client_fd == client_fd) {
        active_server_fd = -1;
        active_client_fd = -1;
    }
    pthread_mutex_unlock(&input_mutex);
}

int rb4dx_input_send(int server_fd, const char* frame, size_t length) {
    static const char prefix[] = "BACHATA/1 INPUT ";
    if (server_fd < 0 || frame == NULL || length < sizeof(prefix) || length > 511 ||
            memcmp(frame, prefix, sizeof(prefix) - 1) != 0 || frame[length - 1] != '\n') {
        return -EINVAL;
    }
    pthread_mutex_lock(&input_mutex);
    if (active_server_fd != server_fd || active_client_fd < 0) {
        pthread_mutex_unlock(&input_mutex);
        return 0;
    }
    /* Protect against detach/close races, and never block the controller producer. */
    ssize_t sent = send(active_client_fd, frame, length, MSG_NOSIGNAL | MSG_DONTWAIT);
    int result = sent < 0 ? -errno : (int)sent;
    pthread_mutex_unlock(&input_mutex);
    return result == (int)length ? result : (result >= 0 ? -EAGAIN : result);
}
