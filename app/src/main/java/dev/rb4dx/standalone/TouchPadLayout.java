// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

/** Pure geometry used by the Android touch renderer and JVM layout tests. */
final class TouchPadLayout {
    private TouchPadLayout() {}

    static long buttonAt(boolean guitar, float x, float y, float width, float height) {
        if (width <= 0 || height <= 0 || x < 0 || y < 0 || x > width || y > height) {
            return 0;
        }
        float radius = Math.min(width * 0.073f, height * 0.135f);
        if (guitar) {
            if (hit(x, y, width * .88f, height * .13f, radius))
                return Ps4InputState.OPTIONS;
            if (y < height * .72f || y > height * .95f) return 0;
            long[] keys = {Ps4InputState.CROSS, Ps4InputState.CIRCLE,
                    Ps4InputState.TRIANGLE, Ps4InputState.SQUARE, Ps4InputState.R1};
            return keys[Math.min(4, (int)(x / width * 5))];
        }
        if (hit(x, y, width * 0.5f, height * 0.14f, radius))
            return Ps4InputState.OPTIONS;
        float[] shoulderX = {.10f, .25f, .75f, .90f};
        long[] shoulders = {Ps4InputState.L2, Ps4InputState.L1,
                Ps4InputState.R1, Ps4InputState.R2};
        for (int i = 0; i < shoulderX.length; i++) {
            if (hit(x, y, width * shoulderX[i], height * .14f, radius)) return shoulders[i];
        }
        if (hit(x, y, width * .17f, height * .34f, radius)) return Ps4InputState.UP;
        if (hit(x, y, width * .25f, height * .48f, radius)) return Ps4InputState.RIGHT;
        if (hit(x, y, width * .17f, height * .62f, radius)) return Ps4InputState.DOWN;
        if (hit(x, y, width * .09f, height * .48f, radius)) return Ps4InputState.LEFT;
        if (hit(x, y, width * .85f, height * .34f, radius)) return Ps4InputState.TRIANGLE;
        if (hit(x, y, width * .94f, height * .48f, radius)) return Ps4InputState.CIRCLE;
        if (hit(x, y, width * .85f, height * .62f, radius)) return Ps4InputState.CROSS;
        if (hit(x, y, width * .76f, height * .48f, radius)) return Ps4InputState.SQUARE;
        return 0;
    }

    static int stickAt(boolean guitar, float x, float y, float width, float height) {
        if (guitar || width <= 0 || height <= 0) return 0;
        float radius = stickRadius(width, height);
        if (hit(x, y, width * .39f, height * .82f, radius)) return 1;
        if (hit(x, y, width * .60f, height * .82f, radius)) return 2;
        return 0;
    }

    static float stickRadius(float width, float height) {
        return Math.min(width * .085f, height * .15f);
    }

    static int[] stickAxis(float touchX, float touchY, float cx, float cy, float radius) {
        if (radius <= 0) return new int[] {128, 128};
        float dx = (touchX - cx) / radius;
        float dy = (touchY - cy) / radius;
        float length = (float)Math.hypot(dx, dy);
        if (length > 1) { dx /= length; dy /= length; }
        return new int[] {clamp(Math.round(128 + dx * 127)), clamp(Math.round(128 + dy * 127))};
    }

    private static boolean hit(float x, float y, float cx, float cy, float radius) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
