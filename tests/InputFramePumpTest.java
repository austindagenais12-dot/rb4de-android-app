package dev.rb4dx.standalone;

public final class InputFramePumpTest {
    public static void main(String[] args) {
        Ps4InputState state = new Ps4InputState();
        InputFramePump pump = new InputFramePump(state, frame -> 0);
        pump.tick();
        check(pump.report().contains("input_frames_not_connected=1"), "not-connected feedback");
        state.setHardwareButton(Ps4InputState.CROSS, true);
        InputFramePump active = new InputFramePump(state, frame -> frame.length());
        active.tick();
        check(active.report().contains("input_frames_delivered_to_socket=1"), "success counted");
        check(active.report().contains("input_frames_with_buttons=1"), "actual press counted");
        check(active.report().contains("input_guest_acknowledgement=NOT_AVAILABLE"), "does not claim game received buttons");
        InputFramePump failed = new InputFramePump(state, frame -> -32);
        failed.tick();
        check(failed.report().contains("input_last_send_error=-32"), "write error recorded");
        System.out.println("PASS: input frame pump reports delivered, disconnected, pressed, and failed writes accurately");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
