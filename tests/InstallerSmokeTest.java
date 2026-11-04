// SPDX-License-Identifier: GPL-2.0-or-later
import dev.rb4dx.standalone.DeluxeInstaller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class InstallerSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("rb4dx-test-");
        try {
            byte[] payload = makeZip(false, (byte) 42);
            DeluxeInstaller.Result result = DeluxeInstaller.install(
                    new ByteArrayInputStream(payload), dir, DeluxeInstaller.US_TITLE, null);
            assertTrue(result.files == 2, "Two GoldHEN files imported");
            assertTrue(result.bytes == 2052, "Unknown legacy hen file was ignored");
            assertTrue(Files.size(result.pluginPath) == 2048, "Plugin installed");
            assertTrue(Arrays.equals(Files.readAllBytes(result.assetsDirectory.resolve(
                    "RB4DX/ps4/ui/game/example.bin")), new byte[] {1, 2, 3, 4}),
                    "Raw asset copied");

            byte[] replacement = makeZip(false, (byte) 43);
            DeluxeInstaller.install(new ByteArrayInputStream(replacement), dir,
                    DeluxeInstaller.US_TITLE, null);
            assertTrue(Files.readAllBytes(result.pluginPath)[0] == 43,
                    "Re-import replaces plugin");
            assertTrue(Files.list(result.assetsDirectory.getParent()).anyMatch(
                    p -> p.getFileName().toString().startsWith("GoldHEN-before-")),
                    "Re-import preserves previous GoldHEN as a backup");

            boolean rejected = false;
            try {
                DeluxeInstaller.install(new ByteArrayInputStream(makeZip(true, (byte) 44)),
                        dir, DeluxeInstaller.EU_TITLE, null);
            } catch (IOException expected) {
                rejected = true;
            }
            assertTrue(rejected, "Unsafe ZIP path rejected");
            assertTrue(!Files.exists(dir.resolve("evil.txt")), "No path escape");
            assertTrue(!Files.exists(dir.resolve("shadps4/user/data/"
                    + DeluxeInstaller.EU_TITLE)), "Rejected ZIP did not commit assets");

            System.out.println("PASS: import, plugin placement, backup, traversal rejection");
        } finally {
            // This test's only deletion target is the unique temp tree it made.
            try (var paths = Files.walk(dir)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.delete(p); } catch (IOException error) {
                        throw new RuntimeException(error);
                    }
                });
            }
        }
    }

    private static byte[] makeZip(boolean unsafe, byte pluginMarker) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "GoldHEN/RB4DX/ps4/ui/game/example.bin", new byte[] {1, 2, 3, 4});
            byte[] plugin = new byte[2048];
            Arrays.fill(plugin, pluginMarker);
            put(zip, "GoldHEN/plugins/RB4DX-Plugin.prx", plugin);
            put(zip, "hen/plugins.ini", new byte[] {99});
            if (unsafe) put(zip, "GoldHEN/../../evil.txt", new byte[] {66});
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static void assertTrue(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
