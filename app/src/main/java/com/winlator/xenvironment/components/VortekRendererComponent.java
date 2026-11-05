// SPDX-License-Identifier: LGPL-2.1-or-later
package com.winlator.xenvironment.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone app-private Android endpoint for the pinned Vortek protocol.
 *
 * The JNI names are intentionally compatible with the upstream Vortek
 * renderer. This is an actual Vulkan command server. Its first Android
 * screen-output milestone is an end-of-probe still-frame preview, after
 * the Vulkan producer has stopped. Continuous screen output is not ready.
 */
public final class VortekRendererComponent implements AutoCloseable {
    // Retain the GPU source until a later preview replaces it so Android's
    // asynchronous compositor never samples a prematurely freed buffer.
    private static Bitmap retainedPreview;
    private final Options options = new Options();
    private final int width;
    private final int height;
    private final SurfaceHolder previewHolder;
    private final AtomicInteger lastUpdatedWindow = new AtomicInteger(Integer.MIN_VALUE);
    private final AtomicInteger windowUpdates = new AtomicInteger();
    private long serverPtr;

    static {
        System.loadLibrary("winlator");
        System.loadLibrary("vortekrenderer");
        System.loadLibrary("rb4dx_vortek_bridge");
    }

    public static final class Options {
        // Vulkan 1.3.128, matching the upstream server's default API cap.
        public int vkMaxVersion = (1 << 22) | (3 << 12) | 128;
        public short maxDeviceMemory = 0;
        public short imageCacheSize = 256;
        public byte resourceMemoryType = 0;
        public String[] exposedDeviceExtensions = null;
    }

    public VortekRendererComponent(Context context, SurfaceHolder holder, String libvulkanPath) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = Math.max(1, metrics.widthPixels);
        height = Math.max(1, metrics.heightPixels);
        previewHolder = holder;
        // The pinned Vortek JNI already loads custom AdrenoTools drivers when
        // provided an absolute library path. Null preserves system Vulkan.
        initVulkanWrapper(context.getApplicationInfo().nativeLibraryDir, libvulkanPath);
        if (!nativeVulkanReady()) {
            throw new IllegalStateException("Vortek Vulkan initialization failed for "
                    + (libvulkanPath == null ? "System Adreno" : "Mesa Turnip")
                    + ": required function pointers unavailable. "
                    + "Check libhook_impl.so and libmain_hook.so in APK.");
        }
    }

    public int windowUpdateCount() { return windowUpdates.get(); }

    /**
     * Diagnostic only. The producer must be stopped before hardware buffers
     * are sampled by Android's canvas: sampling while Vulkan is writing into
     * the same shared buffer would be undefined behavior.
     */
    public synchronized boolean stopAndRenderFinalFrame() {
        if (serverPtr != 0) {
            stopNativeServer(serverPtr);
            serverPtr = 0;
        }
        if (previewHolder == null || lastUpdatedWindow.get() == Integer.MIN_VALUE) {
            return false;
        }
        Surface surface = previewHolder.getSurface();
        if (surface == null || !surface.isValid()) return false;

        HardwareBuffer buffer = getNativeHardwareBuffer(lastUpdatedWindow.get());
        if (buffer == null) return false;
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.wrapHardwareBuffer(buffer, null);
            if (bitmap == null) return false;
            Canvas canvas = surface.lockHardwareCanvas();
            try {
                canvas.drawColor(Color.BLACK);
                Rect bounds = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(bitmap, null, bounds, paint);
            } finally {
                surface.unlockCanvasAndPost(canvas);
            }
            synchronized (VortekRendererComponent.class) {
                if (retainedPreview != null) retainedPreview.recycle();
                retainedPreview = bitmap;
                bitmap = null;
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            if (bitmap != null) bitmap.recycle();
            buffer.close();
        }
    }

    public synchronized boolean start(String privateSocketPath) {
        if (serverPtr != 0) return true;
        serverPtr = startNativeServer(privateSocketPath, options);
        return serverPtr != 0;
    }

    public synchronized int connectedClients() {
        return serverPtr == 0 ? 0 : nativeConnectedClients(serverPtr);
    }

    public synchronized int createdContexts() {
        return serverPtr == 0 ? 0 : nativeCreatedContexts(serverPtr);
    }

    public synchronized int handshakesOk() {
        return serverPtr == 0 ? 0 : nativeHandshakesOk(serverPtr);
    }

    public synchronized int handshakesFailed() {
        return serverPtr == 0 ? 0 : nativeHandshakesFailed(serverPtr);
    }

    @Override
    public synchronized void close() {
        if (serverPtr != 0) {
            stopNativeServer(serverPtr);
            serverPtr = 0;
        }
        releaseNativeBuffers();
    }

    // Called by the upstream JNI Vortek request handler. These dimensions
    // are provisional until native X11 window IDs can be tracked.
    @SuppressWarnings("unused")
    private int getWindowWidth(int windowId) { return width; }

    @SuppressWarnings("unused")
    private int getWindowHeight(int windowId) { return height; }

    @SuppressWarnings("unused")
    private long getWindowHardwareBuffer(int windowId, boolean preferBgra) {
        return acquireNativeBuffer(windowId, preferBgra, width, height);
    }

    // Called by the upstream renderer after a window's GPU content changes.
    // Keep a thread-safe window ID for a still-frame preview at probe exit.
    @SuppressWarnings("unused")
    private void updateWindowContent(int windowId) {
        lastUpdatedWindow.set(windowId);
        windowUpdates.incrementAndGet();
    }

    private native long createVkContext(int clientFd, Options options);
    private native void destroyVkContext(long contextPtr);
    private native void initVulkanWrapper(String nativeLibraryDir, String libvulkanPath);
    private native boolean nativeVulkanReady();
    private native boolean handleExtraDataRequest(long contextPtr, int requestCode, int requestLength);

    private native long startNativeServer(String path, Options options);
    private native void stopNativeServer(long ptr);
    private native int nativeConnectedClients(long ptr);
    private native int nativeCreatedContexts(long ptr);
    private native int nativeHandshakesOk(long ptr);
    private native int nativeHandshakesFailed(long ptr);
    private native long acquireNativeBuffer(int windowId, boolean preferBgra, int width, int height);
    private native HardwareBuffer getNativeHardwareBuffer(int windowId);
    private native void releaseNativeBuffers();
}
