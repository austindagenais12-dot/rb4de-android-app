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

        // The actual v0.12.17 log lost its mid-run GPU events: repeated
        // scePadOpen/reset/close/stub entries occupied almost all 8 MB.
        String noisyPad = "[Lib.Pad] <Info> (joypad_reader) pad.cpp:357 scePadOpen: "
                + "scePadOpen user_id=1000 type=2 index=0 initialized=true\n"
                + "[Lib.Pad] <Info> (joypad_reader) pad.cpp:632 "
                + "scePadResetOrientation: called handle=20\n"
                + "[Lib.Pad] <Error> (joypad_reader) pad.cpp:106 "
                + "scePadDeviceClassGetExtendedInformation: (STUBBED) called\n"
                + "[Lib.Pad] <Warning> (joypad_reader) pad.cpp:82 scePadClose: called, handle: 20\n";
        String bridgeError = "[Render.Vulkan] PM4 WAIT_REG_MEM stalled "
                + "queue=gfx address=0x2e0f927e0\n";
        String newLog = "EARLY: Vulkan initialization\n"
                + noisyPad.repeat(20)
                + bridgeError
                + "[Lib.Pad] <Error> (joypad_reader) pad.cpp:999 scePadGetInfo: "
                + "an uncommon error that must not be removed\n"
                + noisyPad.repeat(20)
                + "LATE: normal process termination\n";
        Files.writeString(source, newLog);
        StartupLogStore.Retention useful = StartupLogStore.retainReadableDiagnostics(
                source.toFile(), saved, 700);
        String usefulText = Files.readString(saved);
        check(usefulText.contains(bridgeError), "mid-run GPU stall must survive controller spam");
        check(usefulText.contains("scePadOpen"), "keep first representative pad-open message");
        check(usefulText.contains("scePadResetOrientation"), "keep first representative reset message");
        check(usefulText.contains("scePadClose"), "keep first representative pad-close message");
        check(usefulText.contains("scePadDeviceClassGetExtendedInformation"),
                "keep first representative device-class stub message");
        check(usefulText.contains("scePadGetInfo: an uncommon error"),
                "do not filter unrelated pad errors");
        check(usefulText.contains("repeated controller polling lines omitted"),
                "summarize removed controller polling without misrepresenting the log");
        check(useful.omittedPadPollLines > 100,
                "the diagnostic should report how much polling it removed");
        check(useful.sourceBytes == Files.size(source),
                "report original capture bytes, not only filtered input bytes");
        check(useful.savedBytes <= 700, "filtered log must still obey the byte limit");
        check(Files.readString(source).equals(newLog), "never mutate the native capture file");

        // Nothing is filtered if there are no known repetitive poll lines.
        Files.writeString(source, shortLog);
        StartupLogStore.Retention noFiltering = StartupLogStore.retainReadableDiagnostics(
                source.toFile(), saved, 128);
        check(Files.readString(saved).equals(shortLog),
                "unrelated small diagnostics remain byte-for-byte unchanged");
        check(noFiltering.omittedPadPollLines == 0,
                "do not claim filtering when none occurred");

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
