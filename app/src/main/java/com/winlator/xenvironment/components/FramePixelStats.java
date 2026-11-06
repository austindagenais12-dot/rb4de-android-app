// SPDX-License-Identifier: LGPL-2.1-or-later
package com.winlator.xenvironment.components;

/** Classification of a small sample from a safely stopped Vulkan producer. */
final class FramePixelStats {
    private FramePixelStats() {}

    static String classify(int[] pixels) {
        if (pixels == null || pixels.length == 0) return "UNREADABLE";
        int opaque = 0, colored = 0;
        for (int pixel : pixels) {
            if (((pixel >>> 24) & 0xff) < 8) continue;
            opaque++;
            if (((pixel >>> 16) & 0xff) > 8
                    || ((pixel >>> 8) & 0xff) > 8
                    || (pixel & 0xff) > 8) colored++;
        }
        if (opaque == 0) return "TRANSPARENT";
        return colored > 0 ? "CONTENT" : "BLACK";
    }
}
