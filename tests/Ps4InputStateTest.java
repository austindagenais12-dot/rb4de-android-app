package dev.rb4dx.standalone;

public final class Ps4InputStateTest {
    public static void main(String[] args) {
        exactNativeProtocolAndMonotonicSequence();
        differentSourcesCannotReleaseEachOthersButtons();
        touchSticksOverrideOnlyWhileHeldAndClamp();
        resetRestoresNeutralControllerAndSequenceAdvances();
        hatAndKeysComposeWithoutClobberingEachOther();
        System.out.println("PASS: real PS4 input snapshots, merged controls and neutral release");
    }

    private static void exactNativeProtocolAndMonotonicSequence() {
        Ps4InputState state = new Ps4InputState();
        check("BACHATA/1 INPUT slot=0 seq=1 buttons=0x0 lx=128 ly=128 rx=128 ry=128 l2=0 r2=0 touch=0 tx=0 ty=0\n".equals(state.nextFrame()), "initial neutral wire frame");
        state.setHardwareButton(Ps4InputState.CROSS, true);
        state.setHardwareAxes(0, 255, 128, 255, 35, 100);
        check("BACHATA/1 INPUT slot=0 seq=2 buttons=0x4000 lx=0 ly=255 rx=128 ry=255 l2=35 r2=100 touch=0 tx=0 ty=0\n".equals(state.nextFrame()), "native frame with keys, axes");
    }

    private static void differentSourcesCannotReleaseEachOthersButtons() {
        Ps4InputState state = new Ps4InputState();
        state.setHardwareButton(Ps4InputState.CROSS, true);
        state.setTouchButtons(Ps4InputState.CROSS | Ps4InputState.CIRCLE);
        state.setHardwareButton(Ps4InputState.CROSS, false);
        check(state.nextFrame().contains("buttons=0x6000 "), "touch buttons retained after hardware release");
        state.clearTouch();
        check(state.nextFrame().contains("buttons=0x0 "), "touch buttons released");
    }

    private static void touchSticksOverrideOnlyWhileHeldAndClamp() {
        Ps4InputState state = new Ps4InputState();
        state.setHardwareAxes(180, 100, 80, 160, 300, -4);
        state.setTouchAxes(0, 256, -2, 42, true, false);
        check(state.nextFrame().contains("lx=0 ly=255 rx=80 ry=160 l2=255 r2=0 "), "touch left stick and clamped triggers");
        state.clearTouch();
        check(state.nextFrame().contains("lx=180 ly=100 rx=80 ry=160 "), "hardware stick restored after touch release");
    }

    private static void resetRestoresNeutralControllerAndSequenceAdvances() {
        Ps4InputState state = new Ps4InputState();
        state.setHardwareButton(Ps4InputState.OPTIONS, true);
        state.setTouchButtons(Ps4InputState.SQUARE);
        state.setTouchAxes(0, 0, 255, 255, true, true);
        state.nextFrame();
        state.reset();
        check("BACHATA/1 INPUT slot=0 seq=2 buttons=0x0 lx=128 ly=128 rx=128 ry=128 l2=0 r2=0 touch=0 tx=0 ty=0\n".equals(state.nextFrame()), "neutral reset preserves increasing sequence");
    }

    private static void hatAndKeysComposeWithoutClobberingEachOther() {
        Ps4InputState state = new Ps4InputState();
        state.setHardwareButton(Ps4InputState.UP, true);
        state.setHardwareHat(Ps4InputState.RIGHT);
        check(state.nextFrame().contains("buttons=0x30 "), "hat OR dpad keys");
        state.setHardwareHat(0);
        check(state.nextFrame().contains("buttons=0x10 "), "hat neutral does not release dpad key");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
