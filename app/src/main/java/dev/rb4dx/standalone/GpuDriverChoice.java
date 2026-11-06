// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.nio.file.Path;

/** Explicitly separates the Linux Vortek ICD from the Android/Bionic physical driver. */
final class GpuDriverChoice {
    static final String SYSTEM = "system";
    static final String TURNIP_27 = "turnip27";
    static final String BACHATA_COMPAT = "bachata_compat";

    private GpuDriverChoice() {}

    static String normalize(String choice) {
        if (SYSTEM.equals(choice) || BACHATA_COMPAT.equals(choice)) return choice;
        return TURNIP_27;
    }

    static Path libraryPath(Path nativeDir, String choice) {
        String normalized = normalize(choice);
        if (SYSTEM.equals(normalized)) return null;
        return nativeDir.resolve(BACHATA_COMPAT.equals(normalized)
                ? "libvulkan_freedreno_compat.so" : "libvulkan_freedreno.so");
    }
}
