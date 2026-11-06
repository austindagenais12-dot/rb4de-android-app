package dev.rb4dx.standalone;

public final class TouchPadLayoutTest {
    public static void main(String[] args) {
        check(TouchPadLayout.buttonAt(false, 0.85f * 400, 0.62f * 250, 400, 250)
                == Ps4InputState.CROSS, "green face button hit");
        check(TouchPadLayout.buttonAt(false, 0.17f * 400, 0.34f * 250, 400, 250)
                == Ps4InputState.UP, "dpad up hit");
        long[] frets = {Ps4InputState.CROSS, Ps4InputState.CIRCLE,
                Ps4InputState.TRIANGLE, Ps4InputState.SQUARE, Ps4InputState.R1};
        for (int i = 0; i < 5; i++) {
            check(TouchPadLayout.buttonAt(true, ((i + .5f) / 5f) * 400, .84f * 250,
                    400, 250) == frets[i], "guitar five-lane hitline " + i);
        }
        check(TouchPadLayout.buttonAt(true, .5f * 400, .47f * 250, 400, 250) == 0,
                "tapping incoming notes above the hit line does not press a fret");
        check(TouchPadLayout.stickAt(false, 0.39f * 400, 0.82f * 250, 400, 250) == 1,
                "left analog touch zone");
        check(TouchPadLayout.stickAt(false, 0.6f * 400, 0.82f * 250, 400, 250) == 2,
                "right analog touch zone");
        check(TouchPadLayout.stickAt(true, 0.39f * 400, 0.82f * 250, 400, 250) == 0,
                "guitar layout has no analog joysticks");
        check(TouchPadLayout.buttonAt(false, 900, 900, 400, 250) == 0,
                "touches outside the view do not create buttons");
        check(TouchPadLayout.stickAxis(0, 0, 0, 0, 40)[0] == 128,
                "neutral stick axis");
        check(TouchPadLayout.stickAxis(100, 0, 0, 0, 40)[0] == 255,
                "analog axis clamps to edge");
        System.out.println("PASS: multi-layout on-screen pad geometry and analog clamp");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
