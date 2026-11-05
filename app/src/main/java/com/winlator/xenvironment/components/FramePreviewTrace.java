// SPDX-License-Identifier: LGPL-2.1-or-later
package com.winlator.xenvironment.components;

/**
 * Portable diagnostic state for the optional final-frame preview.
 * It intentionally records only fixed stage identifiers and exception types,
 * never file paths, graphic content, or exception messages.
 */
public final class FramePreviewTrace {
    private String result = "NOT_RUN";
    private String lastAttempted = "NONE";
    private String lastCompleted = "NONE";
    private String failure = "NONE";
    private String exceptionType = "NONE";

    public void begin(String stage) {
        lastAttempted = stage;
    }

    public void completed(String stage) {
        lastCompleted = stage;
    }

    /** Preserve the first failure, including failures before entering the canvas. */
    public void fail(String reason, Throwable cause) {
        if ("FAILED".equals(result)) return;
        result = "FAILED";
        failure = reason;
        if (cause != null) exceptionType = cause.getClass().getSimpleName();
    }

    public void posted() {
        if ("FAILED".equals(result)) return;
        result = "POSTED";
    }

    public String lastAttempted() {
        return lastAttempted;
    }

    public String report() {
        return "android_preview_result=" + result + '\n'
                + "android_preview_last_attempted=" + lastAttempted + '\n'
                + "android_preview_last_completed=" + lastCompleted + '\n'
                + "android_preview_failure=" + failure + '\n'
                + "android_preview_exception=" + exceptionType + '\n';
    }
}
