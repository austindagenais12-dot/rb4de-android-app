#ifndef RB4DX_SURFACE_CONTROL_COMPAT_H
#define RB4DX_SURFACE_CONTROL_COMPAT_H

/*
 * Android NDK r26's <android/surface_control.h> uses C++-only declarations
 * (including default arguments and references). The standalone Vortek bridge
 * is C11, so expose only the official API 29-31 C ABI function signatures it
 * uses. The Android 16 release-callback function is resolved with dlsym.
 *
 * This is NOT a replacement implementation or a private Android API.
 */
#include <android/native_window.h>

typedef struct ASurfaceControl ASurfaceControl;
typedef struct ASurfaceTransaction ASurfaceTransaction;

enum {
    RB4DX_SURFACE_HIDE = 0,
    RB4DX_SURFACE_SHOW = 1,
    RB4DX_SURFACE_OPAQUE = 2
};

ASurfaceControl* ASurfaceControl_createFromWindow(ANativeWindow* parent, const char* debug_name);
void ASurfaceControl_acquire(ASurfaceControl* surface_control);
void ASurfaceControl_release(ASurfaceControl* surface_control);
ASurfaceTransaction* ASurfaceTransaction_create(void);
void ASurfaceTransaction_delete(ASurfaceTransaction* transaction);
void ASurfaceTransaction_apply(ASurfaceTransaction* transaction);
void ASurfaceTransaction_setVisibility(ASurfaceTransaction* transaction,
        ASurfaceControl* surface_control, int visibility);
void ASurfaceTransaction_reparent(ASurfaceTransaction* transaction,
        ASurfaceControl* surface_control, ASurfaceControl* new_parent);
void ASurfaceTransaction_setScale(ASurfaceTransaction* transaction,
        ASurfaceControl* surface_control, float x_scale, float y_scale);
void ASurfaceTransaction_setPosition(ASurfaceTransaction* transaction,
        ASurfaceControl* surface_control, int32_t x, int32_t y);
void ASurfaceTransaction_setBufferTransparency(ASurfaceTransaction* transaction,
        ASurfaceControl* surface_control, int transparency);

#endif
