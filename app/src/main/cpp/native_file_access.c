// SPDX-License-Identifier: GPL-2.0-or-later
#include <jni.h>

#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

/* Android-native POSIX access check. Opens only the user-selected eboot.bin. */
JNIEXPORT jstring JNICALL
Java_dev_rb4dx_standalone_NativeFileAccessProbe_inspect(
        JNIEnv* env, jclass ignored, jstring absolute_path) {
    (void)ignored;
    char report[1024];
    if (absolute_path == NULL) {
        return (*env)->NewStringUTF(env,
                "RB4DX_DIRECT_NATIVE_ACCESS_FAILED\nerror=No eboot path\n");
    }
    const char* path = (*env)->GetStringUTFChars(env, absolute_path, NULL);
    if (path == NULL) return NULL;
    size_t len = strlen(path);
    if (len > 2048 || strncmp(path, "/storage/", strlen("/storage/")) != 0
            || len < strlen("/eboot.bin")
            || strcmp(path + len - strlen("/eboot.bin"), "/eboot.bin") != 0) {
        (*env)->ReleaseStringUTFChars(env, absolute_path, path);
        return (*env)->NewStringUTF(env,
                "RB4DX_DIRECT_NATIVE_ACCESS_FAILED\nerror=Unexpected game path\n");
    }
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    int open_error = errno;
    (*env)->ReleaseStringUTFChars(env, absolute_path, path);
    if (fd < 0) {
        snprintf(report, sizeof(report),
                "RB4DX_DIRECT_NATIVE_ACCESS_FAILED\nopen_errno=%d\nopen_error=%s\n",
                open_error, strerror(open_error));
        return (*env)->NewStringUTF(env, report);
    }

    struct stat info;
    int stat_ok = fstat(fd, &info);
    uint8_t bytes[16];
    ssize_t count = pread(fd, bytes, sizeof(bytes), 0);
    int read_error = errno;
    close(fd);
    if (stat_ok != 0 || !S_ISREG(info.st_mode) || count != (ssize_t)sizeof(bytes)) {
        snprintf(report, sizeof(report),
                "RB4DX_DIRECT_NATIVE_ACCESS_FAILED\nfstat_ok=%s\nread_bytes=%zd\nread_errno=%d\n",
                stat_ok == 0 ? "true" : "false", count, read_error);
        return (*env)->NewStringUTF(env, report);
    }
    char header[sizeof(bytes) * 2 + 1];
    for (size_t i = 0; i < sizeof(bytes); i++) {
        snprintf(header + i * 2, sizeof(header) - i * 2, "%02x", bytes[i]);
    }
    snprintf(report, sizeof(report),
            "RB4DX_DIRECT_NATIVE_ACCESS_PASS\n"
            "android_posix_open=success\n"
            "eboot_size=%lld\n"
            "eboot_header=%s\n"
            "note=Native Android POSIX access works; Linux glibc emulator launch not tested\n",
            (long long)info.st_size, header);
    return (*env)->NewStringUTF(env, report);
}
