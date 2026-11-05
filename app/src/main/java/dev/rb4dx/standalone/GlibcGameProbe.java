// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Execute the actual Linux ARM64 glibc loader on the selected game file. */
final class GlibcGameProbe {
    private static final int TIMEOUT_SECONDS = 12;
    private static final int MAX_OUTPUT_BYTES = 8192;

    static final class Result {
        final boolean succeeded;
        final String report;

        Result(boolean succeeded, String report) {
            this.succeeded = succeeded;
            this.report = report;
        }
    }

    private GlibcGameProbe() {}

    static Result inspect(Context context, Path runtimeDirectory, Path eboot) {
        Path host = runtimeDirectory.resolve("host").normalize();
        Path lib = Path.of(context.getApplicationInfo().nativeLibraryDir);
        Path loader = lib.resolve("librb4dx_host_loader.so");
        Path probe = lib.resolve("librb4dx_glibc_fileprobe.so");
        StringBuilder report = new StringBuilder("RB4DX_GLIBC_PROBE_V1\n")
                .append("mode=READ_ONLY_LINUX_ARM64_GLIBC\n")
                .append("loader=").append(loader).append('\n')
                .append("probe=").append(probe).append('\n');

        if (!Files.isRegularFile(loader) || !Files.isRegularFile(probe)
                || !Files.isRegularFile(eboot)) {
            return new Result(false, "RB4DX_GLIBC_GAME_ACCESS_FAILED\n" + report
                    + "reason=Missing loader, probe or eboot\n");
        }
        File log = null;
        Process process = null;
        try {
            log = File.createTempFile("rb4dx-glibc-", ".log", context.getCacheDir());
            ProcessBuilder builder = new ProcessBuilder(
                    loader.toString(), "--library-path", host.toString(),
                    probe.toString(), eboot.toString());
            builder.directory(context.getFilesDir());
            builder.environment().put("HOME", context.getFilesDir().getAbsolutePath());
            builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            builder.environment().put("LD_LIBRARY_PATH", host.toString());
            builder.environment().put("GLIBC_TUNABLES", "glibc.pthread.rseq=0");
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);
            process = builder.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            int exit = finished ? process.exitValue() : -1;
            byte[] output = Files.readAllBytes(log.toPath());
            String stdout = new String(output, 0, Math.min(output.length, MAX_OUTPUT_BYTES),
                    StandardCharsets.UTF_8);
            boolean passed = exit == 0 && stdout.contains("RB4DX_GLIBC_GAME_ACCESS_PASS")
                    && stdout.contains("linux_aarch64_glibc_open=success");
            report.append("exit=").append(exit)
                    .append("\nEMULATOR_GLIBC_OUTPUT:\n").append(stdout);
            if (output.length > MAX_OUTPUT_BYTES) report.append("\n[Log truncated]\n");
            return new Result(passed, (passed ? "RB4DX_GLIBC_GAME_ACCESS_PASS\n"
                    : "RB4DX_GLIBC_GAME_ACCESS_FAILED\n") + report);
        } catch (Exception error) {
            report.append("error=").append(error.getClass().getSimpleName())
                    .append(": ").append(error.getMessage()).append('\n');
            return new Result(false, "RB4DX_GLIBC_GAME_ACCESS_FAILED\n" + report);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (log != null) log.delete();
        }
    }
}
