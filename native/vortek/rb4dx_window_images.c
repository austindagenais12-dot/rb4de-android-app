#include "rb4dx_window_images.h"

#include <string.h>

static bool valid_key(uint8_t image_index, int width, int height) {
    return image_index < 3 && width > 0 && height > 0;
}

uintptr_t rb4dx_window_images_lookup(const Rb4dxWindowImages *table, int window_id,
                                     uint8_t image_index, int width, int height) {
    if (table == NULL || !valid_key(image_index, width, height)) return 0;
    for (size_t i = table->count; i > 0; i--) {
        const Rb4dxWindowImage *entry = &table->images[i - 1];
        if (entry->window_id == window_id && entry->image_index == image_index &&
            entry->width == width && entry->height == height) return entry->handle;
    }
    return 0;
}

bool rb4dx_window_images_record(Rb4dxWindowImages *table, int window_id,
                                uint8_t image_index, int width, int height, uintptr_t handle) {
    if (table == NULL || handle == 0 || !valid_key(image_index, width, height) ||
        table->count >= RB4DX_MAX_WINDOW_IMAGES ||
        rb4dx_window_images_lookup(table, window_id, image_index, width, height)) return false;

    /* Never let two swapchain images alias the same AHardwareBuffer. */
    for (size_t i = 0; i < table->count; i++) {
        if (table->images[i].handle == handle) return false;
    }
    table->images[table->count++] = (Rb4dxWindowImage){
        .window_id = window_id, .image_index = image_index,
        .width = width, .height = height, .handle = handle
    };
    return true;
}

bool rb4dx_window_images_presented(Rb4dxWindowImages *table, int window_id, uint8_t image_index) {
    if (table == NULL || image_index >= 3) return false;
    for (size_t i = table->count; i > 0; i--) {
        const Rb4dxWindowImage *entry = &table->images[i - 1];
        if (entry->window_id == window_id && entry->image_index == image_index) {
            table->last_presented = i - 1;
            table->has_presented = true;
            return true;
        }
    }
    return false;
}

bool rb4dx_window_images_preview(const Rb4dxWindowImages *table, uintptr_t *out) {
    if (table == NULL || out == NULL || !table->has_presented ||
        table->last_presented >= table->count) return false;
    *out = table->images[table->last_presented].handle;
    return true;
}

void rb4dx_window_images_clear(Rb4dxWindowImages *table) {
    if (table != NULL) memset(table, 0, sizeof(*table));
}
