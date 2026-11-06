#include "rb4dx_frame_ring.h"

#include <stddef.h>
#include <stdint.h>

enum {
    RB4DX_SLOT_FREE = 0,
    RB4DX_SLOT_GPU_WRITING = 1,
    RB4DX_SLOT_GPU_COMPLETE = 2,
    RB4DX_SLOT_CONSUMER_OWNED = 3,
};

static bool is_open(const Rb4dxFrameRing *ring) {
    return ring != NULL && ring->initialized && !ring->closed;
}

static Rb4dxFrameSlot *matching_slot(Rb4dxFrameRing *ring, Rb4dxFrameLease lease) {
    if (!is_open(ring) || lease.token == 0 || lease.slot >= ring->capacity) {
        return NULL;
    }
    Rb4dxFrameSlot *slot = &ring->slots[lease.slot];
    return slot->token == lease.token ? slot : NULL;
}

bool rb4dx_frame_ring_init(Rb4dxFrameRing *ring, uint8_t capacity) {
    return rb4dx_frame_ring_init_seeded(ring, capacity, 0);
}

bool rb4dx_frame_ring_init_seeded(Rb4dxFrameRing *ring, uint8_t capacity, uint64_t token_seed) {
    if (ring == NULL || (capacity != 2 && capacity != 3) || ring->initialized || ring->closed ||
        token_seed == UINT64_MAX) {
        return false;
    }
    for (uint8_t i = 0; i < 3; i++) {
        ring->slots[i].state = RB4DX_SLOT_FREE;
        ring->slots[i].token = 0;
    }
    ring->next_token = token_seed;
    ring->capacity = capacity;
    ring->initialized = true;
    return true;
}

bool rb4dx_frame_ring_cancel(Rb4dxFrameRing *ring, Rb4dxFrameLease lease) {
    Rb4dxFrameSlot *slot = matching_slot(ring, lease);
    if (slot == NULL || (slot->state != RB4DX_SLOT_GPU_WRITING &&
                         slot->state != RB4DX_SLOT_GPU_COMPLETE)) {
        return false;
    }
    slot->state = RB4DX_SLOT_FREE;
    return true;
}

bool rb4dx_frame_ring_acquire(Rb4dxFrameRing *ring, Rb4dxFrameLease *out) {
    if (!is_open(ring) || out == NULL || ring->next_token == UINT64_MAX) {
        return false;
    }
    for (uint8_t i = 0; i < ring->capacity; i++) {
        Rb4dxFrameSlot *slot = &ring->slots[i];
        if (slot->state == RB4DX_SLOT_FREE) {
            const uint64_t token = ++ring->next_token;
            slot->state = RB4DX_SLOT_GPU_WRITING;
            slot->token = token;
            *out = (Rb4dxFrameLease) {.slot = i, .token = token};
            return true;
        }
    }
    return false;
}

bool rb4dx_frame_ring_gpu_complete(Rb4dxFrameRing *ring, Rb4dxFrameLease lease) {
    Rb4dxFrameSlot *slot = matching_slot(ring, lease);
    if (slot == NULL || slot->state != RB4DX_SLOT_GPU_WRITING) {
        return false;
    }
    slot->state = RB4DX_SLOT_GPU_COMPLETE;
    return true;
}

bool rb4dx_frame_ring_present(Rb4dxFrameRing *ring, Rb4dxFrameLease lease) {
    Rb4dxFrameSlot *slot = matching_slot(ring, lease);
    if (slot == NULL || slot->state != RB4DX_SLOT_GPU_COMPLETE) {
        return false;
    }
    slot->state = RB4DX_SLOT_CONSUMER_OWNED;
    return true;
}

bool rb4dx_frame_ring_release(Rb4dxFrameRing *ring, Rb4dxFrameLease lease) {
    Rb4dxFrameSlot *slot = matching_slot(ring, lease);
    if (slot == NULL || slot->state != RB4DX_SLOT_CONSUMER_OWNED) {
        return false;
    }
    slot->state = RB4DX_SLOT_FREE;
    /* Keep the old token until the next lease to reject stale callbacks. */
    return true;
}

void rb4dx_frame_ring_close(Rb4dxFrameRing *ring) {
    if (ring != NULL) {
        ring->closed = true;
    }
}
