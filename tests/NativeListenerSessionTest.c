#include <jni.h>
#include "rb4dx_input_transport.h"
#include "rb4dx_socket_cancel.h"

#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

extern jstring Java_dev_rb4dx_standalone_NativeRuntimeSocket_readSession(
        JNIEnv*, jclass, jint, jint);

static jstring host_new_string(JNIEnv* env, const char* text) {
    (void)env;
    return strdup(text);
}
static const struct JNINativeInterface_ host_jni = { .NewStringUTF = host_new_string };

struct listener {
    int fd;
    int timeout_ms;
    char* result;
    pthread_t thread;
    struct sockaddr_un addr;
    socklen_t addr_len;
};

static void* read_thread(void* context) {
    struct listener* l = context;
    JNIEnv env = &host_jni;
    l->result = Java_dev_rb4dx_standalone_NativeRuntimeSocket_readSession(
            &env, NULL, l->fd, l->timeout_ms);
    return NULL;
}

static struct listener start_listener(int timeout_ms) {
    static int sequence;
    struct listener l = { .timeout_ms = timeout_ms };
    l.fd = socket(AF_UNIX, SOCK_STREAM, 0);
    assert(l.fd >= 0);
    l.addr.sun_family = AF_UNIX;
    char name[75];
    snprintf(name, sizeof(name), "rb4dx-socket-test-%ld-%d", (long)getpid(), ++sequence);
    memcpy(l.addr.sun_path + 1, name, strlen(name));
    l.addr_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name));
    assert(bind(l.fd, (struct sockaddr*)&l.addr, l.addr_len) == 0);
    assert(listen(l.fd, 1) == 0);
    rb4dx_socket_cancel_reset(l.fd);
    return l;
}

static void launch(struct listener* l) {
    assert(pthread_create(&l->thread, NULL, read_thread, l) == 0);
}

static void join_quickly(struct listener* l) {
    struct timespec deadline;
    assert(clock_gettime(CLOCK_REALTIME, &deadline) == 0);
    deadline.tv_sec += 2;
    assert(pthread_timedjoin_np(l->thread, NULL, &deadline) == 0);
    assert(l->result != NULL);
}

static void finish(struct listener* l) {
    free(l->result);
    close(l->fd);
}

static int guest_connect(struct listener* l) {
    int client = socket(AF_UNIX, SOCK_STREAM, 0);
    assert(client >= 0);
    assert(connect(client, (struct sockaddr*)&l->addr, l->addr_len) == 0);
    return client;
}

static void pause_ms(long millis) {
    struct timespec interval = {.tv_sec = millis / 1000,
            .tv_nsec = (millis % 1000) * 1000000};
    nanosleep(&interval, NULL);
}

static void test_preconnect_stop(void) {
    struct listener l = start_listener(120000);
    launch(&l);
    pause_ms(50);
    rb4dx_socket_cancel_request(l.fd);
    join_quickly(&l);
    assert(strcmp(l.result, "LISTENER_CANCELLED") == 0);
    finish(&l);
}

static void test_connected_stop_and_detach(void) {
    struct listener l = start_listener(120000);
    launch(&l);
    int guest = guest_connect(&l);
    const char hello[] = "BACHATA/1 EVENT Running\n";
    assert(write(guest, hello, sizeof(hello) - 1) == sizeof(hello) - 1);
    const char input[] = "BACHATA/1 INPUT slot=0 buttons=1\n";
    int sent = 0;
    for (int attempt = 0; attempt < 60 && sent == 0; attempt++) {
        sent = rb4dx_input_send(l.fd, input, sizeof(input) - 1);
        if (sent == 0) pause_ms(10);
    }
    assert(sent == (int)sizeof(input) - 1);
    char buffer[128];
    assert(read(guest, buffer, sizeof(buffer)) == sizeof(input) - 1);
    rb4dx_socket_cancel_request(l.fd);
    join_quickly(&l);
    assert(strstr(l.result, "Running") != NULL);
    assert(rb4dx_input_send(l.fd, input, sizeof(input) - 1) == 0);
    close(guest);
    finish(&l);
}

static void test_capture_saturation_does_not_disconnect_input(void) {
    struct listener l = start_listener(120000);
    launch(&l);
    int guest = guest_connect(&l);
    char block[1024];
    memset(block, 'F', sizeof(block));
    memcpy(block, "BACHATA/1 EVENT Running\n", 24);
    for (int i = 0; i < 32; i++) {
        assert(send(guest, block, sizeof(block), 0) == sizeof(block));
    }
    pause_ms(500);
    const char input[] = "BACHATA/1 INPUT slot=0 buttons=1\n";
    int sent = rb4dx_input_send(l.fd, input, sizeof(input) - 1);
    assert(sent == (int)sizeof(input) - 1);
    rb4dx_socket_cancel_request(l.fd);
    join_quickly(&l);
    assert(strstr(l.result, "BACHATA/1 EVENT Running") != NULL);
    assert(strlen(l.result) == 16384);
    close(guest);
    finish(&l);
}

static void test_monotonic_timeout_without_client(void) {
    struct listener l = start_listener(300);
    launch(&l);
    join_quickly(&l);
    assert(strcmp(l.result, "NO_CLIENT_CONNECTED") == 0);
    finish(&l);
}

int main(void) {
    int probe_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (probe_fd < 0 && errno == EPERM) {
        puts("SKIP: local sandbox forbids AF_UNIX sockets; execute in CI");
        return 77;
    }
    assert(probe_fd >= 0);
    close(probe_fd);
    test_preconnect_stop();
    test_connected_stop_and_detach();
    test_capture_saturation_does_not_disconnect_input();
    test_monotonic_timeout_without_client();
    puts("PASS: real native socket stops pre/after connect, drains capture, and times out");
    return 0;
}
