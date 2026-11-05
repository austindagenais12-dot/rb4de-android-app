// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

/** Tiny Android/Bionic AF_UNIX listener for the upstream emulator's runtime protocol. */
final class NativeRuntimeSocket {
    static {
        System.loadLibrary("rb4dx_bridge");
    }

    private NativeRuntimeSocket() {}

    /** Returns an owned descriptor, or negative errno. Only app-private paths are supplied. */
    static native int openServer(String socketPath);
    /** Blocks for a bounded time, accepting one client and capturing protocol frames. */
    static native String readSession(int serverFd, int timeoutMs);
    static native void closeServer(int serverFd);
}
