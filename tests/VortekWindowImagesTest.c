#include "rb4dx_window_images.h"

#include <assert.h>
#include <stdint.h>
#include <stdio.h>

static void test_each_swapchain_image_has_separate_identity(void) {
    Rb4dxWindowImages table = {0};
    assert(rb4dx_window_images_record(&table, 14, 0, 1280, 720, (uintptr_t)0x1000));
    assert(rb4dx_window_images_record(&table, 14, 1, 1280, 720, (uintptr_t)0x2000));
    assert(rb4dx_window_images_record(&table, 15, 0, 1280, 720, (uintptr_t)0x3000));
    assert(rb4dx_window_images_lookup(&table, 14, 0, 1280, 720) == (uintptr_t)0x1000);
    assert(rb4dx_window_images_lookup(&table, 14, 1, 1280, 720) == (uintptr_t)0x2000);
    assert(rb4dx_window_images_lookup(&table, 15, 0, 1280, 720) == (uintptr_t)0x3000);
    assert(rb4dx_window_images_lookup(&table, 15, 1, 1280, 720) == 0);
    assert(!rb4dx_window_images_record(&table, 14, 2, 1280, 720, (uintptr_t)0x1000));
    assert(!rb4dx_window_images_record(&table, 14, 0, 1280, 720, (uintptr_t)0x4000));
}

static void test_last_presented_image_wins_preview_selection(void) {
    Rb4dxWindowImages table = {0};
    uintptr_t preview = 77;
    assert(!rb4dx_window_images_preview(&table, &preview));
    assert(preview == 77);
    assert(rb4dx_window_images_record(&table, 5, 0, 100, 100, (uintptr_t)0x5000));
    assert(rb4dx_window_images_record(&table, 5, 1, 100, 100, (uintptr_t)0x6000));
    assert(!rb4dx_window_images_presented(&table, 5, 2));
    assert(rb4dx_window_images_presented(&table, 5, 1));
    assert(rb4dx_window_images_preview(&table, &preview) && preview == (uintptr_t)0x6000);
    assert(rb4dx_window_images_presented(&table, 5, 0));
    assert(rb4dx_window_images_preview(&table, &preview) && preview == (uintptr_t)0x5000);
    rb4dx_window_images_clear(&table);
    assert(!rb4dx_window_images_preview(&table, &preview));
}

static void test_rejects_invalid_key_and_window_resize_keeps_latest(void) {
    Rb4dxWindowImages table = {0};
    assert(!rb4dx_window_images_record(NULL, 7, 0, 32, 32, (uintptr_t)3));
    assert(!rb4dx_window_images_record(&table, 7, 3, 32, 32, (uintptr_t)3));
    assert(!rb4dx_window_images_record(&table, 7, 0, 0, 32, (uintptr_t)3));
    assert(!rb4dx_window_images_record(&table, 7, 0, 32, 32, (uintptr_t)0));
    assert(rb4dx_window_images_record(&table, 7, 0, 32, 32, (uintptr_t)0x1000));
    assert(rb4dx_window_images_record(&table, 7, 0, 64, 64, (uintptr_t)0x2000));
    assert(rb4dx_window_images_lookup(&table, 7, 0, 32, 32) == (uintptr_t)0x1000);
    assert(rb4dx_window_images_lookup(&table, 7, 0, 64, 64) == (uintptr_t)0x2000);
    assert(rb4dx_window_images_presented(&table, 7, 0));
    uintptr_t preview = 0;
    assert(rb4dx_window_images_preview(&table, &preview) && preview == (uintptr_t)0x2000);
    assert(!rb4dx_window_images_preview(NULL, &preview));
    assert(!rb4dx_window_images_preview(&table, NULL));
}

int main(void) {
    test_each_swapchain_image_has_separate_identity();
    test_last_presented_image_wins_preview_selection();
    test_rejects_invalid_key_and_window_resize_keeps_latest();
    puts("PASS: per-image Vulkan buffer identity and final-frame selection");
    return 0;
}
