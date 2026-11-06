#include "rb4dx_socket_cancel.h"

#include <assert.h>
#include <stdio.h>

int main(void) {
    rb4dx_socket_cancel_reset(13);
    assert(!rb4dx_socket_cancelled(13));
    rb4dx_socket_cancel_request(999);
    assert(!rb4dx_socket_cancelled(13));
    rb4dx_socket_cancel_request(13);
    rb4dx_socket_cancel_request(13);
    assert(rb4dx_socket_cancelled(13));
    rb4dx_socket_cancel_reset(14);
    assert(!rb4dx_socket_cancelled(14));
    assert(!rb4dx_socket_cancelled(13));
    puts("PASS: per-session native listener cancellation and reset");
    return 0;
}
