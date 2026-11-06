// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

/* A single startup probe is active per app process; the listener owns the fd. */
void rb4dx_socket_cancel_reset(int server_fd);
void rb4dx_socket_cancel_request(int server_fd);
int rb4dx_socket_cancelled(int server_fd);
