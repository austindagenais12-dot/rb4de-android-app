package dev.rb4dx.standalone;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GpuWaitSummaryTest {
    public static void main(String[] args) throws Exception {
        Path log = Files.createTempFile("rb4dx-gpu-wait-test-", ".log");
        try {
            Files.writeString(log, "old boot information\n"
                    + "[Render.Vulkan] RB4DX_WAIT_BEGIN queue=gfx address=0xabc value=0x1 ref=0x0 function=3\n"
                    + "GPU coroutine active resumes=1000\n"
                    + "[Render.Vulkan] PM4 WAIT_REG_MEM stalled queue=gfx iterations=100000 value=0x1\n"
                    + "[Render.Vulkan] RB4DX_WAIT_BEGIN queue=gfx address=0xdef value=0x2\n");
            String summary = GpuWaitSummary.fromLog(log);
            check(summary.contains("gpu_wait_begin_captured=true"), "capture first wait");
            check(summary.contains("address=0xabc"), "original wait address retained");
            check(!summary.contains("address=0xdef"), "later wait not substituted");
            check(summary.contains("gpu_wait_stall_captured=true"), "capture first stall");
            check(!summary.contains("GPU coroutine active"), "skip unrelated log flood");

            Files.writeString(log, "unrelated boot message\n");
            summary = GpuWaitSummary.fromLog(log);
            check(summary.contains("gpu_wait_begin_captured=false"), "missing wait marked");
            check(summary.contains("gpu_wait_stall_captured=false"), "missing stall marked");
        } finally {
            Files.deleteIfExists(log);
        }
        System.out.println("PASS: first GPU wait and stall retained without coroutine log flood");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
