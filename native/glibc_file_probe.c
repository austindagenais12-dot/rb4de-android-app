// SPDX-License-Identifier: GPL-2.0-or-later
/* A tiny AArch64 *glibc* program, invoked by the same loader as shadPS4.
 * Its sole purpose is to verify read-only Linux pathname access to eboot.bin.
 * No game initialization, rendering, or writes to the game directory occur.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

int main(int argc, char** argv) {
    if (argc != 2 || argv[1][0] != '/') {
        fprintf(stderr, "RB4DX_GLIBC_GAME_ACCESS_FAILED\nreason=Expected absolute eboot path\n");
        return 2;
    }
    size_t length = strlen(argv[1]);
    if (length < strlen("/eboot.bin") ||
            strcmp(argv[1] + length - strlen("/eboot.bin"), "/eboot.bin") != 0) {
        fprintf(stderr, "RB4DX_GLIBC_GAME_ACCESS_FAILED\nreason=Not an eboot path\n");
        return 2;
    }
    int fd = open(argv[1], O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) {
        fprintf(stderr, "RB4DX_GLIBC_GAME_ACCESS_FAILED\nopen_errno=%d\nerror=%s\n",
                errno, strerror(errno));
        return 3;
    }
    struct stat info;
    if (fstat(fd, &info) != 0 || !S_ISREG(info.st_mode)) {
        fprintf(stderr, "RB4DX_GLIBC_GAME_ACCESS_FAILED\nreason=Not a regular file\n");
        close(fd);
        return 4;
    }
    uint8_t header[16];
    ssize_t received = pread(fd, header, sizeof(header), 0);
    close(fd);
    if (received != (ssize_t)sizeof(header)) {
        fprintf(stderr, "RB4DX_GLIBC_GAME_ACCESS_FAILED\nread_bytes=%zd\n", received);
        return 5;
    }
    char header_hex[sizeof(header) * 2 + 1];
    for (size_t i = 0; i < sizeof(header); i++) {
        snprintf(header_hex + i * 2, sizeof(header_hex) - i * 2, "%02x", header[i]);
    }
    printf("RB4DX_GLIBC_GAME_ACCESS_PASS\n"
           "linux_aarch64_glibc_open=success\n"
           "eboot_size=%lld\n"
           "eboot_header=%s\n"
           "note=Read-only Linux-side probe; shadPS4 game initialization not tested\n",
           (long long)info.st_size, header_hex);
    fflush(stdout);
    return 0;
}
