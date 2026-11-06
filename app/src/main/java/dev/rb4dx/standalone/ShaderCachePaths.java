// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.nio.file.Path;

/** Stable, per-title Mesa disk-cache location, separate from shadPS4 pipeline serialization. */
final class ShaderCachePaths {
    private ShaderCachePaths() {}

    static Path forTitle(Path userDirectory, String titleId) {
        if (userDirectory == null || titleId == null || !titleId.matches("CUSA[0-9]{5}")) {
            return null;
        }
        return userDirectory.resolve("shader-cache").resolve("mesa")
                .resolve(titleId).toAbsolutePath().normalize();
    }
}
