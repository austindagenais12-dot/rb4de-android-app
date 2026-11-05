// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

public final class NativeCrashSummaryTest {
    public static void main(String[] args) {
        String crash = """
                [Bachata.Crash] tid=6707 signal=7 code=1
                [Bachata.Crash] pc=0x6d0f1ba410 sp=0x7463f241c0 lr=0x6d0f1b9a94
                [Bachata.Crash] tid=6682 signal=11 code=1
                [Bachata.Crash] pc=0x0 sp=0x76f1568d00 fp=0x76f1568d00 lr=0x76f4e2bc04
                [Bachata.Crash] maps begin
                76f21c0000-76f220f000 r-xp 00000000 fe:41 570205 /files/host/lib/libvulkan_vortek.so
                76f3c30000-76f7c80000 r-xp 00000000 fe:41 535644 /files/host/shadps4-arm64-fex
                [Bachata.Crash] maps end
                Unhandled access violation at code address 0x0: Read from address 0x0
                """;
        String summary = NativeCrashSummary.fromText(crash);
        check(summary.contains("crash_detected=true\n"), "fatal report detected");
        check(summary.contains("crash_last_signal=11\n"), "last fatal signal selected");
        check(summary.contains("crash_null_execution=true\n"), "null program counter detected");
        check(summary.contains("crash_guest_caller_offset=0x11fbc04\n"),
                "LR translated relative to actual ARM64 emulator map");

        String noCrash = NativeCrashSummary.fromText("[Debug] Render initialized\n");
        check(noCrash.equals("crash_detected=false\n"), "uncrashed run not mislabeled");

        String malformed = NativeCrashSummary.fromText("""
                [Bachata.Crash] tid=5 signal=11 code=1
                [Bachata.Crash] pc=incorrect lr=0x999
                invalid mapping line
                """);
        check(malformed.contains("crash_null_execution=false\n"),
                "malformed register dump must not claim a null jump");
        check(!malformed.contains("crash_guest_caller_offset="),
                "missing map must not invent an offset");

        String outside = NativeCrashSummary.fromText("""
                [Bachata.Crash] tid=5 signal=11 code=1
                [Bachata.Crash] pc=0x0 lr=0x76f8e2bc04
                76f3c30000-76f7c80000 r-xp 00000000 fe:41 535644 /files/host/shadps4-arm64-fex
                """);
        check(!outside.contains("crash_guest_caller_offset="),
                "return address outside emulator mapping is not symbolizable");
        System.out.println("PASS: fatal crash signal and ARM64 relative-caller diagnostics");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
