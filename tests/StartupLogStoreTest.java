// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StartupLogStoreTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("rb4dx-log-test-");
        Path source = directory.resolve("capture.log");
        Path saved = directory.resolve("diagnostics/startup.log");

        String shortLog = "first Vulkan line\nshader probe\n";
        Files.writeString(source, shortLog);
        StartupLogStore.Retention small = StartupLogStore.retainHeadAndTail(source.toFile(), saved, 128);
        check(!small.truncated, "small log should be complete");
        check(small.sourceBytes == shortLog.getBytes(StandardCharsets.UTF_8).length,
                "source length should be reported");
        check(Files.readString(saved).equals(shortLog), "small log bytes should match");

        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        long exportedBytes = StartupLogStore.copyTo(saved, exported);
        check(exportedBytes == small.savedBytes, "export count should equal saved bytes");
        check(exported.toString(StandardCharsets.UTF_8).equals(shortLog),
                "export should preserve original content");

        // The original tail-only retention lost early initialization errors
        // whenever the guest generated tens of megabytes of pad-polling spam.
        String huge = "EARLY: Vulkan instance initialization failed\n"
                + "pad polling noise\n".repeat(100)
                + "LATE: PM4 WAIT_REG_MEM stalled\n";
        Files.writeString(source, huge);
        StartupLogStore.Retention large = StartupLogStore.retainHeadAndTail(source.toFile(), saved, 512);
        String retained = Files.readString(saved);
        check(large.truncated, "oversized log must say it was truncated");
        check(large.savedBytes <= 512, "saved file must be bounded");
        check(retained.contains(StartupLogStore.TRUNCATION_MARKER),
                "omitted middle lines must be labeled");
        check(retained.startsWith("EARLY: Vulkan instance initialization failed\n"),
                "retained log should begin with the real startup lines");
        check(retained.endsWith("LATE: PM4 WAIT_REG_MEM stalled\n"),
                "diagnostics must preserve the end of the run");

        Files.writeString(source, "replacement\n");
        StartupLogStore.retainHeadAndTail(source.toFile(), saved, 128);
        check(Files.readString(saved).equals("replacement\n"),
                "new run should atomically replace old run");

        boolean failedForMissingSource = false;
        try {
            StartupLogStore.retainHeadAndTail(directory.resolve("does-not-exist").toFile(), saved, 128);
        } catch (IOException expected) {
            failedForMissingSource = true;
        }
        check(failedForMissingSource, "missing source must signal error");
        check(Files.readString(saved).equals("replacement\n"),
                "failed update must preserve the last usable log");
        System.out.println("PASS: startup logs retained, bounded and exportable");
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
