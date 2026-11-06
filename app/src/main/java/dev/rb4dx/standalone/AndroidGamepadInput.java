// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Converts Android Bluetooth/USB gamepad signals to native PS4 pad controls. */
final class AndroidGamepadInput {
    private AndroidGamepadInput() {}

    static boolean handleKey(KeyEvent event, Ps4InputState pad) {
        if (!event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                && !event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                && !event.isFromSource(InputDevice.SOURCE_DPAD)) return false;
        long mask;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_A: mask = Ps4InputState.CROSS; break;
            case KeyEvent.KEYCODE_BUTTON_B: mask = Ps4InputState.CIRCLE; break;
            case KeyEvent.KEYCODE_BUTTON_X: mask = Ps4InputState.SQUARE; break;
            case KeyEvent.KEYCODE_BUTTON_Y: mask = Ps4InputState.TRIANGLE; break;
            case KeyEvent.KEYCODE_BUTTON_L1: mask = Ps4InputState.L1; break;
            case KeyEvent.KEYCODE_BUTTON_R1: mask = Ps4InputState.R1; break;
            case KeyEvent.KEYCODE_BUTTON_L2: mask = Ps4InputState.L2; break;
            case KeyEvent.KEYCODE_BUTTON_R2: mask = Ps4InputState.R2; break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: mask = Ps4InputState.L3; break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: mask = Ps4InputState.R3; break;
            case KeyEvent.KEYCODE_BUTTON_START: mask = Ps4InputState.OPTIONS; break;
            case KeyEvent.KEYCODE_BUTTON_SELECT: mask = Ps4InputState.TOUCHPAD; break;
            case KeyEvent.KEYCODE_DPAD_UP: mask = Ps4InputState.UP; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: mask = Ps4InputState.RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: mask = Ps4InputState.DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: mask = Ps4InputState.LEFT; break;
            case KeyEvent.KEYCODE_DPAD_CENTER: mask = Ps4InputState.CROSS; break;
            default: return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
            pad.setHardwareButton(mask, event.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        return false;
    }

    static boolean handleJoystick(MotionEvent event, Ps4InputState pad) {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        InputDevice device = event.getDevice();
        float rightX = axis(event, device, MotionEvent.AXIS_RX, MotionEvent.AXIS_Z);
        float rightY = axis(event, device, MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ);
        int leftTrigger = trigger(event, device, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE);
        int rightTrigger = trigger(event, device, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS);
        pad.setHardwareAxes(stick(event.getAxisValue(MotionEvent.AXIS_X)),
                stick(event.getAxisValue(MotionEvent.AXIS_Y)),
                stick(rightX), stick(rightY), leftTrigger, rightTrigger);

        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        long hat = 0;
        if (hatX < -0.5f) hat |= Ps4InputState.LEFT;
        if (hatX > 0.5f) hat |= Ps4InputState.RIGHT;
        if (hatY < -0.5f) hat |= Ps4InputState.UP;
        if (hatY > 0.5f) hat |= Ps4InputState.DOWN;
        pad.setHardwareHat(hat);
        return true;
    }

    private static float axis(MotionEvent event, InputDevice device, int primary, int backup) {
        return hasAxis(device, primary) ? event.getAxisValue(primary) : event.getAxisValue(backup);
    }

    private static int trigger(MotionEvent event, InputDevice device, int primary, int backup) {
        float value = axis(event, device, primary, backup);
        return Math.round(Math.max(0, Math.min(1, value)) * 255);
    }

    private static boolean hasAxis(InputDevice device, int axis) {
        return device != null && device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null;
    }

    private static int stick(float value) {
        return Math.max(0, Math.min(255, Math.round((value + 1.0f) * 127.5f)));
    }
}
