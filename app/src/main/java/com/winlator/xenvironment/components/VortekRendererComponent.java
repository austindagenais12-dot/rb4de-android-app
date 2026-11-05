// SPDX-License-Identifier: LGPL-2.1-or-later
package com.winlator.xenvironment.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone app-private Android endpoint for the pinned Vortek protocol.
 *
 * The JNI names are intentionally compatible with the upstream Vortek
 * renderer. This is an actual Vulkan command server. Its first Android
 * screen-output milestones are a diagnostic live callback heartbeat and a
 * post-stop still-frame preview. Continuous game-video output is not ready.
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
    private final LiveHeartbeatTracker heartbeat = new LiveHeartbeatTracker(750);
    private volatile FramePreviewTrace lastPreviewTrace = new FramePreviewTrace();
    private volatile boolean previewOnly;
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

    public String previewDiagnosticReport() { return lastPreviewTrace.report(); }

    public String heartbeatReport() {
        return "android_live_transport_heartbeat_attempted=" + heartbeat.attempted() + '\n'
                + "android_live_transport_heartbeat_posted=" + heartbeat.posted() + '\n'
                + "android_live_transport_heartbeat_failed=" + heartbeat.failed() + '\n'
                + "android_live_transport_heartbeat_last_failure=" + heartbeat.lastFailure() + '\n'
                + "android_live_game_frame_posted=false\n";
    }

    /**
     * Diagnostic only. The producer must be stopped before hardware buffers
     * are sampled by Android's canvas: sampling while Vulkan is writing into
     * the same shared buffer would be undefined behavior.
     */
    public synchronized boolean stopAndRenderFinalFrame() {
        previewOnly = true;
        FramePreviewTrace trace = new FramePreviewTrace();
        lastPreviewTrace = trace;
        trace.begin("SERVER_STOP");
        if (serverPtr != 0) {
            stopNativeServer(serverPtr);
            serverPtr = 0;
        }
        trace.completed("SERVER_STOP");
        trace.begin("WINDOW_UPDATE");
        if (previewHolder == null) {
            trace.fail("SURFACE_HOLDER_MISSING", null);
            return false;
        }
        if (lastUpdatedWindow.get() == Integer.MIN_VALUE) {
            trace.fail("NO_WINDOW_UPDATE", null);
            return false;
        }
        trace.completed("WINDOW_UPDATE");
        trace.begin("SURFACE_VALIDATION");
        Surface surface = previewHolder.getSurface();
        if (surface == null || !surface.isValid()) {
            trace.fail("SURFACE_INVALID", null);
            return false;
        }
        trace.completed("SURFACE_VALIDATION");

        trace.begin("HARDWARE_BUFFER");
        HardwareBuffer buffer = getLastPresentedNativeHardwareBuffer();
        if (buffer == null) {
            trace.fail("BUFFER_UNAVAILABLE", null);
            return false;
        }
        trace.completed("HARDWARE_BUFFER");
        Bitmap bitmap = null;
        try {
            trace.begin("BITMAP_WRAP");
            bitmap = Bitmap.wrapHardwareBuffer(buffer, null);
            if (bitmap == null) {
                trace.fail("BITMAP_UNAVAILABLE", null);
                return false;
            }
            trace.completed("BITMAP_WRAP");
            trace.begin("CANVAS_LOCK");
            Canvas canvas = surface.lockHardwareCanvas();
            trace.completed("CANVAS_LOCK");
            try {
                trace.begin("CANVAS_DRAW");
                canvas.drawColor(Color.BLACK);
                Rect bounds = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(bitmap, null, bounds, paint);
                trace.completed("CANVAS_DRAW");
            } catch (RuntimeException exception) {
                trace.fail("CANVAS_DRAW_EXCEPTION", exception);
                throw exception;
            } finally {
                trace.begin("CANVAS_POST");
                try {
                    surface.unlockCanvasAndPost(canvas);
                    trace.completed("CANVAS_POST");
                } catch (RuntimeException exception) {
                    trace.fail("CANVAS_POST_EXCEPTION", exception);
                    throw exception;
                }
            }
            trace.begin("RETAIN_PREVIEW");
            synchronized (VortekRendererComponent.class) {
                if (retainedPreview != null) retainedPreview.recycle();
                retainedPreview = bitmap;
                bitmap = null;
            }
            trace.completed("RETAIN_PREVIEW");
            trace.posted();
            return true;
        } catch (RuntimeException exception) {
            trace.fail("EXCEPTION_AT_" + trace.lastAttempted(), exception);
            return false;
        } finally {
            if (bitmap != null) bitmap.recycle();
            buffer.close();
        }
    }

    public synchronized boolean start(String privateSocketPath) {
        if (serverPtr != 0) return true;
        previewOnly = false;
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
        previewOnly = true;
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
    private long getWindowHardwareBuffer(int windowId, int imageIndex, boolean preferBgra) {
        return acquireNativeBuffer(windowId, imageIndex, preferBgra, width, height);
    }

    // Called by the upstream renderer on present. Never sample the backing
    // HardwareBuffer here: Vortek has not supplied a completed GPU fence.
    // Instead paint independent diagnostic pixels to verify the live surface.
    @SuppressWarnings("unused")
    private void updateWindowContent(int windowId, int imageIndex) {
        if (nativeNotePresentedImage(windowId, imageIndex)) {
            lastUpdatedWindow.set(windowId);
            windowUpdates.incrementAndGet();
            if (!previewOnly) renderLiveHeartbeat();
        }
    }

    private void renderLiveHeartbeat() {
        if (!heartbeat.tryBegin(SystemClock.elapsedRealtime())) return;
        if (previewHolder == null) {
            heartbeat.recordFailed("SURFACE_HOLDER_MISSING");
            return;
        }
        Surface surface = previewHolder.getSurface();
        if (surface == null || !surface.isValid()) {
            heartbeat.recordFailed("SURFACE_INVALID");
            return;
        }
        Canvas canvas = null;
        String error = null;
        try {
            canvas = surface.lockHardwareCanvas();
            if (canvas == null) {
                error = "CANVAS_UNAVAILABLE";
            } else {
                canvas.drawColor(Color.rgb(7, 38, 64));
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.WHITE);
                paint.setTextSize(40f);
                float top = Math.max(55f, canvas.getHeight() / 2f - 50f);
                canvas.drawText("RB4DX DISPLAY LINK TEST", 24f, top, paint);
                paint.setTextSize(31f);
                paint.setColor(Color.rgb(100, 215, 255));
                canvas.drawText("Vortek present #" + windowUpdates.get(), 24f, top + 46f, paint);
                paint.setTextSize(25f);
                canvas.drawText("Diagnostic only - NOT game video", 24f, top + 82f, paint);
            }
        } catch (RuntimeException exception) {
            error = "CANVAS_" + exception.getClass().getSimpleName();
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas);
                } catch (RuntimeException exception) {
                    if (error == null) error = "POST_" + exception.getClass().getSimpleName();
                }
            }
        }
        if (error == null) heartbeat.recordPosted();
        else heartbeat.recordFailed(error);
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
    private native long acquireNativeBuffer(int windowId, int imageIndex, boolean preferBgra, int width, int height);
    private native boolean nativeNotePresentedImage(int windowId, int imageIndex);
    private native HardwareBuffer getLastPresentedNativeHardwareBuffer();
    private native void releaseNativeBuffers();
}
