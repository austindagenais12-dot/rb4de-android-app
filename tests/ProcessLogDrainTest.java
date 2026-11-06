package dev.rb4dx.standalone;

import java.io.ByteArrayInputStream;
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
            System.out.println("PASS: unbounded Play sessions capture bounded logs with head and tail");
        } finally { Files.deleteIfExists(temp); }
    }
    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
