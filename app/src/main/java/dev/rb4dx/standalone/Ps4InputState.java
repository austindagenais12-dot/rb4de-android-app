// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

/** Merges hardware and multitouch controls into the pinned shadPS4 Bachata input protocol. */
final class Ps4InputState {
    // OrbisPadButtonDataOffset values in the pinned ARM64 shadPS4 runtime.
    static final long L3 = 0x2, R3 = 0x4, OPTIONS = 0x8;
    static final long UP = 0x10, RIGHT = 0x20, DOWN = 0x40, LEFT = 0x80;
    static final long L2 = 0x100, R2 = 0x200, L1 = 0x400, R1 = 0x800;
    static final long TRIANGLE = 0x1000, CIRCLE = 0x2000;
    static final long CROSS = 0x4000, SQUARE = 0x8000, TOUCHPAD = 0x100000;

    private long hardwareButtons;
    private long hardwareHat;
    private long touchButtons;
    private final int[] hardwareAxes = {128, 128, 128, 128, 0, 0};
    private int touchLx = 128, touchLy = 128, touchRx = 128, touchRy = 128;
    private boolean touchLeftActive, touchRightActive;
    private long sequence;

    synchronized void setHardwareButton(long button, boolean pressed) {
        if (pressed) hardwareButtons |= button;
        else hardwareButtons &= ~button;
    }

    synchronized void setHardwareHat(long hatButtons) {
        hardwareHat = hatButtons & (UP | DOWN | LEFT | RIGHT);
    }

    synchronized void setHardwareAxes(int lx, int ly, int rx, int ry, int l2, int r2) {
        int[] values = {lx, ly, rx, ry, l2, r2};
        for (int i = 0; i < values.length; i++) hardwareAxes[i] = clamp(values[i]);
    }

    synchronized void setTouchButtons(long buttons) {
        touchButtons = buttons;
    }

    synchronized void setTouchAxes(int lx, int ly, int rx, int ry,
            boolean leftActive, boolean rightActive) {
        touchLx = clamp(lx);
        touchLy = clamp(ly);
        touchRx = clamp(rx);
        touchRy = clamp(ry);
        touchLeftActive = leftActive;
        touchRightActive = rightActive;
    }

    synchronized void clearTouch() {
        touchButtons = 0;
        touchLeftActive = false;
        touchRightActive = false;
    }

    synchronized void clearHardware() {
        hardwareButtons = 0;
        hardwareHat = 0;
        hardwareAxes[0] = hardwareAxes[1] = hardwareAxes[2] = hardwareAxes[3] = 128;
        hardwareAxes[4] = hardwareAxes[5] = 0;
    }

    synchronized void reset() {
        clearHardware();
        clearTouch();
    }

    synchronized String nextFrame() {
        long buttons = hardwareButtons | hardwareHat | touchButtons;
        int lx = touchLeftActive ? touchLx : hardwareAxes[0];
        int ly = touchLeftActive ? touchLy : hardwareAxes[1];
        int rx = touchRightActive ? touchRx : hardwareAxes[2];
        int ry = touchRightActive ? touchRy : hardwareAxes[3];
        int l2 = (touchButtons & L2) != 0 ? 255 : hardwareAxes[4];
        int r2 = (touchButtons & R2) != 0 ? 255 : hardwareAxes[5];
        return "BACHATA/1 INPUT slot=0 seq=" + (++sequence)
                + " buttons=0x" + Long.toHexString(buttons)
                + " lx=" + lx + " ly=" + ly + " rx=" + rx + " ry=" + ry
                + " l2=" + l2 + " r2=" + r2 + " touch=0 tx=0 ty=0\n";
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
