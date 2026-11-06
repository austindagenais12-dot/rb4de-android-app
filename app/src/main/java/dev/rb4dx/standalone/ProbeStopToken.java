// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.util.ArrayList;
import java.util.List;

/** A one-shot cancellation signal with race-safe late resource registration. */
final class ProbeStopToken {
    private boolean stopped;
    private final List<Runnable> stopActions = new ArrayList<>();

    synchronized boolean isStopped() {
        return stopped;
    }

    void onStop(Runnable action) {
        boolean runNow;
        synchronized (this) {
            runNow = stopped;
            if (!runNow) stopActions.add(action);
        }
        if (runNow) runSafely(action);
    }

    boolean requestStop() {
        List<Runnable> actions;
        synchronized (this) {
            if (stopped) return false;
            stopped = true;
            actions = new ArrayList<>(stopActions);
            stopActions.clear();
        }
        for (Runnable action : actions) runSafely(action);
        return true;
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Continue terminating other resources even if one was already closed.
        }
    }
}
