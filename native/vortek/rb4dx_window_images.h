#ifndef RB4DX_WINDOW_IMAGES_H
#define RB4DX_WINDOW_IMAGES_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Retain at most 96 window-image allocations, each using image index 0-2. */
#define RB4DX_MAX_WINDOW_IMAGES 96

typedef struct {
    int window_id;
    uint8_t image_index;
    int width;
    int height;
    uintptr_t handle;
} Rb4dxWindowImage;

/* Caller must externally serialize access to this table. The handles are
 * borrowed: the table does not allocate, release or wait for any GPU buffer. */
typedef struct {
    Rb4dxWindowImage images[RB4DX_MAX_WINDOW_IMAGES];
    size_t count;
    size_t last_presented;
    bool has_presented;
} Rb4dxWindowImages;

uintptr_t rb4dx_window_images_lookup(const Rb4dxWindowImages *table, int window_id,
                                     uint8_t image_index, int width, int height);
bool rb4dx_window_images_record(Rb4dxWindowImages *table, int window_id,
                                uint8_t image_index, int width, int height, uintptr_t handle);
bool rb4dx_window_images_presented(Rb4dxWindowImages *table, int window_id, uint8_t image_index);
bool rb4dx_window_images_preview(const Rb4dxWindowImages *table, uintptr_t *out);
void rb4dx_window_images_clear(Rb4dxWindowImages *table);

#endif
