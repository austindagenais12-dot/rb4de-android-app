// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports the unmodified RB4DX PS4 archive into *this app's own* emulator user
 * directory. This class has no Android dependencies and can be unit-tested with
 * a normal JDK. It never touches another app's sandbox.
 */
public final class DeluxeInstaller {
    public static final String US_TITLE = "CUSA02084";
    public static final String EU_TITLE = "CUSA02901";
    private static final String GOLDHEN_PREFIX = "GoldHEN/";
    private static final String MOD_ASSET_PREFIX = "GoldHEN/RB4DX/";
    private static final String PLUGIN = "plugins/RB4DX-Plugin.prx";
    private static final long MAX_UNPACKED_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 5_000;

    public interface Progress {
        void onProgress(int entries, long copiedBytes);
    }

    public static final class Result {
        public final int files;
        public final long bytes;
        public final Path assetsDirectory;
        public final Path pluginPath;

        private Result(int files, long bytes, Path assetsDirectory, Path pluginPath) {
            this.files = files;
            this.bytes = bytes;
            this.assetsDirectory = assetsDirectory;
            this.pluginPath = pluginPath;
        }
    }

    private DeluxeInstaller() {}

    public static Result install(InputStream zipStream, Path appFiles, String titleId,
                                 Progress progress) throws IOException {
        if (!US_TITLE.equals(titleId) && !EU_TITLE.equals(titleId)) {
            throw new IllegalArgumentException("Unsupported Rock Band 4 title ID: " + titleId);
        }
        Path userRoot = appFiles.resolve("shadps4").resolve("user");
        Files.createDirectories(userRoot);
        Path stage = Files.createTempDirectory(userRoot, "rb4dx-import-");
        Path stageGoldhen = stage.resolve("GoldHEN");
        Files.createDirectories(stageGoldhen);

        int files = 0;
        int entries = 0;
        int assetFiles = 0;
        long copiedBytes = 0;
        Set<String> seenNames = new HashSet<>();

        try {
            try (ZipInputStream zip = new ZipInputStream(zipStream)) {
                ZipEntry entry;
                byte[] buffer = new byte[64 * 1024];
                while ((entry = zip.getNextEntry()) != null) {
                    entries++;
                    if (entries > MAX_ZIP_ENTRIES) {
                        throw new IOException("Too many files in RB4DX archive");
                    }

                    String name = entry.getName();
                    if (name.isEmpty() || name.startsWith("/") || name.contains("\\")
                            || name.indexOf('\u0000') >= 0) {
                        throw new IOException("Unsafe archive filename: " + name);
                    }
                    Path entryPath = Path.of(name).normalize();
                    if (entryPath.isAbsolute() || entryPath.startsWith("..")
                            || !entryPath.toString().equals(name.replaceAll("/+$", ""))) {
                        throw new IOException("Unsafe archive path: " + name);
                    }
                    if (!seenNames.add(name)) {
                        throw new IOException("Duplicate ZIP entry: " + name);
                    }

                    // RB4DX also has a legacy hen/plugins.ini file. The
                    // custom module loader needs only GoldHEN content.
                    if (!name.startsWith(GOLDHEN_PREFIX)) {
                        zip.closeEntry();
                        continue;
                    }
                    String relative = name.substring(GOLDHEN_PREFIX.length());
                    if (relative.isEmpty()) {
                        zip.closeEntry();
                        continue;
                    }
                    Path dest = stageGoldhen.resolve(relative).normalize();
                    if (!dest.startsWith(stageGoldhen)) {
                        throw new IOException("Path escaped the staging directory");
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                        zip.closeEntry();
                        continue;
                    }

                    Files.createDirectories(dest.getParent());
                    try (OutputStream out = Files.newOutputStream(dest,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        int len;
                        while ((len = zip.read(buffer)) != -1) {
                            copiedBytes += len;
                            if (copiedBytes > MAX_UNPACKED_BYTES) {
                                throw new IOException("RB4DX archive expands beyond 4 GiB limit");
                            }
                            out.write(buffer, 0, len);
                        }
                    }
                    files++;
                    if (name.startsWith(MOD_ASSET_PREFIX)) {
                        assetFiles++;
                    }
                    zip.closeEntry();
                    if (progress != null && files % 20 == 0) {
                        progress.onProgress(files, copiedBytes);
                    }
                }
            }

            if (assetFiles == 0 || !Files.isRegularFile(stageGoldhen.resolve(PLUGIN))
                    || Files.size(stageGoldhen.resolve(PLUGIN)) < 1024) {
                throw new IOException("Archive lacks RB4DX assets or its PS4 plugin");
            }

            Path gameData = userRoot.resolve("data").resolve(titleId);
            Path goldhen = gameData.resolve("GoldHEN");
            Path modules = userRoot.resolve("custom_modules").resolve(titleId);
            Path module = modules.resolve("RB4DX-Plugin.prx");
            Files.createDirectories(gameData);
            Files.createDirectories(modules);

            // Preserve existing installs on re-import. Stage and live dirs
            // share the app's private filesystem for inexpensive renames.
            String suffix = "-before-" + System.currentTimeMillis();
            Path backupGoldhen = gameData.resolve("GoldHEN" + suffix);
            Path backupModule = modules.resolve("RB4DX-Plugin.prx" + suffix);
            boolean goldMoved = false;
            boolean moduleMoved = false;
            boolean goldInstalled = false;
            try {
                if (Files.exists(goldhen)) {
                    Files.move(goldhen, backupGoldhen);
                    goldMoved = true;
                }
                Files.move(stageGoldhen, goldhen);
                goldInstalled = true;
                if (Files.exists(module)) {
                    Files.move(module, backupModule);
                    moduleMoved = true;
                }
                Files.copy(goldhen.resolve(PLUGIN), module,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                if (Files.exists(module) && !moduleMoved) {
                    Files.deleteIfExists(module);
                } else if (Files.exists(module)) {
                    Files.deleteIfExists(module);
                }
                if (moduleMoved) {
                    Files.move(backupModule, module);
                }
                if (goldInstalled) {
                    deleteTree(goldhen);
                }
                if (goldMoved) {
                    Files.move(backupGoldhen, goldhen);
                }
                throw failure;
            }

            if (progress != null) {
                progress.onProgress(files, copiedBytes);
            }
            return new Result(files, copiedBytes, goldhen, module);
        } finally {
            deleteTree(stage);
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
