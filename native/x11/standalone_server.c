// SPDX-License-Identifier: GPL-3.0-or-later
// Standalone app-private X11 service process. It is NOT a game renderer:
// a host renderer/SurfaceView and Vulkan transport must be connected later.
#include <android/looper.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "embedded.h"

static volatile sig_atomic_t stop_requested = 0;

static void request_stop(int signum) {
    (void)signum;
    stop_requested = 1;
}

static void on_ready(void* ignored, const char* display) {
    (void)ignored;
    // The launcher must wait for this line before assigning DISPLAY to SDL.
    // Xorg has already opened its socket by the time this callback executes.
    printf("RB4DX_X11_READY display=:%s\n", display);
    fflush(stdout);
}

static void clamp_size(const LorieWindowConstraints* limits, int* width, int* height) {
    if (*width < limits->minWidth) *width = limits->minWidth;
    if (*height < limits->minHeight) *height = limits->minHeight;
    if (*width > limits->maxWidth) *width = limits->maxWidth;
    if (*height > limits->maxHeight) *height = limits->maxHeight;
}

int main(int argc, char** argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: rb4dx-x11-server :DISPLAY [-auth file] -nolisten tcp -noreset\n");
        return 2;
    }
    if (getenv("TMPDIR") == NULL || getenv("XKB_CONFIG_ROOT") == NULL) {
        fprintf(stderr, "RB4DX_X11_CONFIG_MISSING TMPDIR or XKB_CONFIG_ROOT\n");
        return 3;
    }
    // Never open a public unauthenticated X11 service, even for a test build.
    bool has_auth = false;
    bool tcp_disabled = false;
    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "-ac") == 0) {
            fprintf(stderr, "RB4DX_X11_AUTH_REQUIRED: -ac is forbidden\n");
            return 6;
        }
        if (strcmp(argv[i], "-auth") == 0 && i + 1 < argc) {
            has_auth = (access(argv[i + 1], R_OK) == 0);
        }
        if (strcmp(argv[i], "-nolisten") == 0 && i + 1 < argc &&
                strcmp(argv[i + 1], "tcp") == 0) tcp_disabled = true;
    }
    if (!has_auth || !tcp_disabled) {
        fprintf(stderr, "RB4DX_X11_AUTH_REQUIRED: supply readable -auth file and -nolisten tcp\n");
        return 6;
    }
    signal(SIGTERM, request_stop);
    signal(SIGINT, request_stop);
    if (!ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS)) {
        fprintf(stderr, "RB4DX_X11_LOOPER_FAILED\n");
        return 4;
    }
    const LorieServerCallbacks callbacks = {
        .ready = on_ready,
        .windowSize = clamp_size,
    };
    if (!lorieServerStart(argc - 1, (const char* const*)(argv + 1), &callbacks, NULL)) {
        fprintf(stderr, "RB4DX_X11_START_FAILED\n");
        return 5;
    }
    while (!stop_requested) ALooper_pollOnce(250, NULL, NULL, NULL);
    lorieServerStop();
    return 0;
}
