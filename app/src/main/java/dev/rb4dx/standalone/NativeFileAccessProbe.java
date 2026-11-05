// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

/** Read-only POSIX open/fstat/pread test in the app's native Android process. */
final class NativeFileAccessProbe {
    static { System.loadLibrary("rb4dx_bridge"); }

    private NativeFileAccessProbe() {}

    static native String inspect(String absoluteEbootPath);
}
