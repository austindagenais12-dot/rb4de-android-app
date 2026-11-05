// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Compares lightweight glibc-loader startup with the FEXCore guest CPU test.
 * Android may terminate foreign Linux ELF code with SIGSYS before main();
 * testing each boundary separately avoids guessing which layer is at fault.
 */
final class Arm64SmokeRunner {
    private static final int MAX_LOG_BYTES = 24 * 1024;
    private static final long TIMEOUT_SECONDS = 12;
    private static final String LOADER_NAME = "librb4dx_host_loader.so";

    static final class Result {
        final boolean succeeded;
        final String report;

        Result(boolean succeeded, String report) {
            this.succeeded = succeeded;
            this.report = report;
        }
    }

    private static final class StepResult {
        final int exitCode;
        final boolean completed;
        final String output;

        StepResult(int exitCode, boolean completed, String output) {
            this.exitCode = exitCode;
            this.completed = completed;
            this.output = output;
        }

        boolean smokePassed() {
            return completed && exitCode == 0 && output.contains("FEXCORE_SMOKE_OK");
        }
    }

    private Arm64SmokeRunner() {}

    static Result run(Context context, Path runtimeDirectory) {
        Path host = runtimeDirectory.resolve("host").normalize();
        Path smoke = host.resolve("fexcore-smoke");
        Path loader = new File(context.getApplicationInfo().nativeLibraryDir,
                LOADER_NAME).toPath();
        StringBuilder report = new StringBuilder("RB4DX_ARM64_DIAGNOSTIC_V2\n")
                .append("device=").append(Build.MODEL)
                .append(" androidApi=").append(Build.VERSION.SDK_INT)
                .append(" abi=").append(System.getProperty("os.arch"))
                .append("\nloader=").append(loader)
                .append("\nruntime=").append(runtimeDirectory)
                .append("\nseccomp=").append(seccompStatus()).append("\n");

        if (!Files.isRegularFile(loader)) {
            return new Result(false, report + "HOST_LOADER_MISSING\n");
        }
        if (!Files.isRegularFile(smoke)) {
            return new Result(false, report + "FEXCORE_SMOKE_MISSING\n");
        }

        StepResult plain = runStep(context, loader, host, smoke, false, false, false);
        appendStep(report, "loader_version", plain);

        // glibc may register restartable sequences during startup. Compare
        // the supported glibc tunable without changing Android permissions.
        StepResult tuned = runStep(context, loader, host, smoke, true, false, false);
        appendStep(report, "loader_version_rseq_disabled", tuned);

        StepResult smokeTuned = runStep(context, loader, host, smoke, true, false, true);
        appendStep(report, "fexcore_smoke_rseq_disabled", smokeTuned);

        // Extra linker tracing only when the guest CPU test still fails.
        if (!smokeTuned.smokePassed()) {
            StepResult traced = runStep(context, loader, host, smoke, true, true, true);
            appendStep(report, "fexcore_smoke_linker_trace", traced);
        }

        boolean passed = smokeTuned.smokePassed();
        report.insert(0, passed ? "FEXCORE_SMOKE_PASS\n" : "FEXCORE_SMOKE_FAILED\n");
        if (!passed && plain.exitCode == 159 && tuned.exitCode == 159) {
            report.append("INFERENCE: Both loader-only tests returned 159; "
                    + "a signal or early loader failure is possible.\n");
        }
        report.append("NOTE: This tests CPU execution, not PS4 game launch.\n");
        return new Result(passed, report.toString());
    }

    private static StepResult runStep(Context context, Path loader, Path host,
                                      Path smoke, boolean disableRseq,
                                      boolean traceLinker, boolean runSmoke) {
        File capture = null;
        Process process = null;
        try {
            capture = File.createTempFile("rb4dx-stage-", ".log", context.getCacheDir());
            ProcessBuilder builder = runSmoke
                    ? new ProcessBuilder(loader.toString(), "--library-path",
                            host.toString(), smoke.toString())
                    : new ProcessBuilder(loader.toString(), "--version");
            builder.directory(context.getFilesDir());
            builder.environment().put("HOME", context.getFilesDir().getAbsolutePath());
            builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            builder.environment().put("LD_LIBRARY_PATH", host.toString());
            if (disableRseq) {
                builder.environment().put("GLIBC_TUNABLES", "glibc.pthread.rseq=0");
            }
            if (traceLinker) builder.environment().put("LD_DEBUG", "libs");
            builder.redirectErrorStream(true);
            builder.redirectOutput(capture);

            process = builder.start();
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            return new StepResult(completed ? process.exitValue() : -1,
                    completed, readLimited(capture));
        } catch (Exception error) {
            return new StepResult(-2, false, "START_ERROR: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (capture != null) capture.delete();
        }
    }

    private static void appendStep(StringBuilder report, String stage, StepResult result) {
        report.append("\n=== ").append(stage).append(" ===\n")
                .append("exit=").append(result.exitCode)
                .append(result.completed ? "\n" : " (timed out or not started)\n")
                .append(result.output.isEmpty() ? "[no process output]\n" : result.output + "\n");
    }

    private static String seccompStatus() {
        try {
            List<String> lines = Files.readAllLines(Path.of("/proc/self/status"));
            for (String line : lines) {
                if (line.startsWith("Seccomp:")) return line.trim();
            }
        } catch (IOException ignored) {
            // Diagnostic metadata is optional.
        }
        return "unavailable";
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
