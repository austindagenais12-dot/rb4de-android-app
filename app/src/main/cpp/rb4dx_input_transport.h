// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <stddef.h>

/* The accepted fd is owned and closed by the readSession JNI thread. */
void rb4dx_input_attach(int server_fd, int client_fd);
void rb4dx_input_detach(int server_fd, int client_fd);
/* 0 = no connected matching session; positive = bytes sent; negative = errno. */
int rb4dx_input_send(int server_fd, const char* frame, size_t length);
