// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

/** Safety-bounded diagnostic durations; not an unbounded gameplay mode. */
final class GameProbeOptions {
    static final int QUICK_MS = 18000;
    static final int DEFAULT_MS = 60000;
    static final int EXTENDED_MS = 120000;

    private GameProbeOptions() {}

    static int requireDurationMs(int durationMs) {
        if (durationMs != QUICK_MS && durationMs != DEFAULT_MS
                && durationMs != EXTENDED_MS) {
            throw new IllegalArgumentException("Unsupported diagnostic duration: " + durationMs);
        }
        return durationMs;
    }
}
