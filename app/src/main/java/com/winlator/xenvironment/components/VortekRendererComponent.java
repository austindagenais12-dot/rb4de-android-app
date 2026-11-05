// SPDX-License-Identifier: LGPL-2.1-or-later
package com.winlator.xenvironment.components;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * Standalone app-private Android endpoint for the pinned Vortek protocol.
 *
 * The JNI names are intentionally compatible with the upstream Vortek
 * renderer. This is an actual Vulkan command server, but Android screen
 * presentation / X11 window tracking has NOT been connected yet.
 */
public final class VortekRendererComponent implements AutoCloseable {
    private final Options options = new Options();
    private final int width;
    private final int height;
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

    public VortekRendererComponent(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = Math.max(1, metrics.widthPixels);
        height = Math.max(1, metrics.heightPixels);
        initVulkanWrapper(context.getApplicationInfo().nativeLibraryDir, null);
    }

    public synchronized boolean start(String privateSocketPath) {
        if (serverPtr != 0) return true;
        serverPtr = startNativeServer(privateSocketPath, options);
        return serverPtr != 0;
    }

    public synchronized int connectedClients() {
        return serverPtr == 0 ? 0 : nativeConnectedClients(serverPtr);
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

    // Native GPU images are not yet composited to an Android Surface.
    @SuppressWarnings("unused")
    private void updateWindowContent(int windowId) {}

    private native long createVkContext(int clientFd, Options options);
    private native void destroyVkContext(long contextPtr);
    private native void initVulkanWrapper(String nativeLibraryDir, String libvulkanPath);
    private native boolean handleExtraDataRequest(long contextPtr, int requestCode, int requestLength);

    private native long startNativeServer(String path, Options options);
    private native void stopNativeServer(long ptr);
    private native int nativeConnectedClients(long ptr);
    private native long acquireNativeBuffer(int windowId, boolean preferBgra, int width, int height);
    private native void releaseNativeBuffers();
}
