// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;

/** Drains high-volume native logs without growing storage through long Play sessions. */
final class ProcessLogDrain {
    private static final int HEAD_BYTES = 2 * 1024 * 1024;
    private static final int TAIL_BYTES = 2 * 1024 * 1024;

    private ProcessLogDrain() {}

    static long capture(InputStream input, Path destination) throws IOException {
        byte[] io = new byte[65536];
        byte[] tail = new byte[TAIL_BYTES];
        long total = 0;
        int position = 0, stored = 0;
        try (OutputStream output = Files.newOutputStream(destination,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int count;
            while ((count = input.read(io)) >= 0) {
                if (count == 0) continue;
                int head = (int)Math.min(count, Math.max(0L, HEAD_BYTES - total));
                if (head > 0) output.write(io, 0, head);
                for (int i = head; i < count; i++) {
                    tail[position] = io[i];
                    position = (position + 1) % TAIL_BYTES;
                    if (stored < TAIL_BYTES) stored++;
                }
                total += count;
            }
            if (stored > 0) {
                output.write("\n[RB4DX_PLAY_LOG_TAIL_AFTER_BOUNDED_HEAD]\n"
                        .getBytes(StandardCharsets.UTF_8));
                int oldest = (position - stored + TAIL_BYTES) % TAIL_BYTES;
                int first = Math.min(stored, TAIL_BYTES - oldest);
                output.write(tail, oldest, first);
                if (first < stored) output.write(tail, 0, stored - first);
            }
        }
        return total;
    }
}
