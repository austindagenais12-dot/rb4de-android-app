package com.winlator.xenvironment.components;

public final class FramePreviewTraceTest {
    public static void main(String[] args) {
        missingWindowIsExplained();
        invalidSurfaceIsDistinctFromMissingBuffer();
        firstExceptionSurvivesCanvasCleanup();
        postedPreviewReportsSuccess();
        System.out.println("PASS: preview failure stages and first-exception diagnostics");
    }

    private static void missingWindowIsExplained() {
        FramePreviewTrace trace = new FramePreviewTrace();
        trace.begin("WINDOW_UPDATE");
        trace.fail("NO_WINDOW_UPDATE", null);
        require(trace.report().contains("android_preview_result=FAILED\n"), "missing window result");
        require(trace.report().contains("android_preview_failure=NO_WINDOW_UPDATE\n"), "missing window reason");
        require(trace.report().contains("android_preview_last_attempted=WINDOW_UPDATE\n"), "missing window stage");
    }

    private static void invalidSurfaceIsDistinctFromMissingBuffer() {
        FramePreviewTrace trace = new FramePreviewTrace();
        trace.begin("WINDOW_UPDATE");
        trace.completed("WINDOW_UPDATE");
        trace.begin("SURFACE_VALIDATION");
        trace.fail("SURFACE_INVALID", null);
        require(trace.report().contains("android_preview_last_completed=WINDOW_UPDATE\n"), "prior completed stage");
        require(trace.report().contains("android_preview_failure=SURFACE_INVALID\n"), "invalid surface reason");
        require(!trace.report().contains("android_preview_failure=BUFFER_UNAVAILABLE"), "buffer is not blamed");
    }

    private static void firstExceptionSurvivesCanvasCleanup() {
        FramePreviewTrace trace = new FramePreviewTrace();
        trace.begin("CANVAS_DRAW");
        trace.fail("CANVAS_DRAW_EXCEPTION", new IllegalStateException("private file path must not leak"));
        trace.begin("CANVAS_POST");
        trace.fail("CANVAS_POST_EXCEPTION", new UnsupportedOperationException("not safe to expose"));
        String report = trace.report();
        require(report.contains("android_preview_failure=CANVAS_DRAW_EXCEPTION\n"), "first failure preserved");
        require(report.contains("android_preview_exception=IllegalStateException\n"), "exception class preserved");
        require(!report.contains("private file path"), "exception message redacted");
        require(!report.contains("UnsupportedOperationException"), "cleanup does not mask first exception");
    }

    private static void postedPreviewReportsSuccess() {
        FramePreviewTrace trace = new FramePreviewTrace();
        trace.begin("CANVAS_POST");
        trace.completed("CANVAS_POST");
        trace.posted();
        require(trace.report().contains("android_preview_result=POSTED\n"), "posted status");
        require(trace.report().contains("android_preview_failure=NONE\n"), "no failure for success");
        require(trace.report().contains("android_preview_last_completed=CANVAS_POST\n"), "posted stage completed");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
