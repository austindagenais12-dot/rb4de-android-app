// SPDX-License-Identifier: LGPL-2.1-or-later
package com.winlator.xenvironment.components;

/**
 * shadPS4's bundled Rock Band 4 presenter is running at 1280x720. Its output
 * image must not be allocated using the Android device's portrait resolution:
 * doing so places the actual game image in the top-left strip of a huge buffer.
 */
final class LiveFrameGeometry {
    static final int FRAME_WIDTH = 1280;
    static final int FRAME_HEIGHT = 720;

    static final class Layout {
        final int sourceWidth;
        final int sourceHeight;
        final int targetWidth;
        final int targetHeight;
        final int offsetX;
        final int offsetY;

        private Layout(int sourceWidth, int sourceHeight, int targetWidth,
                int targetHeight, int offsetX, int offsetY) {
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.targetWidth = targetWidth;
            this.targetHeight = targetHeight;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    static Layout fit(int viewportWidth, int viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("viewport size must be positive");
        }
        float scale = Math.min((float) viewportWidth / FRAME_WIDTH,
                (float) viewportHeight / FRAME_HEIGHT);
        int targetWidth = Math.max(1, Math.min(viewportWidth, Math.round(FRAME_WIDTH * scale)));
        int targetHeight = Math.max(1, Math.min(viewportHeight, Math.round(FRAME_HEIGHT * scale)));
        return new Layout(FRAME_WIDTH, FRAME_HEIGHT, targetWidth, targetHeight,
                (viewportWidth - targetWidth) / 2, (viewportHeight - targetHeight) / 2);
    }
}
