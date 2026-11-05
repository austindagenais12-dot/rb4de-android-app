// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Real Android -> Linux ARM64 control-channel probe. Runs the upstream emulator
 * with a deliberately nonexistent eboot path; it must connect to the Android
 * AF_UNIX listener, report its content validation error, then exit cleanly.
 * No game assets, saved games, or Deluxe mod files are accessed or modified.
 */
final class RuntimeBridgeProbe {
    private static final int TIMEOUT_MS = 12000;
    private static final int MAX_LOG_BYTES = 16 * 1024;
    private static final String LOADER_NAME = "librb4dx_host_loader.so";

    static final class Result {
        final boolean succeeded;
        final String report;

        Result(boolean succeeded, String report) {
            this.succeeded = succeeded;
            this.report = report;
        }
    }

    private RuntimeBridgeProbe() {}

    static Result run(Context context, Path runtimeDirectory) {
        Path host = runtimeDirectory.resolve("host").normalize();
        Path executable = host.resolve("shadps4-arm64-fex");
        Path loader = new File(context.getApplicationInfo().nativeLibraryDir,
                LOADER_NAME).toPath();
        Path files = context.getFilesDir().toPath().toAbsolutePath().normalize();
        Path socket = files.resolve("rb4dx-control-" + Long.toHexString(System.nanoTime()) + ".sock");
        Path invalidGame = files.resolve("rb4dx-bridge-probe-nonexistent-eboot.bin");

        StringBuilder report = new StringBuilder("RB4DX_CONTROL_BRIDGE_V1\n")
                .append("loader=").append(loader)
                .append("\nemulator=").append(executable)
                .append("\nsocket=").append(socket)
                .append("\nintent=TEST_INVALID_GAME_ONLY\n");
        if (!Files.isRegularFile(loader) || !Files.isRegularFile(executable)) {
            return new Result(false, report + "MISSING_NATIVE_EXECUTABLE\n");
        }
        if (Files.exists(invalidGame)) {
            return new Result(false, report + "REFUSING_TO_TOUCH_EXISTING_GAME_FILE\n");
        }

        int serverFd = -1;
        ExecutorService listener = null;
        Process process = null;
        File capture = null;
        try {
            serverFd = NativeRuntimeSocket.openServer(socket.toString());
            if (serverFd < 0) {
                return new Result(false, report + "SOCKET_BIND_FAILED errno=" + (-serverFd));
            }
            final int listeningFd = serverFd;
            listener = Executors.newSingleThreadExecutor();
            Future<String> incoming = listener.submit(
                    () -> NativeRuntimeSocket.readSession(listeningFd, TIMEOUT_MS));

            capture = File.createTempFile("rb4dx-bridge-", ".log", context.getCacheDir());
            ProcessBuilder builder = new ProcessBuilder(
                    loader.toString(), "--library-path", host.toString(),
                    executable.toString(),
                    "--bachata-socket", socket.toString(),
                    "--bachata-storage-root", files.toString(),
                    "--game", invalidGame.toString());
            builder.directory(context.getFilesDir());
            builder.environment().put("HOME", files.toString());
            builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            builder.environment().put("LD_LIBRARY_PATH", host.toString());
            builder.environment().put("GLIBC_TUNABLES", "glibc.pthread.rseq=0");
            builder.redirectErrorStream(true);
            builder.redirectOutput(capture);

            process = builder.start();
            boolean completed = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            int exitCode = completed ? process.exitValue() : -1;
            String frames = incoming.get(16, TimeUnit.SECONDS);
            String stdout = readLimited(capture);

            boolean receivedHello = frames.contains("BACHATA/1 HELLO version=1");
            boolean receivedStarting = frames.contains("BACHATA/1 EVENT Starting");
            boolean receivedExpectedError = frames.contains(
                    "BACHATA/1 ERROR code=CONTENT_INVALID");
            boolean receivedStop = frames.contains(
                    "BACHATA/1 EVENT Stopped exit_code=1");
            boolean passed = completed && exitCode == 1 && receivedHello && receivedStarting
                    && receivedExpectedError && receivedStop;
            report.append("exit=").append(exitCode)
                    .append("\nhello=").append(receivedHello)
                    .append(" starting=").append(receivedStarting)
                    .append(" invalid_content=").append(receivedExpectedError)
                    .append(" stopped=").append(receivedStop)
                    .append("\n\nSOCKET_FRAMES:\n").append(frames)
                    .append("\nEMULATOR_OUTPUT:\n").append(stdout)
                    .append("\n");
            return new Result(passed, (passed ? "RB4DX_CONTROL_BRIDGE_PASS\n"
                    : "RB4DX_CONTROL_BRIDGE_FAILED\n") + report);
        } catch (Throwable error) {
            report.append("START_ERROR: ").append(error.getClass().getSimpleName())
                    .append(": ").append(error.getMessage()).append("\n");
            return new Result(false, "RB4DX_CONTROL_BRIDGE_FAILED\n" + report);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (listener != null) listener.shutdownNow();
            if (serverFd >= 0) NativeRuntimeSocket.closeServer(serverFd);
            if (capture != null) capture.delete();
            try {
                Files.deleteIfExists(socket);
            } catch (IOException ignored) {
                // A stale socket path here is harmless and isolated to this app.
            }
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
