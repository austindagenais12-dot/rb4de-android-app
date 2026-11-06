// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.system.Os;
import android.view.SurfaceHolder;

import com.winlator.xenvironment.components.VortekRendererComponent;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controlled, time-limited startup of the real shadPS4 emulator with the
 * selected vanilla Rock Band 4 executable. This is a diagnostic, not Play.
 * The external game folder is only mounted as read-only /app0 by upstream.
 */
final class GameBootProbe {
    private static final int MAX_LOG_BYTES = 16 * 1024;
    private static final int MAX_CRASH_LINES = 48;

    static final class Result {
        final String report;
        final Path fullLog;

        Result(String report) {
            this(report, null);
        }

        Result(String report, Path fullLog) {
            this.report = report;
            this.fullLog = fullLog;
        }
    }

    private GameBootProbe() {}

    static Result run(Context context, Path runtimeDirectory, Uri gameTree,
            String expectedTitle, SurfaceHolder previewSurface, Ps4InputState controller,
            int timeoutMs, ProbeStopToken stop) {
        GameProbeOptions.requireDurationMs(timeoutMs);
        return runInternal(context, runtimeDirectory, gameTree, expectedTitle, previewSurface,
                controller, timeoutMs, stop, false);
    }

    static Result play(Context context, Path runtimeDirectory, Uri gameTree,
            String expectedTitle, SurfaceHolder gameSurface, Ps4InputState controller,
            ProbeStopToken stop) {
        return runInternal(context, runtimeDirectory, gameTree, expectedTitle, gameSurface,
                controller, 0, stop, true);
    }

    private static Result runInternal(Context context, Path runtimeDirectory, Uri gameTree,
            String expectedTitle, SurfaceHolder previewSurface, Ps4InputState controller,
            int timeoutMs, ProbeStopToken stop, boolean play) {
        StringBuilder report = new StringBuilder("RB4DX_GAME_STARTUP_DIAGNOSTIC_V2\n")
                .append("mode=").append(play ? "FULLSCREEN_PLAY" : "REAL_SHADPS4_EMBEDDED_X11_STARTUP")
                .append('\n')
                .append("max_runtime_ms=").append(timeoutMs).append('\n');
        if (stop.isStopped()) return cancelled(report);
        if (!Environment.isExternalStorageManager()) {
            return failed(report, "All files access permission was not granted", stop);
        }

        // Always recheck the chosen game and title ID; never boot an arbitrary
        // file based solely on a saved URI or a previous successful report.
        GamePreflight.Result preflight = GamePreflight.inspect(context, gameTree, expectedTitle);
        if (!preflight.succeeded || !preflight.report.contains("app_ver=02.21")) {
            return failed(report, "Rock Band 4 region/version preflight did not match 02.21\n"
                    + preflight.report, stop);
        }
        if (stop.isStopped()) return cancelled(report);
        String gameFolder = GamePreflight.rawPrimaryPath(gameTree);
        if (gameFolder == null) {
            return failed(report, "Selected game folder is not mapped to primary shared storage", stop);
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
        Path vulkanDriverFile = host.resolve("vulkan/icd.d/vortek.json");
        Path loader = Path.of(context.getApplicationInfo().nativeLibraryDir,
                "librb4dx_host_loader.so");
        Path x11SocketBridge = Path.of(context.getApplicationInfo().nativeLibraryDir,
                "librb4dx_x11_redirect.so");
        Path socket = files.resolve("rb4dx-real-game-" + Long.toHexString(System.nanoTime())
                + ".sock");
        Path vortekSocket = files.resolve("rb4dx-vortek-" + Long.toHexString(System.nanoTime())
                + ".sock");
        Path audioSocket = files.resolve("ra-" + Long.toHexString(System.nanoTime()) + ".sock");
        // Diagnose each dependency independently: one missing packaged .so
        // should not look like a bad game dump or a broken GPU driver.
        boolean gameReady = Files.isRegularFile(gameEboot) && Files.isReadable(gameEboot);
        boolean loaderReady = Files.isRegularFile(loader);
        boolean emulatorReady = Files.isRegularFile(emulator);
        boolean x11BridgeReady = Files.isRegularFile(x11SocketBridge);
        boolean icdReady = Files.isRegularFile(vulkanDriverFile)
                && Files.isReadable(vulkanDriverFile);
        report.append("eboot_available=").append(gameReady).append('\n')
                .append("native_loader_available=").append(loaderReady).append('\n')
                .append("emulator_available=").append(emulatorReady).append('\n')
                .append("x11_redirect_available=").append(x11BridgeReady).append('\n')
                .append("vulkan_icd_available=").append(icdReady).append('\n');
        if (!gameReady || !loaderReady || !emulatorReady || !x11BridgeReady || !icdReady) {
            return failed(report, "Required startup dependency missing (see *_available fields)", stop);
        }
        report.append("expected_title_id=").append(expectedTitle).append('\n')
                .append("game_path=").append(gameEboot).append('\n')
                .append("app_data_only=true\n")
                .append("deluxe_plugin_requested=false\n")
                .append("game_patch_loading=false\n")
                .append("vulkan_icd_file=").append(vulkanDriverFile).append('\n');

        // This selects the Android driver behind Vortek. The glibc shadPS4
        // process must still use its pinned, app-private Vortek client ICD.
        SharedPreferences gpuPreferences = context.getSharedPreferences("rb4dx", Context.MODE_PRIVATE);
        String driverChoice = GpuDriverChoice.normalize(gpuPreferences.getString(
                "gpu_driver_choice", gpuPreferences.getBoolean("gpu_turnip_enabled", true)
                        ? "turnip27" : "system"));
        Path turnipLibrary = GpuDriverChoice.libraryPath(
                Path.of(context.getApplicationInfo().nativeLibraryDir), driverChoice);
        if (turnipLibrary != null && !Files.isRegularFile(turnipLibrary)) {
            report.append("android_gpu_driver=").append(driverChoice)
                    .append("\nturnip_driver_available=false\n");
            return failed(report, "Selected bundled Turnip driver is missing; install the full APK or select System Adreno", stop);
        }
        Path hookImpl = Path.of(context.getApplicationInfo().nativeLibraryDir,
                "libhook_impl.so");
        Path mainHook = Path.of(context.getApplicationInfo().nativeLibraryDir,
                "libmain_hook.so");
        report.append("turnip_hook_impl_available=").append(Files.isRegularFile(hookImpl)).append('\n')
                .append("turnip_main_hook_available=").append(Files.isRegularFile(mainHook)).append('\n');
        if (turnipLibrary != null && (!Files.isRegularFile(hookImpl) || !Files.isRegularFile(mainHook))) {
            return failed(report, "Turnip requires bundled AdrenoTools hook libraries; missing libhook_impl.so or libmain_hook.so", stop);
        }
        report.append("android_gpu_driver=").append(driverChoice).append('\n')
                .append("turnip_driver_available=")
                .append(turnipLibrary == null || Files.isRegularFile(turnipLibrary)).append('\n');

        int serverFd = -1;
        ExecutorService listener = null;
        ExecutorService logReader = null;
        Future<Long> logFuture = null;
        ScheduledExecutorService inputSender = null;
        InputFramePump inputPump = null;
        Process process = null;
        File capture = null;
        StandaloneX11Server x11 = null;
        VortekRendererComponent vortek = null;
        BachataAudioSocket audio = null;
        String oldMesaCache = Os.getenv("MESA_SHADER_CACHE_DIR");
        try {
            if (stop.isStopped()) return cancelled(report);
            // This is idempotent and never replaces user content. The emulator
            // needs an existing portable UserDir before its static path setup.
            // Its default user manager may then create home/1000 beneath it.
            Files.createDirectories(portableUserDir.resolve("home").resolve("1000"));
            Path mesaCacheDir = ShaderCachePaths.forTitle(files, expectedTitle);
            Files.createDirectories(mesaCacheDir);
            // Vortek's Android/Bionic driver runs in THIS process; child-only
            // ProcessBuilder variables would not reach its Mesa pipeline cache.
            Os.setenv("MESA_SHADER_CACHE_DIR", mesaCacheDir.toString(), true);
            report.append("portable_user_dir=").append(portableUserDir).append('\n')
                    .append("portable_user_dir_ready=true\n")
                    .append("mesa_shader_cache_dir=").append(mesaCacheDir).append('\n')
                    .append("mesa_shader_cache_persistent=true\n");
            if (stop.isStopped()) return cancelled(report);

            // This is a genuine private Xorg socket, not the SDL dummy driver.
            // A complete on-screen renderer and Vulkan proxy are still pending.
            x11 = StandaloneX11Server.start(context, runtimeDirectory);
            report.append("x11_ready=true\n")
                    .append("x11_display=").append(x11.display).append('\n')
                    .append("x11_authentication=true\n");
            if (stop.isStopped()) return cancelled(report);

            // The glibc Vortek client communicates with a separate Android
            // Vulkan server using a Unix socket and shared-memory FDs. Keep
            // the endpoint app-private and tied to this one launch session.
            // Preview is only taken when the producer is fully stopped.
            // The embedded X11 window is not yet connected to a live surface.
            vortek = new VortekRendererComponent(context, previewSurface,
                    turnipLibrary == null ? null : turnipLibrary.toString());
            if (!vortek.start(vortekSocket.toString())) {
                return failed(report, "Unable to start Android Vortek Vulkan transport server", stop);
            }
            report.append("vortek_server_ready=true\n")
                    .append("vortek_socket=").append(vortekSocket).append('\n');

            // The pinned emulator routes game audio to Bachata's Unix socket.
            // Without this server, guest AudioOut silently discards samples.
            audio = new BachataAudioSocket(audioSocket);
            audio.start();
            report.append("bachata_audio_bridge_started=true\n");

            serverFd = NativeRuntimeSocket.openServer(socket.toString());
            if (serverFd < 0) return failed(report, "Control socket errno=" + (-serverFd), stop);
            int listeningFd = serverFd;
            stop.onStop(() -> NativeRuntimeSocket.cancelSession(listeningFd));
            if (stop.isStopped()) return cancelled(report);
            listener = Executors.newSingleThreadExecutor();
            Future<String> incoming = listener.submit(
                    () -> NativeRuntimeSocket.readSession(listeningFd, play ? 0 : timeoutMs));

            capture = File.createTempFile("rb4dx-real-boot-", ".log", context.getCacheDir());
            ProcessBuilder builder = new ProcessBuilder(
                    loader.toString(), "--library-path", host.toString(),
                    emulator.toString(),
                    "--bachata-socket", socket.toString(),
                    "--bachata-storage-root", files.toString(),
                    "--game", gameEboot.toString(),
                    "--ignore-game-patch", "--config-clean", "--fullscreen", play ? "true" : "false");
            builder.directory(emulatorWorkingDir.toFile());
            builder.environment().put("HOME", files.toString());
            builder.environment().put("MESA_SHADER_CACHE_DIR", mesaCacheDir.toString());
            builder.environment().put("XDG_CACHE_HOME", files.resolve("shader-cache").toString());
            builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            builder.environment().put("LD_LIBRARY_PATH", host.toString());
            // The Linux Vulkan loader does not search the Android app's
            // private runtime directory on its own. Use the packaged glibc
            // Vortek driver manifest explicitly; never claim a renderer is
            // attached until its separate Android server exists.
            builder.environment().put("VK_DRIVER_FILES", vulkanDriverFile.toString());
            builder.environment().put("VK_ICD_FILENAMES", vulkanDriverFile.toString());
            builder.environment().put("VK_LOADER_DEBUG", "error,warn,driver");
            builder.environment().put("BACHATA_VORTEK_LOG_LEVEL", "2");
            builder.environment().put("BACHATA_VORTEK_SOCKET", vortekSocket.toString());
            builder.environment().put("BACHATA_ALSA_SOCKET", audio.socketPath());
            // The pinned ARM64 emulator already contains an async-signal-safe
            // crash reporter. Capture PC/LR, register state and module maps
            // if the GPU communication thread jumps through a null pointer.
            if (play) {
                // Handled guest faults can emit thousands of register lines;
                // retain ordinary stderr/exit reporting during normal Play.
                builder.environment().remove("BACHATA_CRASH_REGISTERS");
            } else {
                builder.environment().put("BACHATA_CRASH_REGISTERS", "1");
            }
            // The Debian libX11/libxcb client uses /tmp/.X11-unix/Xn, whereas
            // the embedded Android server must bind inside its private TMPDIR.
            // Translate only this session's display socket at connect(2).
            builder.environment().put("LD_PRELOAD", x11SocketBridge.toString());
            builder.environment().put("RB4DX_X11_SOCKET_DIR",
                    files.resolve("x11-session/tmp/.X11-unix").toString());
            builder.environment().put("RB4DX_X11_DISPLAY_NUM", x11.display.substring(1));
            builder.environment().put("GLIBC_TUNABLES", "glibc.pthread.rseq=0");
            builder.environment().put("DISPLAY", x11.display);
            builder.environment().put("XAUTHORITY", x11.authority.toString());
            builder.environment().put("SDL_VIDEODRIVER", "x11");
            builder.environment().put("XDG_RUNTIME_DIR", files.resolve("x11-session/runtime").toString());
            builder.redirectErrorStream(true);
            if (!play) builder.redirectOutput(capture);

            if (stop.isStopped()) return cancelled(report);
            process = builder.start();
            Process activeProcess = process;
            stop.onStop(activeProcess::destroyForcibly);
            if (play) {
                logReader = Executors.newSingleThreadExecutor();
                File destination = capture;
                logFuture = logReader.submit(() -> ProcessLogDrain.capture(
                        activeProcess.getInputStream(), destination.toPath(), stop::isStopped));
            }
            // Use the native emulator's already-implemented controller snapshot
            // reader. A bounded 20 Hz sender lets button holds persist during
            // the diagnostic while avoiding a flood of socket writes.
            inputPump = new InputFramePump(controller,
                    frame -> NativeRuntimeSocket.sendInputFrame(listeningFd, frame));
            InputFramePump activeInput = inputPump;
            inputSender = Executors.newSingleThreadScheduledExecutor();
            inputSender.scheduleAtFixedRate(activeInput::tick, 0, 50, TimeUnit.MILLISECONDS);
            boolean completed;
            if (play) {
                process.waitFor();
                completed = true;
            } else {
                completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            }
            boolean stoppedByUser = stop.isStopped();
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            int exit = completed ? process.exitValue() : -1;
            inputSender.shutdownNow();
            inputSender.awaitTermination(750, TimeUnit.MILLISECONDS);
            if (logFuture != null) {
                report.append("play_log_source_bytes=")
                        .append(logFuture.get(5, TimeUnit.SECONDS)).append('\n');
            }
            // On timeout or Stop, release the native listener before collecting
            // its trace; a missing connection must not delay UI responsiveness.
            NativeRuntimeSocket.cancelSession(listeningFd);
            String frames = incoming.get(4, TimeUnit.SECONDS);
            String emulatorLog = tail(capture);
            String crashDiagnostics = crashDiagnostics(capture);
            String gpuWaitDiagnostics = GpuWaitSummary.fromLog(capture.toPath());
            int vortekConnections = vortek.connectedClients();
            int vortekContexts = vortek.createdContexts();
            int vortekHandshakesOk = vortek.handshakesOk();
            int vortekHandshakesFailed = vortek.handshakesFailed();
            int windowUpdateCount = vortek.windowUpdateCount();
            boolean finalFramePosted = vortek.stopAndRenderFinalFrame();

            // These are checkpoints, not a claim that the game rendered.
            boolean accepted = !frames.contains("ERROR code=CONTENT_INVALID")
                    && !emulatorLog.contains("Invalid PS4 executable");
            boolean reachedGuest = frames.contains("BACHATA/1 EVENT Running");
            // The log is capped to 32 KB, so the initial Run marker is often
            // omitted after many guest symbol resolution warnings.
            boolean reachedEmulator = reachedGuest
                    || emulatorLog.contains("Starting shadps4 emulator")
                    || emulatorLog.contains("Game id:");
            String checkpoint = !accepted ? "CONTENT_REJECTED"
                    : reachedGuest ? "GUEST_RUNNING_EVENT"
                    : reachedEmulator ? "EMULATOR_RUN_REACHED"
                    : frames.contains("BACHATA/1 EVENT Starting")
                            ? "CONTROL_CONNECTED_BEFORE_WINDOW" : "PROCESS_STARTED_ONLY";

            report.append("exit=").append(exit).append('\n')
                    .append("timed_out=").append(!completed && !stoppedByUser).append('\n')
                    .append("stopped_by_user=").append(stoppedByUser).append('\n')
                    .append("early_content_accepted=").append(accepted).append('\n')
                    .append("emulator_run_marker=").append(reachedEmulator).append('\n')
                    .append("guest_running_event=").append(reachedGuest).append('\n')
                    .append("vortek_client_connections=").append(vortekConnections).append('\n')
                    .append("vortek_handshakes_ok=").append(vortekHandshakesOk).append('\n')
                    .append("vortek_handshakes_failed=").append(vortekHandshakesFailed).append('\n')
                    .append("vortek_contexts_created=").append(vortekContexts).append('\n')
                    .append("vulkan_proxy_attached=").append(vortekContexts > 0).append('\n')
                    .append("vortek_window_updates=").append(windowUpdateCount).append('\n')
                    .append(vortek.heartbeatReport())
                    .append(audio.report())
                    .append(inputPump.report())
                    .append("android_final_frame_preview_posted=").append(finalFramePosted).append('\n')
                    .append(vortek.previewDiagnosticReport())
                    .append("crash_register_capture_enabled=true\n")
                    .append("checkpoint=").append(checkpoint).append('\n')
                    .append(NativeCrashSummary.fromText(crashDiagnostics))
                    .append(gpuWaitDiagnostics)
                    .append("\nSOCKET_FRAMES:\n").append(frames)
                    .append("\nCRASH_DIAGNOSTICS:\n").append(crashDiagnostics)
                    .append("\nEMULATOR_OUTPUT_TAIL:\n").append(emulatorLog)
                    .append(play ? "\nnote=Foreground Play ended; report records diagnostics, not verified playable game progress.\n"
                            : "\nnote=Experimental live frame output requires observing changing game video; this remains a startup probe, not a Deluxe launch.\n");
            return retainLog("RB4DX_GAME_STARTUP_PROBE_COMPLETE\n" + report, capture, files);
        } catch (Throwable error) {
            return retainLog("RB4DX_GAME_STARTUP_PROBE_FAILED\n" + report
                    + "timed_out=false\nstopped_by_user=" + stop.isStopped() + '\n'
                    + "reason=" + error.getClass().getSimpleName() + ": "
                    + error.getMessage() + '\n', capture, files);
        } finally {
            // JNI poll does not respond to Java Thread.interrupt(). Always
            // cancel the native session here, including errors during setup
            // and analysis, and let it detach the accepted client before the
            // listening descriptor can be closed or reused by another test.
            if (serverFd >= 0) NativeRuntimeSocket.cancelSession(serverFd);
            if (inputSender != null) inputSender.shutdownNow();
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (audio != null) audio.close();
            if (vortek != null) vortek.close();
            if (x11 != null) x11.close();
            if (listener != null) {
                listener.shutdown();
                try {
                    if (!listener.awaitTermination(2, TimeUnit.SECONDS)) {
                        listener.shutdownNow();
                    }
                } catch (InterruptedException interrupted) {
                    listener.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            if (logReader != null) logReader.shutdownNow();
            if (serverFd >= 0) NativeRuntimeSocket.closeServer(serverFd);
            if (capture != null) capture.delete();
            try {
                if (oldMesaCache == null) Os.unsetenv("MESA_SHADER_CACHE_DIR");
                else Os.setenv("MESA_SHADER_CACHE_DIR", oldMesaCache, true);
            } catch (android.system.ErrnoException ignored) {
                // Do not hide the emulator's diagnostic result on cleanup.
            }
            try {
                Files.deleteIfExists(socket);
            } catch (IOException ignored) {
                // Isolated to this app's private files directory.
            }
        }
    }

    private static Result failed(StringBuilder report, String reason, ProbeStopToken stop) {
        return new Result("RB4DX_GAME_STARTUP_PROBE_FAILED\n" + report
                + "timed_out=false\nstopped_by_user=" + stop.isStopped() + '\n'
                + "reason=" + reason + '\n');
    }

    private static Result cancelled(StringBuilder report) {
        return new Result("RB4DX_GAME_STARTUP_PROBE_CANCELLED\n" + report
                + "timed_out=false\nstopped_by_user=true\n");
    }

    private static Result retainLog(String report, File capture, Path files) {
        if (capture == null || !capture.isFile()) {
            return new Result(report + "full_log_available=false\n");
        }
        try {
            Path destination = files.resolve("diagnostics/rb4dx-last-startup.log");
            StartupLogStore.Retention saved = StartupLogStore.retainReadableDiagnostics(
                    capture, destination, StartupLogStore.MAX_SAVED_BYTES);
            return new Result(report + "full_log_available=true\n"
                    + "full_log_source_bytes=" + saved.sourceBytes + '\n'
                    + "full_log_filtered_source_bytes=" + saved.filteredSourceBytes + '\n'
                    + "full_log_omitted_pad_poll_lines=" + saved.omittedPadPollLines + '\n'
                    + "full_log_saved_bytes=" + saved.savedBytes + '\n'
                    + "full_log_truncated=" + saved.truncated + '\n'
                    + "full_log_capture=" + (saved.omittedPadPollLines > 0
                            ? saved.truncated ? "PAD_POLL_FILTERED_START_AND_END" : "PAD_POLL_FILTERED"
                            : saved.truncated ? "START_AND_END" : "COMPLETE")
                    + '\n', saved.path);
        } catch (IOException logError) {
            // Never conceal the actual guest crash because storing its log failed.
            return new Result(report + "full_log_available=false\n"
                    + "full_log_error=" + logError.getClass().getSimpleName() + '\n');
        }
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

    /**
     * Keep the last crash signatures, reporter registers, and relevant module
     * mappings independently of the bounded log tail. Rapid controller
     * polling can otherwise bury the actual fault in thousands of log lines.
     */
    private static String crashDiagnostics(File file) throws IOException {
        ArrayDeque<String> lines = new ArrayDeque<>();
        boolean inMappings = false;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(),
                StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("[Bachata.Crash] maps begin")) {
                    inMappings = true;
                } else if (line.contains("[Bachata.Crash] maps end")) {
                    inMappings = false;
                }
                boolean relevantMapping = inMappings
                        && (line.contains("shadps4-arm64-fex")
                                || line.contains("libvulkan_vortek")
                                || line.contains("libvulkan.so"));
                if (!line.contains("[Bachata.Crash]")
                        && !line.contains("[Bachata.Symbols]")
                        && !line.contains("Unhandled access violation")
                        && !line.contains("FEX HLE call")
                        && !line.contains("<Critical>")
                        && !relevantMapping) {
                    continue;
                }
                lines.addLast(line);
                if (lines.size() > MAX_CRASH_LINES) lines.removeFirst();
            }
        }
        return lines.isEmpty() ? "No native crash signature captured\n"
                : String.join("\n", lines) + "\n";
    }
}
