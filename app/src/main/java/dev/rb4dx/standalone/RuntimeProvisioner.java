// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Safely installs the already-built Linux ARM64 runtime into app-owned storage.
 *
 * This does NOT launch Linux/glibc code on Android. An Android execution,
 * display and input bridge remains necessary before enabling Play.
 */
final class RuntimeProvisioner {
    private static final Object INSTALL_LOCK = new Object();
    private static final long MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_RUNTIME_FILES = 20000;
    private static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;
    private static final String MARKER = ".rb4dx-complete";

    interface Progress {
        void update(long bytes, long totalBytes);
    }

    static final class Result {
        final String version;
        final int files;
        final long bytes;
        final boolean reused;
        final Path directory;

        Result(String version, int files, long bytes, boolean reused, Path directory) {
            this.version = version;
            this.files = files;
            this.bytes = bytes;
            this.reused = reused;
            this.directory = directory;
        }
    }

    private static final class FileSpec {
        final long size;
        final String sha256;

        FileSpec(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }

    private RuntimeProvisioner() {}

    static Result prepare(AssetManager assets, Path filesDir, Progress progress) throws Exception {
        synchronized (INSTALL_LOCK) {
            return prepareLocked(assets, filesDir, progress);
        }
    }

    private static Result prepareLocked(AssetManager assets, Path filesDir, Progress progress)
            throws Exception {
        final JSONObject manifest;
        try (InputStream input = assets.open("runtime/manifest.json", AssetManager.ACCESS_STREAMING)) {
            manifest = new JSONObject(new String(readLimited(input), StandardCharsets.UTF_8));
        }

        if (manifest.getInt("schemaVersion") != 2 || manifest.getInt("protocolVersion") != 1) {
            throw new IOException("Unsupported native runtime manifest");
        }
        String version = manifest.getString("runtimeVersion");
        if (!version.matches("runtime-[0-9a-f]{12}")) {
            throw new IOException("Invalid runtime version");
        }

        JSONArray entries = manifest.getJSONArray("files");
        if (entries.length() == 0 || entries.length() > MAX_RUNTIME_FILES) {
            throw new IOException("Invalid runtime manifest file count");
        }
        Map<String, FileSpec> expected = new HashMap<>();
        long total = 0;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String name = entry.getString("path");
            String hash = entry.getString("sha256");
            long size = entry.getLong("size");
            if (!isSafeRelativeName(name) || name.equals(MARKER)
                    || !hash.matches("[0-9a-f]{64}") || size < 0
                    || size > MAX_UNCOMPRESSED_BYTES - total
                    || expected.putIfAbsent(name, new FileSpec(size, hash)) != null) {
                throw new IOException("Invalid runtime manifest entry: " + name);
            }
            total += size;
        }
        for (String required : new String[] {
                "host/shadps4-arm64-fex",
                "host/ld-linux-aarch64.so.1",
                "host/fexcore-smoke",
                "host/lib/libvulkan_vortek.so"
        }) {
            if (!expected.containsKey(required)) {
                throw new IOException("Required emulator component missing: " + required);
            }
        }

        // Only our own dedicated directory is ever created or cleaned.
        Path base = filesDir.resolve("native-runtime").toAbsolutePath().normalize();
        Path installed = base.resolve(version);
        Path marker = installed.resolve(MARKER);
        if (Files.isRegularFile(marker)
                && version.equals(new String(
                        Files.readAllBytes(marker), StandardCharsets.UTF_8).trim())
                && Files.isRegularFile(installed.resolve("host/shadps4-arm64-fex"))
                && Files.isRegularFile(installed.resolve("host/lib/libvulkan_vortek.so"))) {
            return new Result(version, entries.length(), total, true, installed);
        }

        Files.createDirectories(base);
        Path staging = base.resolve(version + ".staging");
        deleteOwnedDirectory(staging, base);
        Files.createDirectories(staging);

        long extracted = 0;
        Set<String> seen = new HashSet<>();
        try {
            try (InputStream input = assets.open("runtime/runtime.zip", AssetManager.ACCESS_STREAMING);
                 ZipInputStream archive = new ZipInputStream(input)) {
                byte[] buffer = new byte[64 * 1024];
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !isSafeRelativeName(name) || !seen.add(name)) {
                        throw new IOException("Invalid or repeated runtime ZIP entry: " + name);
                    }
                    FileSpec spec = expected.get(name);
                    if (spec == null) {
                        throw new IOException("Unexpected runtime ZIP entry: " + name);
                    }
                    Path output = staging.resolve(name).normalize();
                    if (!output.startsWith(staging)) {
                        throw new IOException("Runtime archive path escapes staging directory");
                    }
                    Files.createDirectories(output.getParent());
                    MessageDigest digest = sha256();
                    long written = 0;
                    try (OutputStream file = Files.newOutputStream(output)) {
                        int count;
                        while ((count = archive.read(buffer)) != -1) {
                            if (count > spec.size - written) {
                                throw new IOException("Oversized runtime entry: " + name);
                            }
                            file.write(buffer, 0, count);
                            digest.update(buffer, 0, count);
                            written += count;
                            extracted += count;
                            if (progress != null) progress.update(extracted, total);
                        }
                    }
                    if (written != spec.size || !hex(digest.digest()).equals(spec.sha256)) {
                        throw new IOException("Runtime integrity check failed: " + name);
                    }
                    archive.closeEntry();
                }
            }
            if (seen.size() != expected.size() || extracted != total) {
                throw new IOException("Incomplete native runtime ZIP");
            }
            Files.write(staging.resolve(MARKER),
                    (version + "\n").getBytes(StandardCharsets.UTF_8));
            // An interrupted previous install may have left an incomplete
            // directory. It is app-generated, so it is safe to replace.
            deleteOwnedDirectory(installed, base);
            try {
                Files.move(staging, installed, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staging, installed);
            }
            return new Result(version, expected.size(), total, false, installed);
        } catch (Exception error) {
            deleteOwnedDirectory(staging, base);
            throw error;
        }
    }

    private static byte[] readLimited(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > MAX_MANIFEST_BYTES) {
                throw new IOException("Runtime manifest is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static boolean isSafeRelativeName(String name) {
        if (name == null || name.isEmpty() || name.startsWith("/")
                || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) return false;
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] >>> 4) & 0xF];
            result[i * 2 + 1] = digits[bytes[i] & 0xF];
        }
        return new String(result);
    }

    private static void deleteOwnedDirectory(Path target, Path base) throws IOException {
        if (!target.normalize().startsWith(base.normalize()) || target.equals(base)) {
            throw new IOException("Refusing to delete outside managed runtime staging");
        }
        if (!Files.exists(target)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error)
                    throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
