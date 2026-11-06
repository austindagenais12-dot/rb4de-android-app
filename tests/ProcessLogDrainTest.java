package dev.rb4dx.standalone;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProcessLogDrainTest {
    public static void main(String[] args) throws Exception {
        byte[] generated = new byte[7 * 1024 * 1024];
        generated[0] = 'A';
        generated[generated.length - 1] = 'Z';
        Path temp = Files.createTempFile("rb4dx-log-drain-", ".txt");
        try {
            long count = ProcessLogDrain.capture(new ByteArrayInputStream(generated), temp);
            byte[] saved = Files.readAllBytes(temp);
            check(count == generated.length, "source bytes counted");
            check(saved.length < 5 * 1024 * 1024, "output bounded");
            check(saved[0] == 'A' && saved[saved.length - 1] == 'Z', "head and tail retained");
            try (FilterInputStream cutOffOnStop = new FilterInputStream(new ByteArrayInputStream(generated)) {
                @Override public int read(byte[] buffer) throws IOException {
                    int result = super.read(buffer);
                    if (result == -1) throw new IOException("Stream Closed");
                    return result;
                }
            }) {
                count = ProcessLogDrain.capture(cutOffOnStop, temp, () -> true);
            }
            saved = Files.readAllBytes(temp);
            check(count == generated.length, "bytes counted on intentional stop");
            check(saved.length < 5 * 1024 * 1024, "stop log remains bounded");
            check(saved[0] == 'A' && saved[saved.length - 1] == 'Z',
                    "stop closes pipe but still retains final log tail");

            boolean unexpectedlyClosed = false;
            try (FilterInputStream cutOffUnexpectedly = new FilterInputStream(new ByteArrayInputStream(generated)) {
                @Override public int read(byte[] buffer) throws IOException {
                    int result = super.read(buffer);
                    if (result == -1) throw new IOException("Unexpected read failure");
                    return result;
                }
            }) {
                ProcessLogDrain.capture(cutOffUnexpectedly, temp, () -> false);
            } catch (IOException expected) {
                unexpectedlyClosed = expected.getMessage().contains("Unexpected read failure");
            }
            check(unexpectedlyClosed, "unexpected I/O errors still propagate");
            System.out.println("PASS: unbounded Play sessions capture bounded logs with head and tail");
        } finally { Files.deleteIfExists(temp); }
    }
    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
