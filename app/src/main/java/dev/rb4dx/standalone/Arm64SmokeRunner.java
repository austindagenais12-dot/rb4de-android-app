// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * First genuine process-execution test. Attempts to run the upstream FEXCore
 * ARM64 smoke binary through the packaged glibc loader, without launching a
 * PS4 title, creating a window, or changing system settings.
 */
final class Arm64SmokeRunner {
    private static final int MAX_LOG_BYTES = 64 * 1024;
    private static final long TIMEOUT_SECONDS = 20;
    private static final String LOADER_NAME = "librb4dx_host_loader.so";

    static final class Result {
        final boolean succeeded;
        final String report;

        Result(boolean succeeded, String report) {
            this.succeeded = succeeded;
            this.report = report;
        }
    }

    private Arm64SmokeRunner() {}

    static Result run(Context context, Path runtimeDirectory) {
        Path host = runtimeDirectory.resolve("host").normalize();
        Path smoke = host.resolve("fexcore-smoke");
        Path loader = new File(context.getApplicationInfo().nativeLibraryDir,
                LOADER_NAME).toPath();
        if (!Files.isRegularFile(loader)) {
            return new Result(false, "HOST_LOADER_MISSING: " + loader
                    + "\nThe APK must package and extract the ARM64 glibc loader.");
        }
        if (!Files.isRegularFile(smoke)) {
            return new Result(false, "FEXCORE_SMOKE_MISSING: " + smoke);
        }

        File capture = null;
        Process process = null;
        try {
            capture = File.createTempFile("rb4dx-smoke-", ".log", context.getCacheDir());
            ProcessBuilder builder = new ProcessBuilder(
                    loader.toString(), "--library-path", host.toString(), smoke.toString());
            builder.directory(context.getFilesDir());
            builder.environment().put("HOME", context.getFilesDir().getAbsolutePath());
            builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            builder.environment().put("LD_LIBRARY_PATH", host.toString());
            builder.redirectErrorStream(true);
            builder.redirectOutput(capture);

            process = builder.start();
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            int exitCode = completed ? process.exitValue() : -1;
            String output = readLimited(capture);
            boolean passed = completed && exitCode == 0
                    && output.contains("FEXCORE_SMOKE_OK");
            return new Result(passed,
                    (passed ? "FEXCORE_SMOKE_PASS" : completed
                            ? "FEXCORE_SMOKE_FAILED" : "FEXCORE_SMOKE_TIMEOUT")
                    + "\nexit=" + exitCode
                    + "\nloader=" + loader
                    + "\nruntime=" + runtimeDirectory
                    + "\n\n" + output);
        } catch (Exception error) {
            return new Result(false, "FEXCORE_LAUNCH_ERROR: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage()
                    + "\nloader=" + loader
                    + "\nsmoke=" + smoke);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (capture != null) capture.delete();
        }
    }

    private static String readLimited(File file) throws IOException {
        byte[] bytes = new byte[MAX_LOG_BYTES];
        int used = 0;
        try (InputStream input = Files.newInputStream(file.toPath())) {
            while (used < bytes.length) {
                int count = input.read(bytes, used, bytes.length - used);
                if (count < 0) break;
                used += count;
            }
        }
        return new String(bytes, 0, used, StandardCharsets.UTF_8)
                + (file.length() > MAX_LOG_BYTES ? "\n[Log truncated]" : "");
    }
}
