// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Retains the bounded, exact output of the latest native startup test. */
final class StartupLogStore {
    static final int MAX_SAVED_BYTES = 8 * 1024 * 1024;
    static final String TRUNCATION_MARKER = "[RB4DX] Earlier output truncated to bound the exported log\n";

    static final class Retention {
        final Path path;
        final long sourceBytes;
        final long savedBytes;
        final boolean truncated;

        Retention(Path path, long sourceBytes, long savedBytes, boolean truncated) {
            this.path = path;
            this.sourceBytes = sourceBytes;
            this.savedBytes = savedBytes;
            this.truncated = truncated;
        }
    }

    private StartupLogStore() {}

    static Retention retainTail(File source, Path destination, int maxBytes) throws IOException {
        byte[] marker = TRUNCATION_MARKER.getBytes(StandardCharsets.UTF_8);
        if (maxBytes <= marker.length) {
            throw new IllegalArgumentException("Maximum log size must exceed truncation marker");
        }
        long sourceBytes = Files.size(source.toPath());
        boolean truncated = sourceBytes > maxBytes;
        long payloadBytes = truncated ? maxBytes - marker.length : sourceBytes;

        Path parent = destination.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Log destination has no parent directory");
        Files.createDirectories(parent);
        Path pending = Files.createTempFile(parent, "rb4dx-log-", ".partial");
        try {
            try (RandomAccessFile reader = new RandomAccessFile(source, "r");
                    OutputStream output = Files.newOutputStream(pending)) {
                if (truncated) output.write(marker);
                reader.seek(sourceBytes - payloadBytes);
                byte[] buffer = new byte[64 * 1024];
                long remaining = payloadBytes;
                while (remaining > 0) {
                    int count = reader.read(buffer, 0, (int) Math.min(remaining, buffer.length));
                    if (count < 0) throw new EOFException("Startup log changed during capture");
                    output.write(buffer, 0, count);
                    remaining -= count;
                }
            }
            try {
                Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Retention(destination, sourceBytes, Files.size(destination), truncated);
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    static long copyTo(Path retained, OutputStream destination) throws IOException {
        long copied = 0;
        try (InputStream input = Files.newInputStream(retained)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                destination.write(buffer, 0, read);
                copied += read;
            }
        }
        return copied;
    }
}
