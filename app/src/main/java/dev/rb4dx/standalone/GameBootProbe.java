// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Controlled, time-limited startup of the real shadPS4 emulator with the
 * selected vanilla Rock Band 4 executable. This is a diagnostic, not Play.
 * The external game folder is only mounted as read-only /app0 by upstream.
 */
final class GameBootProbe {
    private static final int TIMEOUT_MS = 18000;
    private static final int MAX_LOG_BYTES = 32 * 1024;

    static final class Result {
        final String report;

        Result(String report) {
            this.report = report;
        }
    }

    private GameBootProbe() {}

    static Result run(Context context, Path runtimeDirectory, Uri gameTree,
            String expectedTitle) {
        StringBuilder report = new StringBuilder("RB4DX_GAME_STARTUP_DIAGNOSTIC_V1\n")
                .append("mode=REAL_SHADPS4_VANILLA_TIMED_STARTUP\n")
                .append("max_runtime_ms=").append(TIMEOUT_MS).append('\n');
        if (!Environment.isExternalStorageManager()) {
            return failed(report, "All files access permission was not granted");
        }

        // Always recheck the chosen game and title ID; never boot an arbitrary
        // file based solely on a saved URI or a previous successful report.
        GamePreflight.Result preflight = GamePreflight.inspect(context, gameTree, expectedTitle);
        if (!preflight.succeeded || !preflight.report.contains("app_ver=02.21")) {
            return failed(report, "Rock Band 4 region/version preflight did not match 02.21\n"
                    + preflight.report);
        }
        String gameFolder = GamePreflight.rawPrimaryPath(gameTree);
        if (gameFolder == null) {
            return failed(report, "Selected game folder is not mapped to primary shared storage");
        }
        Path gameEboot = Path.of(gameFolder, "eboot.bin");
        Path files = context.getFilesDir().toPath().toAbsolutePath().normalize();
        // Upstream path_util.cpp first checks <cwd>/user (portable layout).
        // The previously used <files> cwd fell back to a nested XDG path whose
        // parent directories did not exist. Prepare the portable user root
        // within this app's private storage, matching DeluxeInstaller's layout.
        Path emulatorWorkingDir = files.resolve("shadps4");
        Path portableUserDir = emulatorWorkingDir.resolve("user");
        Path host = runtimeDirectory.resolve("host").normalize();
        Path emulator = host.resolve("shadps4-arm64-fex");
        Path loader = Path.of(context.getApplicationInfo().nativeLibraryDir,
                "librb4dx_host_loader.so");
        Path socket = files.resolve("rb4dx-real-game-" + Long.toHexString(System.nanoTime())
                + ".sock");
        if (!Files.isRegularFile(gameEboot) || !Files.isReadable(gameEboot)
                || !Files.isRegularFile(loader) || !Files.isRegularFile(emulator)) {
            return failed(report, "Native loader, emulator or selected eboot.bin unavailable");
        }
        report.append("expected_title_id=").append(expectedTitle).append('\n')
                .append("game_path=").append(gameEboot).append('\n')
                .append("app_data_only=true\n")
                .append("deluxe_plugin_requested=false\n")
                .append("game_patch_loading=false\n");

        int serverFd = -1;
        ExecutorService listener = null;
        Process process = null;
        File capture = null;
        try {
            // This is idempotent and never replaces user content. The emulator
            // needs an existing portable UserDir before its static path setup.
            // Its default user manager may then create home/1000 beneath it.
            Files.createDirectories(portableUserDir.resolve("home").resolve("1000"));
            report.append("portable_user_dir=").append(portableUserDir).append('\n')
                    .append("portable_user_dir_ready=true\n");

            serverFd = NativeRuntimeSocket.openServer(socket.toString());
            if (serverFd < 0) return failed(report, "Control socket errno=" + (-serverFd));
            int listeningFd = serverFd;
            listener = Executors.newSingleThreadExecutor();
            Future<String> incoming = listener.submit(
                    () -> NativeRuntimeSocket.readSession(listeningFd, TIMEOUT_MS));

            capture = File.createTempFile("rb4dx-real-boot-", ".log", context.getCacheDir());
            ProcessBuilder builder = new ProcessBuilder(
                    loader.toString(), "--library-path", host.toString(),
                    emulator.toString(),
                    "--bachata-socket", socket.toString(),
                    "--bachata-storage-root", files.toString(),
                    "--game", gameEboot.toString(),
                    "--ignore-game-patch", "--config-clean", "--fullscreen", "false");
            builder.directory(emulatorWorkingDir.toFile());
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
            int exit = completed ? process.exitValue() : -1;
            String frames = incoming.get(TIMEOUT_MS + 4000L, TimeUnit.MILLISECONDS);
            String emulatorLog = tail(capture);

            // These are checkpoints, not a claim that the game rendered.
            boolean accepted = !frames.contains("ERROR code=CONTENT_INVALID")
                    && !emulatorLog.contains("Invalid PS4 executable");
            boolean reachedEmulator = emulatorLog.contains("Starting shadps4 emulator")
                    || emulatorLog.contains("Game id:");
            boolean reachedGuest = frames.contains("BACHATA/1 EVENT Running");
            String checkpoint = !accepted ? "CONTENT_REJECTED"
                    : reachedGuest ? "GUEST_RUNNING_EVENT"
                    : reachedEmulator ? "EMULATOR_RUN_REACHED"
                    : frames.contains("BACHATA/1 EVENT Starting")
                            ? "CONTROL_CONNECTED_BEFORE_WINDOW" : "PROCESS_STARTED_ONLY";

            report.append("exit=").append(exit).append('\n')
                    .append("timed_out=").append(!completed).append('\n')
                    .append("early_content_accepted=").append(accepted).append('\n')
                    .append("emulator_run_marker=").append(reachedEmulator).append('\n')
                    .append("guest_running_event=").append(reachedGuest).append('\n')
                    .append("checkpoint=").append(checkpoint).append('\n')
                    .append("\nSOCKET_FRAMES:\n").append(frames)
                    .append("\nEMULATOR_OUTPUT_TAIL:\n").append(emulatorLog)
                    .append("\nnote=This is not a successful PS4 game boot or Deluxe launch.\n");
            return new Result("RB4DX_GAME_STARTUP_PROBE_COMPLETE\n" + report);
        } catch (Throwable error) {
            return failed(report, error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (listener != null) listener.shutdownNow();
            if (serverFd >= 0) NativeRuntimeSocket.closeServer(serverFd);
            if (capture != null) capture.delete();
            try {
                Files.deleteIfExists(socket);
            } catch (IOException ignored) {
                // Isolated to this app's private files directory.
            }
        }
    }

    private static Result failed(StringBuilder report, String reason) {
        return new Result("RB4DX_GAME_STARTUP_PROBE_FAILED\n" + report
                + "reason=" + reason + '\n');
    }

    private static String tail(File file) throws IOException {
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            long size = reader.length();
            int count = (int) Math.min(size, MAX_LOG_BYTES);
            byte[] bytes = new byte[count];
            reader.seek(size - count);
            reader.readFully(bytes);
            return (size > count ? "[Earlier output omitted]\n" : "")
                    + new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
