// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Retains bounded startup and shutdown evidence from the latest native test. */
final class StartupLogStore {
    static final int MAX_SAVED_BYTES = 8 * 1024 * 1024;
    static final String TRUNCATION_MARKER = "\n[RB4DX] Middle output omitted to bound the exported log\n";

    static final class Retention {
        final Path path;
        final long sourceBytes;
        final long savedBytes;
        final boolean truncated;
        final long omittedPadPollLines;
        final long filteredSourceBytes;

        Retention(Path path, long sourceBytes, long savedBytes, boolean truncated) {
            this(path, sourceBytes, savedBytes, truncated, 0, sourceBytes);
        }

        Retention(Path path, long sourceBytes, long savedBytes, boolean truncated,
                long omittedPadPollLines, long filteredSourceBytes) {
            this.path = path;
            this.sourceBytes = sourceBytes;
            this.savedBytes = savedBytes;
            this.truncated = truncated;
            this.omittedPadPollLines = omittedPadPollLines;
            this.filteredSourceBytes = filteredSourceBytes;
        }
    }

    private StartupLogStore() {}

    /**
     * Preserve important mid-run evidence by collapsing only four extremely
     * repetitive controller-polling calls. A 60-second RB4 session can log
     * tens of thousands of these lines, hiding real Vulkan stalls in a
     * head-and-tail export. All other lines stay in order, including uncommon
     * pad errors. Neither the source capture nor a previous export is edited.
     */
    static Retention retainReadableDiagnostics(File source, Path destination, int maxBytes)
            throws IOException {
        long rawBytes = Files.size(source.toPath());
        Path parent = destination.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Log destination has no parent directory");
        Files.createDirectories(parent);
        Path filtered = Files.createTempFile(parent, "rb4dx-filtered-", ".partial");
        try {
            Set<String> firstExamples = new HashSet<>();
            Map<String, Long> omittedByCall = new LinkedHashMap<>();
            try (BufferedReader reader = Files.newBufferedReader(source.toPath(), StandardCharsets.UTF_8);
                    BufferedWriter writer = Files.newBufferedWriter(filtered, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String key = repeatedPadCall(line);
                    if (key != null && !firstExamples.add(key)) {
                        omittedByCall.merge(key, 1L, Long::sum);
                        continue;
                    }
                    writer.write(line);
                    writer.newLine();
                }
                if (!omittedByCall.isEmpty()) {
                    long total = 0;
                    for (long count : omittedByCall.values()) total += count;
                    writer.write("[RB4DX] " + total
                            + " repeated controller polling lines omitted; first examples retained:");
                    for (Map.Entry<String, Long> entry : omittedByCall.entrySet()) {
                        writer.write(' ' + entry.getKey() + '=' + entry.getValue());
                    }
                    writer.newLine();
                }
            }
            long omitted = 0;
            for (long count : omittedByCall.values()) omitted += count;
            // If no polling was removed, keep the precise original bytes,
            // including any nonstandard newline or encoding sequences.
            if (omitted == 0) return retainHeadAndTail(source, destination, maxBytes);
            long filteredBytes = Files.size(filtered);
            Retention saved = retainHeadAndTail(filtered.toFile(), destination, maxBytes);
            return new Retention(saved.path, rawBytes, saved.savedBytes, saved.truncated,
                    omitted, filteredBytes);
        } finally {
            Files.deleteIfExists(filtered);
        }
    }

    private static String repeatedPadCall(String line) {
        if (!line.contains("(joypad_reader)") || !line.contains("pad.cpp:")) return null;
        if (line.contains("scePadOpen:")) {
            if (line.contains("type=0") || line.contains("type = 0")) return "scePadOpen[type=0]";
            if (line.contains("type=2") || line.contains("type = 2")) return "scePadOpen[type=2]";
            return "scePadOpen[other]";
        }
        if (line.contains("scePadClose: called")) return "scePadClose";
        if (line.contains("scePadResetOrientation: called")) return "scePadResetOrientation";
        if (line.contains("scePadDeviceClassGetExtendedInformation: (STUBBED) called")) {
            return "scePadDeviceClassGetExtendedInformation";
        }
        return null;
    }

    static Retention retainHeadAndTail(File source, Path destination, int maxBytes) throws IOException {
        byte[] marker = TRUNCATION_MARKER.getBytes(StandardCharsets.UTF_8);
        if (maxBytes <= marker.length) {
            throw new IllegalArgumentException("Maximum log size must exceed truncation marker");
        }
        long sourceBytes = Files.size(source.toPath());
        boolean truncated = sourceBytes > maxBytes;
        long payloadBytes = truncated ? maxBytes - marker.length : sourceBytes;
        // The initial game/driver initialization is only visible at the start;
        // later faults and GPU stalls are visible at the end. A tail-only file
        // loses the former after high-frequency pad polling fills the log.
        long headBytes = truncated ? payloadBytes / 4 : sourceBytes;
        long tailBytes = truncated ? payloadBytes - headBytes : 0;

        Path parent = destination.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Log destination has no parent directory");
        Files.createDirectories(parent);
        Path pending = Files.createTempFile(parent, "rb4dx-log-", ".partial");
        try {
            try (RandomAccessFile reader = new RandomAccessFile(source, "r");
                    OutputStream output = Files.newOutputStream(pending)) {
                copyRange(reader, output, 0, headBytes);
                if (truncated) {
                    output.write(marker);
                    copyRange(reader, output, sourceBytes - tailBytes, tailBytes);
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

    private static void copyRange(RandomAccessFile reader, OutputStream output,
            long start, long length) throws IOException {
        reader.seek(start);
        byte[] buffer = new byte[64 * 1024];
        long remaining = length;
        while (remaining > 0) {
            int count = reader.read(buffer, 0, (int) Math.min(remaining, buffer.length));
            if (count < 0) throw new EOFException("Startup log changed during capture");
            output.write(buffer, 0, count);
            remaining -= count;
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
