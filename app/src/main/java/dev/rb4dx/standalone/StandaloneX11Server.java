// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rb4dx.standalone;

import android.content.Context;
import android.system.Os;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Isolated, authenticated Linux X11 window-server process for the game's SDL3.
 * Display readiness does NOT mean the server has an Android renderer or a
 * Vulkan-presentable surface. Call only on a background worker thread.
 */
final class StandaloneX11Server implements AutoCloseable {
    private static final int STARTUP_TIMEOUT_MS = 7000;
    private static final byte[] MAGIC_COOKIE_NAME =
            "MIT-MAGIC-COOKIE-1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    final String display;
    final Path authority;
    final Path logFile;
    private final Process process;

    private StandaloneX11Server(String display, Path authority, Path logFile,
            Process process) {
        this.display = display;
        this.authority = authority;
        this.logFile = logFile;
        this.process = process;
    }

    static StandaloneX11Server start(Context context, Path runtime) throws IOException {
        Path executable = Path.of(context.getApplicationInfo().nativeLibraryDir,
                "librb4dx_x11_server.so");
        Path xkb = runtime.resolve("rootfs/usr/share/X11/xkb");
        if (!Files.isRegularFile(executable) || !Files.isReadable(executable)) {
            throw new IOException("Bundled independent Android X11 executable is missing");
        }
        if (!Files.isDirectory(xkb)) {
            throw new IOException("Runtime XKB configuration is missing: " + xkb);
        }

        Path privateDir = context.getFilesDir().toPath().resolve("x11-session");
        Path tmp = privateDir.resolve("tmp");
        Files.createDirectories(tmp);
        Files.createDirectories(privateDir.resolve("runtime"));

        // Xorg requires a real authentication file. Never use -ac. Randomize
        // the display number and cookie, and keep the file app-private.
        int number = 90 + RANDOM.nextInt(10);
        String display = ":" + number;
        Path authority = Files.createTempFile(privateDir, "authority-", ".xauth");
        try {
            writeAuthority(authority, number);
            Os.chmod(authority.toString(), 0600);
            Path log = Files.createTempFile(privateDir, "server-", ".log");
            ProcessBuilder builder = new ProcessBuilder(executable.toString(), display,
                    "-auth", authority.toString(), "-nolisten", "tcp", "-noreset");
            builder.directory(privateDir.toFile());
            builder.environment().put("TMPDIR", tmp.toString());
            builder.environment().put("XKB_CONFIG_ROOT", xkb.toString());
            builder.environment().put("XDG_RUNTIME_DIR", privateDir.resolve("runtime").toString());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log.toFile());
            Process process = builder.start();
            StandaloneX11Server session = new StandaloneX11Server(display, authority, log, process);
            if (!session.awaitReady(STARTUP_TIMEOUT_MS)) {
                String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
                session.close();
                throw new IOException("Embedded X11 did not become ready: "
                        + output.substring(0, Math.min(output.length(), 3000)));
            }
            return session;
        } catch (Throwable failure) {
            Files.deleteIfExists(authority);
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Embedded X11 startup: " + failure, failure);
        }
    }

    private static void writeAuthority(Path authority, int displayNumber) throws IOException {
        byte[] number = Integer.toString(displayNumber).getBytes(StandardCharsets.US_ASCII);
        byte[] cookie = new byte[16];
        RANDOM.nextBytes(cookie);
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(authority))) {
            out.writeShort(0xffff); // FamilyWild permits Unix and abstract sockets.
            out.writeShort(0);      // Any local address; security is the cookie.
            out.writeShort(number.length);
            out.write(number);
            out.writeShort(MAGIC_COOKIE_NAME.length);
            out.write(MAGIC_COOKIE_NAME);
            out.writeShort(cookie.length);
            out.write(cookie);
        }
    }

    private boolean awaitReady(long timeoutMs) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        String expected = "RB4DX_X11_READY display=" + display;
        while (System.nanoTime() < deadline) {
            String log = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
            if (log.contains(expected)) return process.isAlive();
            if (!process.isAlive()) return false;
            try {
                Thread.sleep(75);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while starting embedded X11", interrupted);
            }
        }
        return false;
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        try {
            Files.deleteIfExists(authority);
        } catch (IOException ignored) {
            // Never delete another application's files or a shared game folder.
        }
    }
}
