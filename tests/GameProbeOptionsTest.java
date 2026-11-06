package dev.rb4dx.standalone;

public final class GameProbeOptionsTest {
    public static void main(String[] args) {
        for (int duration : new int[] {18000, 60000, 120000}) {
            if (GameProbeOptions.requireDurationMs(duration) != duration) {
                throw new AssertionError("allowed duration not preserved: " + duration);
            }
        }
        if (GameProbeOptions.DEFAULT_MS != 60000) {
            throw new AssertionError("extended test must be default");
        }
        for (int duration : new int[] {-1000, 0, 17999, 30000, 60001, 180000}) {
            try {
                GameProbeOptions.requireDurationMs(duration);
                throw new AssertionError("unsupported duration accepted: " + duration);
            } catch (IllegalArgumentException expected) {
                // Good: arbitrary, unsafe durations must not reach JNI.
            }
        }
        System.out.println("PASS: bounded 18/60/120-second diagnostic policy");
    }
}
