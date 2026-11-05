// SPDX-License-Identifier: LGPL-2.1-or-later
package com.winlator.xenvironment.components;

/**
 * Pure-Java rate limit and diagnostics for a surface heartbeat triggered by
 * Vortek's present notifications. Does not inspect any live Vulkan buffer.
 */
public final class LiveHeartbeatTracker {
    private final long minimumIntervalMs;
    private long lastAttemptMs = -1;
    private int attempts;
    private int successes;
    private int failures;
    private String lastFailure = "NONE";

    public LiveHeartbeatTracker(long minimumIntervalMs) {
        if (minimumIntervalMs <= 0) throw new IllegalArgumentException("interval must be positive");
        this.minimumIntervalMs = minimumIntervalMs;
    }

    public synchronized boolean tryBegin(long uptimeMs) {
        if (lastAttemptMs >= 0 && uptimeMs - lastAttemptMs < minimumIntervalMs) return false;
        lastAttemptMs = uptimeMs;
        attempts++;
        return true;
    }

    public synchronized void recordPosted() { successes++; }

    public synchronized void recordFailed(String reason) {
        failures++;
        lastFailure = reason;
    }

    public synchronized int attempted() { return attempts; }

    public synchronized int posted() { return successes; }

    public synchronized int failed() { return failures; }

    public synchronized String lastFailure() { return lastFailure; }
}
