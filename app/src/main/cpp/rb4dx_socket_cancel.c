// SPDX-License-Identifier: GPL-2.0-or-later
#include "rb4dx_socket_cancel.h"

#include <stdatomic.h>

static atomic_int active_server_fd = ATOMIC_VAR_INIT(-1);
static atomic_int stopped_server_fd = ATOMIC_VAR_INIT(-1);

void rb4dx_socket_cancel_reset(int server_fd) {
    atomic_store(&stopped_server_fd, -1);
    atomic_store(&active_server_fd, server_fd);
}

void rb4dx_socket_cancel_request(int server_fd) {
    if (server_fd >= 0 && atomic_load(&active_server_fd) == server_fd) {
        atomic_store(&stopped_server_fd, server_fd);
    }
}

int rb4dx_socket_cancelled(int server_fd) {
    return server_fd >= 0 && atomic_load(&active_server_fd) == server_fd
            && atomic_load(&stopped_server_fd) == server_fd;
}
