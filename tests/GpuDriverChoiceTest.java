package dev.rb4dx.standalone;

import java.nio.file.Path;

public final class GpuDriverChoiceTest {
    public static void main(String[] args) {
        Path nativeDir = Path.of("/data/app/rb4dx/lib/arm64");
        require(GpuDriverChoice.libraryPath(nativeDir, "system") == null,
                "system driver must use Android Vulkan loader");
        require(GpuDriverChoice.libraryPath(nativeDir, "turnip27").equals(
                        nativeDir.resolve("libvulkan_freedreno.so")),
                "existing bundled Turnip path must be preserved");
        require(GpuDriverChoice.libraryPath(nativeDir, "bachata_compat").equals(
                        nativeDir.resolve("libvulkan_freedreno_compat.so")),
                "Bachata-compatible Turnip must be independently selectable");
        require(GpuDriverChoice.libraryPath(nativeDir, "garbage").equals(
                        nativeDir.resolve("libvulkan_freedreno.so")),
                "unknown preference must safely preserve original default");
        System.out.println("PASS: Vulkan choice isolates system, existing and Bachata Turnip paths");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
