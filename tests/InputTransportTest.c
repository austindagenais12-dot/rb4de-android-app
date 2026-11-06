#include "rb4dx_input_transport.h"

#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

int main(void) {
    const char* frame = "BACHATA/1 INPUT slot=0 seq=7 buttons=0x4000 lx=128 ly=128 rx=128 ry=128 l2=0 r2=0 touch=0 tx=0 ty=0\n";
    int connected[2] = {-1, -1};
    assert(socketpair(AF_UNIX, SOCK_STREAM, 0, connected) == 0);
    size_t size = strlen(frame);
    assert(rb4dx_input_send(8, frame, size) == 0);
    rb4dx_input_attach(7, connected[0]);
    assert(rb4dx_input_send(8, frame, size) == 0); // no cross-session delivery
    assert(rb4dx_input_send(7, frame, 0) == -EINVAL);
    assert(rb4dx_input_send(7, frame, size) == (int)size);
    char received[512] = {0};
    assert(recv(connected[1], received, sizeof(received), 0) == (ssize_t)size);
    assert(strcmp(received, frame) == 0);
    rb4dx_input_detach(7, connected[0]);
    assert(rb4dx_input_send(7, frame, size) == 0); // release at disconnect
    close(connected[0]);
    close(connected[1]);
    puts("PASS: input writer sends exact frames only to its own active socket");
    return 0;
}
