package com.winlator.xenvironment.components;

public final class LiveHeartbeatTrackerTest {
    public static void main(String[] args) {
        LiveHeartbeatTracker tracker = new LiveHeartbeatTracker(750);
        require(tracker.posted() == 0 && tracker.attempted() == 0,
                "never claim a heartbeat before drawing");
        require(tracker.tryBegin(1000), "first callback may draw");
        require(!tracker.tryBegin(1749), "skip callbacks within the rate limit");
        require(tracker.tryBegin(1750), "draw when the full interval elapsed");
        require(tracker.attempted() == 2, "two attempts only");
        tracker.recordFailed("SURFACE_INVALID");
        require(tracker.failed() == 1 && tracker.posted() == 0, "failed draws never count as posted");
        require("SURFACE_INVALID".equals(tracker.lastFailure()), "reason tracked");
        tracker.recordPosted();
        require(tracker.posted() == 1, "successful draw counted");
        require(!tracker.tryBegin(1700), "monotonic clock anomaly cannot bypass throttle");
        require(tracker.tryBegin(2500), "later frame may draw");
        require(tracker.attempted() == 3, "all attempts counted");
        System.out.println("PASS: live display heartbeat is throttled, observable and failure-safe");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
