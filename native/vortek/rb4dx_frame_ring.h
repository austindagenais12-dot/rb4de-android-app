#ifndef RB4DX_FRAME_RING_H
#define RB4DX_FRAME_RING_H

#include <stdbool.h>
#include <stdint.h>

/*
 * Allocation-free ownership protocol for a 2-3 image Vulkan-to-Android ring.
 *
 * IMPORTANT: This is a serial state machine, NOT a synchronization primitive.
 * Its owner MUST serialize calls from GPU and Android/compositor threads using
 * the same mutex. Never call gpu_complete before the producer's GPU completion
 * fence actually signals; never call release before compositor release.
 *
 * Initialize exactly once on a zero-initialized struct. Closing is terminal:
 * do not reinitialize the same object while callbacks might still arrive.
 */
typedef struct {
    uint8_t slot;
    uint64_t token;
} Rb4dxFrameLease;

typedef struct {
    uint64_t token;
    uint8_t state;
} Rb4dxFrameSlot;

typedef struct {
    Rb4dxFrameSlot slots[3];
    uint64_t next_token;
    uint8_t capacity;
    bool initialized;
    bool closed;
} Rb4dxFrameRing;

bool rb4dx_frame_ring_init(Rb4dxFrameRing *ring, uint8_t capacity);
/* For new sessions, seed tokens from a monotonically increasing session epoch. */
bool rb4dx_frame_ring_init_seeded(Rb4dxFrameRing *ring, uint8_t capacity, uint64_t token_seed);
bool rb4dx_frame_ring_acquire(Rb4dxFrameRing *ring, Rb4dxFrameLease *out);
/* Only unpublished producer frames may be cancelled. */
bool rb4dx_frame_ring_cancel(Rb4dxFrameRing *ring, Rb4dxFrameLease lease);
bool rb4dx_frame_ring_gpu_complete(Rb4dxFrameRing *ring, Rb4dxFrameLease lease);
bool rb4dx_frame_ring_present(Rb4dxFrameRing *ring, Rb4dxFrameLease lease);
bool rb4dx_frame_ring_release(Rb4dxFrameRing *ring, Rb4dxFrameLease lease);
void rb4dx_frame_ring_close(Rb4dxFrameRing *ring);

#endif
