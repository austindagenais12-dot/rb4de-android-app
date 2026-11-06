#include "rb4dx_frame_ring.h"

#include <assert.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>

static Rb4dxFrameRing new_ring(uint8_t capacity) {
    Rb4dxFrameRing ring;
    memset(&ring, 0, sizeof(ring));
    assert(rb4dx_frame_ring_init(&ring, capacity));
    return ring;
}

static void test_rejects_invalid_inputs_and_capacities(void) {
    Rb4dxFrameRing ring = {0};
    Rb4dxFrameLease lease = {0};
    assert(!rb4dx_frame_ring_init(NULL, 3));
    assert(!rb4dx_frame_ring_init(&ring, 0));
    assert(!rb4dx_frame_ring_init(&ring, 1));
    assert(!rb4dx_frame_ring_init(&ring, 4));
    assert(!rb4dx_frame_ring_acquire(&ring, &lease));
    assert(rb4dx_frame_ring_init(&ring, 2));
    assert(!rb4dx_frame_ring_init(&ring, 3));
    assert(!rb4dx_frame_ring_acquire(NULL, &lease));
    assert(!rb4dx_frame_ring_acquire(&ring, NULL));
    assert(!rb4dx_frame_ring_gpu_complete(NULL, lease));
    assert(!rb4dx_frame_ring_present(NULL, lease));
    assert(!rb4dx_frame_ring_release(NULL, lease));
    rb4dx_frame_ring_close(NULL);
}

static void test_full_ring_never_reuses_a_busy_slot(void) {
    Rb4dxFrameRing ring = new_ring(3);
    Rb4dxFrameLease slots[3];
    for (int i = 0; i < 3; i++) {
        assert(rb4dx_frame_ring_acquire(&ring, &slots[i]));
        assert(slots[i].token != 0);
        assert(slots[i].slot < 3);
        for (int j = 0; j < i; j++) {
            assert(slots[i].slot != slots[j].slot);
            assert(slots[i].token != slots[j].token);
        }
    }
    Rb4dxFrameLease unchanged = {.slot = 77, .token = 12345};
    assert(!rb4dx_frame_ring_acquire(&ring, &unchanged));
    assert(unchanged.slot == 77 && unchanged.token == 12345);

    assert(rb4dx_frame_ring_gpu_complete(&ring, slots[1]));
    assert(rb4dx_frame_ring_present(&ring, slots[1]));
    assert(!rb4dx_frame_ring_acquire(&ring, &unchanged));
    assert(rb4dx_frame_ring_release(&ring, slots[1]));
    assert(rb4dx_frame_ring_acquire(&ring, &unchanged));
    assert(unchanged.slot == slots[1].slot);
    assert(unchanged.token != slots[1].token);
}

static void test_two_slot_ring_and_complete_lifecycle(void) {
    Rb4dxFrameRing ring = new_ring(2);
    Rb4dxFrameLease a, b, extra;
    assert(rb4dx_frame_ring_acquire(&ring, &a));
    assert(rb4dx_frame_ring_acquire(&ring, &b));
    assert(!rb4dx_frame_ring_acquire(&ring, &extra));
    assert(a.slot != b.slot);
    assert(rb4dx_frame_ring_gpu_complete(&ring, a));
    assert(rb4dx_frame_ring_present(&ring, a));
    assert(rb4dx_frame_ring_release(&ring, a));
    assert(rb4dx_frame_ring_acquire(&ring, &extra));
    assert(extra.slot == a.slot);
}

static void test_out_of_order_and_duplicate_callbacks_are_rejected(void) {
    Rb4dxFrameRing ring = new_ring(2);
    Rb4dxFrameLease lease;
    assert(rb4dx_frame_ring_acquire(&ring, &lease));
    assert(!rb4dx_frame_ring_present(&ring, lease));
    assert(!rb4dx_frame_ring_release(&ring, lease));
    assert(rb4dx_frame_ring_gpu_complete(&ring, lease));
    assert(!rb4dx_frame_ring_gpu_complete(&ring, lease));
    assert(!rb4dx_frame_ring_release(&ring, lease));
    assert(rb4dx_frame_ring_present(&ring, lease));
    assert(!rb4dx_frame_ring_present(&ring, lease));
    assert(!rb4dx_frame_ring_gpu_complete(&ring, lease));
    assert(rb4dx_frame_ring_release(&ring, lease));
    assert(!rb4dx_frame_ring_release(&ring, lease));
    assert(!rb4dx_frame_ring_present(&ring, lease));
}

static void test_old_generation_release_cannot_reclaim_new_frame(void) {
    Rb4dxFrameRing ring = new_ring(2);
    Rb4dxFrameLease old, newer, wrong;
    assert(rb4dx_frame_ring_acquire(&ring, &old));
    assert(rb4dx_frame_ring_gpu_complete(&ring, old));
    assert(rb4dx_frame_ring_present(&ring, old));
    assert(rb4dx_frame_ring_release(&ring, old));
    assert(rb4dx_frame_ring_acquire(&ring, &newer));
    assert(newer.slot == old.slot);
    assert(newer.token > old.token);
    assert(!rb4dx_frame_ring_release(&ring, old));
    wrong = newer;
    wrong.token = old.token;
    assert(!rb4dx_frame_ring_gpu_complete(&ring, wrong));
    wrong = newer;
    wrong.slot = 3;
    assert(!rb4dx_frame_ring_gpu_complete(&ring, wrong));
    assert(rb4dx_frame_ring_gpu_complete(&ring, newer));
    assert(rb4dx_frame_ring_present(&ring, newer));
    assert(rb4dx_frame_ring_release(&ring, newer));
}

static void test_close_invalidates_all_outstanding_leases(void) {
    Rb4dxFrameRing ring = new_ring(3);
    Rb4dxFrameLease producing, ready, presented, unchanged = {.slot = 8, .token = 9};
    assert(rb4dx_frame_ring_acquire(&ring, &producing));
    assert(rb4dx_frame_ring_acquire(&ring, &ready));
    assert(rb4dx_frame_ring_gpu_complete(&ring, ready));
    assert(rb4dx_frame_ring_acquire(&ring, &presented));
    assert(rb4dx_frame_ring_gpu_complete(&ring, presented));
    assert(rb4dx_frame_ring_present(&ring, presented));
    rb4dx_frame_ring_close(&ring);
    rb4dx_frame_ring_close(&ring);
    assert(!rb4dx_frame_ring_init(&ring, 3));
    assert(!rb4dx_frame_ring_acquire(&ring, &unchanged));
    assert(unchanged.slot == 8 && unchanged.token == 9);
    assert(!rb4dx_frame_ring_gpu_complete(&ring, producing));
    assert(!rb4dx_frame_ring_present(&ring, ready));
    assert(!rb4dx_frame_ring_release(&ring, presented));
}

static void test_cancel_unpublished_frame_without_reclaiming_consumer(void) {
    Rb4dxFrameRing ring = new_ring(2);
    Rb4dxFrameLease unfinished, published, reused;
    assert(rb4dx_frame_ring_acquire(&ring, &unfinished));
    assert(rb4dx_frame_ring_cancel(&ring, unfinished));
    assert(!rb4dx_frame_ring_cancel(&ring, unfinished));
    assert(rb4dx_frame_ring_acquire(&ring, &reused));
    assert(reused.slot == unfinished.slot && reused.token != unfinished.token);
    assert(rb4dx_frame_ring_gpu_complete(&ring, reused));
    assert(rb4dx_frame_ring_cancel(&ring, reused));
    assert(rb4dx_frame_ring_acquire(&ring, &published));
    assert(rb4dx_frame_ring_gpu_complete(&ring, published));
    assert(rb4dx_frame_ring_present(&ring, published));
    assert(!rb4dx_frame_ring_cancel(&ring, published));
    assert(rb4dx_frame_ring_release(&ring, published));
}

static void test_new_session_rejects_previous_session_release(void) {
    Rb4dxFrameRing ring = {0};
    assert(rb4dx_frame_ring_init_seeded(&ring, 2, 0x100000000ULL));
    Rb4dxFrameLease previous, current;
    assert(rb4dx_frame_ring_acquire(&ring, &previous));
    assert(rb4dx_frame_ring_gpu_complete(&ring, previous));
    assert(rb4dx_frame_ring_present(&ring, previous));
    rb4dx_frame_ring_close(&ring);
    memset(&ring, 0, sizeof(ring));
    assert(rb4dx_frame_ring_init_seeded(&ring, 2, 0x200000000ULL));
    assert(rb4dx_frame_ring_acquire(&ring, &current));
    assert(!rb4dx_frame_ring_release(&ring, previous));
    assert(current.token != previous.token);
    assert(rb4dx_frame_ring_gpu_complete(&ring, current));
    assert(rb4dx_frame_ring_present(&ring, current));
    assert(rb4dx_frame_ring_release(&ring, current));
}

int main(void) {
    test_rejects_invalid_inputs_and_capacities();
    test_full_ring_never_reuses_a_busy_slot();
    test_two_slot_ring_and_complete_lifecycle();
    test_out_of_order_and_duplicate_callbacks_are_rejected();
    test_old_generation_release_cannot_reclaim_new_frame();
    test_close_invalidates_all_outstanding_leases();
    test_cancel_unpublished_frame_without_reclaiming_consumer();
    test_new_session_rejects_previous_session_release();
    puts("PASS: bounded Vortek frame slots enforce exclusive producer/consumer ownership");
    return 0;
}
