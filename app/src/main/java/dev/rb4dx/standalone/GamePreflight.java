// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Non-mutating Android document-tree inspection; does not extract or copy the game. */
final class GamePreflight {
    private static final int MAX_SFO_BYTES = 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;

    static final class Result {
        final boolean succeeded;
        final String report;

        Result(boolean succeeded, String report) {
            this.succeeded = succeeded;
            this.report = report;
        }
    }

    private static final class Document {
        final String id;
        final String mime;
        final long size;

        Document(String id, String mime, long size) {
            this.id = id;
            this.mime = mime;
            this.size = size;
        }
    }

    private GamePreflight() {}

    static Result inspect(Context context, Uri tree, String expectedTitle) {
        StringBuilder result = new StringBuilder("RB4DX_GAME_PREFLIGHT_V1\n")
                .append("mode=READ_ONLY_SAF\n")
                .append("expected_title_id=").append(expectedTitle).append('\n');
        try {
            ContentResolver resolver = context.getContentResolver();
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            Document eboot = findChild(resolver, tree, rootId, "eboot.bin");
            if (eboot == null || DocumentsContract.Document.MIME_TYPE_DIR.equals(eboot.mime)) {
                return new Result(false, "RB4DX_GAME_PREFLIGHT_FAILED\n" + result
                        + "missing=eboot.bin\n");
            }
            Uri ebootUri = DocumentsContract.buildDocumentUriUsingTree(tree, eboot.id);
            byte[] header = new byte[32];
            int received;
            try (InputStream stream = resolver.openInputStream(ebootUri)) {
                if (stream == null) throw new IOException("Cannot open eboot.bin");
                received = stream.readNBytes(header, 0, header.length);
            }
            if (received < 4) throw new IOException("eboot.bin is shorter than four bytes");
            result.append("eboot_readable=true\n")
                    .append("eboot_size=").append(eboot.size).append('\n')
                    .append("eboot_header=").append(hex(header, Math.min(16, received))).append('\n');

            // SAF access does not imply raw Linux pathname access. Probe both
            // separately so future native mounting work has an accurate input.
            String directPath = rawPrimaryPath(tree);
            boolean rawReadable = directPath != null
                    && Files.isRegularFile(Path.of(directPath, "eboot.bin"))
                    && Files.isReadable(Path.of(directPath, "eboot.bin"));
            result.append("all_files_access_granted=")
                    .append(Environment.isExternalStorageManager()).append('\n');
            result.append("raw_linux_path_readable=").append(rawReadable).append('\n');

            Document system = findChild(resolver, tree, rootId, "sce_sys");
            if (system == null || !DocumentsContract.Document.MIME_TYPE_DIR.equals(system.mime)) {
                return new Result(false, "RB4DX_GAME_PREFLIGHT_PARTIAL\n" + result
                        + "missing=sce_sys/param.sfo\n");
            }
            Document sfo = findChild(resolver, tree, system.id, "param.sfo");
            if (sfo == null) {
                return new Result(false, "RB4DX_GAME_PREFLIGHT_PARTIAL\n" + result
                        + "missing=sce_sys/param.sfo\n");
            }
            Map<String, String> metadata;
            try (InputStream stream = resolver.openInputStream(
                    DocumentsContract.buildDocumentUriUsingTree(tree, sfo.id))) {
                if (stream == null) throw new IOException("Cannot open param.sfo");
                metadata = parseSfo(readBounded(stream, MAX_SFO_BYTES));
            }
            String title = metadata.getOrDefault("TITLE_ID", "<missing>");
            String version = metadata.getOrDefault("APP_VER", "<missing>");
            boolean match = expectedTitle.equals(title);
            result.append("param_sfo_readable=true\n")
                    .append("detected_title_id=").append(title).append('\n')
                    .append("app_ver=").append(version).append('\n')
                    .append("region_match=").append(match).append('\n')
                    .append("note=SAF access verified; native directory mounting still required unless raw path readable\n");
            return new Result(match,
                    (match ? "RB4DX_GAME_PREFLIGHT_PASS\n" : "RB4DX_GAME_PREFLIGHT_REGION_MISMATCH\n")
                            + result);
        } catch (Exception error) {
            result.append("error=").append(error.getClass().getSimpleName())
                    .append(": ").append(error.getMessage()).append('\n');
            return new Result(false, "RB4DX_GAME_PREFLIGHT_FAILED\n" + result);
        }
    }

    private static Document findChild(ContentResolver resolver, Uri tree, String parentId,
            String name) throws IOException {
        Uri childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor cursor = resolver.query(childUri, columns, null, null, null)) {
            if (cursor == null) throw new IOException("The document provider returned no directory listing");
            while (cursor.moveToNext()) {
                if (name.equalsIgnoreCase(cursor.getString(1))) {
                    long size = cursor.isNull(3) ? -1 : cursor.getLong(3);
                    return new Document(cursor.getString(0), cursor.getString(2), size);
                }
            }
        }
        return null;
    }

    static String rawPrimaryPath(Uri tree) {
        if (!"com.android.externalstorage.documents".equals(tree.getAuthority())) return null;
        String id = DocumentsContract.getTreeDocumentId(tree);
        if (id == null || !id.startsWith("primary:")) return null;
        String relative = id.substring("primary:".length());
        Path root = Environment.getExternalStorageDirectory().toPath().toAbsolutePath().normalize();
        Path candidate = root.resolve(relative).normalize();
        return candidate.startsWith(root) ? candidate.toString() : null;
    }

    private static byte[] readBounded(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (true) {
            int count = stream.read(buffer);
            if (count < 0) return output.toByteArray();
            if (output.size() + count > limit) throw new IOException("param.sfo exceeds 1 MiB safety limit");
            output.write(buffer, 0, count);
        }
    }

    private static Map<String, String> parseSfo(byte[] bytes) throws IOException {
        if (bytes.length < 20) throw new IOException("param.sfo header is too short");
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (data.getInt(0) != 0x46535000) throw new IOException("Invalid PSF magic");
        int keyStart = data.getInt(8);
        int dataStart = data.getInt(12);
        int count = data.getInt(16);
        if (keyStart < 20 || dataStart < 20 || count < 0 || count > MAX_ENTRIES
                || 20L + 16L * count > bytes.length
                || keyStart >= bytes.length || dataStart >= bytes.length) {
            throw new IOException("Malformed param.sfo header or entry table");
        }
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int offset = 20 + i * 16;
            int keyOffset = Short.toUnsignedInt(data.getShort(offset));
            int format = Short.toUnsignedInt(data.getShort(offset + 2));
            long length = Integer.toUnsignedLong(data.getInt(offset + 4));
            long payloadOffset = Integer.toUnsignedLong(data.getInt(offset + 12));
            long keyPos = (long)keyStart + keyOffset;
            long valuePos = (long)dataStart + payloadOffset;
            if (keyPos >= bytes.length || valuePos > bytes.length || length > bytes.length - valuePos) {
                throw new IOException("Malformed param.sfo entry offsets");
            }
            String key = nullTerminated(bytes, (int)keyPos, bytes.length - (int)keyPos);
            if (("TITLE_ID".equals(key) || "APP_VER".equals(key)) && format == 0x0204) {
                values.put(key, nullTerminated(bytes, (int)valuePos, (int)length));
            }
        }
        return values;
    }

    private static String nullTerminated(byte[] bytes, int position, int length) {
        int end = position;
        int stop = position + length;
        while (end < stop && bytes[end] != 0) end++;
        return new String(bytes, position, end - position, StandardCharsets.UTF_8);
    }

    private static String hex(byte[] bytes, int count) {
        StringBuilder result = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            result.append(Character.forDigit((bytes[i] >> 4) & 15, 16));
            result.append(Character.forDigit(bytes[i] & 15, 16));
        }
        return result.toString();
    }
}
