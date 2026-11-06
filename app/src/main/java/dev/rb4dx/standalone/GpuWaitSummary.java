// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Retains the first GPU wait evidence before subsequent log spam displaces it. */
final class GpuWaitSummary {
    private static final int MAX_LINE_CHARS = 512;

    private GpuWaitSummary() {}

    static String fromLog(Path log) throws IOException {
        String firstBegin = null;
        String firstStall = null;
        String firstResolved = null;
        String lastBackoff = null;
        String lastResume = null;
        try (BufferedReader reader = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (firstBegin == null && line.contains("RB4DX_WAIT_BEGIN")) {
                    firstBegin = limit(line);
                }
                if (firstStall == null && line.contains("PM4 WAIT_REG_MEM stalled")) {
                    firstStall = limit(line);
                }
                if (firstResolved == null && line.contains("RB4DX_WAIT_RESOLVED")) {
                    firstResolved = limit(line);
                }
                if (line.contains("RB4DX_WAIT_BACKOFF")) {
                    lastBackoff = limit(line);
                }
                if (line.contains("GPU coroutine active resumes=")) {
                    lastResume = limit(line);
                }
            }
        }
        return "gpu_wait_begin_captured=" + (firstBegin != null) + '\n'
                + (firstBegin == null ? "" : "GPU_WAIT_FIRST_BEGIN:\n" + firstBegin + '\n')
                + "gpu_wait_stall_captured=" + (firstStall != null) + '\n'
                + (firstStall == null ? "" : "GPU_WAIT_FIRST_STALL:\n" + firstStall + '\n')
                + "gpu_wait_resolved_captured=" + (firstResolved != null) + '\n'
                + (firstResolved == null ? "" : "GPU_WAIT_FIRST_RESOLVED:\n" + firstResolved + '\n')
                + "gpu_wait_backoff_captured=" + (lastBackoff != null) + '\n'
                + (lastBackoff == null ? "" : "GPU_WAIT_LAST_BACKOFF:\n" + lastBackoff + '\n')
                + "gpu_last_resume_captured=" + (lastResume != null) + '\n'
                + (lastResume == null ? "" : "GPU_WAIT_LAST_RESUME:\n" + lastResume + '\n');
    }

    private static String limit(String line) {
        return line.substring(0, Math.min(MAX_LINE_CHARS, line.length()));
    }
}
