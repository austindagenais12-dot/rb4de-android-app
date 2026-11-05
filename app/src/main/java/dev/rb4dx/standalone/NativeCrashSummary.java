// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts a captured native crash into searchable, ASLR-independent fields. */
final class NativeCrashSummary {
    private static final Pattern SIGNAL = Pattern.compile("\\[Bachata\\.Crash\\].*\\bsignal=(\\d+)");
    private static final Pattern PC = Pattern.compile("\\bpc=0x([0-9a-fA-F]+)");
    private static final Pattern LR = Pattern.compile("\\blr=0x([0-9a-fA-F]+)");
    private static final Pattern MODULE = Pattern.compile(
            "^([0-9a-fA-F]+)-([0-9a-fA-F]+)\\s+r-xp\\s+([0-9a-fA-F]+)\\s+.*[/]shadps4-arm64-fex\\s*$");

    private NativeCrashSummary() {}

    static String fromText(String crash) {
        Integer lastSignal = null;
        Long lastPc = null;
        Long lastLr = null;
        Long mappedStart = null;
        Long mappedEnd = null;
        Long mappedFileOffset = null;

        for (String line : crash.split("\\R")) {
            Matcher signal = SIGNAL.matcher(line);
            if (signal.find()) {
                try {
                    lastSignal = Integer.parseInt(signal.group(1));
                } catch (NumberFormatException invalid) {
                    lastSignal = null;
                }
                lastPc = null;
                lastLr = null;
                continue;
            }
            if (line.startsWith("[Bachata.Crash]") && line.contains("pc=")) {
                Matcher pc = PC.matcher(line);
                Matcher lr = LR.matcher(line);
                lastPc = pc.find() ? parseHex(pc.group(1)) : null;
                lastLr = lr.find() ? parseHex(lr.group(1)) : null;
            }
            Matcher module = MODULE.matcher(line.trim());
            if (module.matches()) {
                mappedStart = parseHex(module.group(1));
                mappedEnd = parseHex(module.group(2));
                mappedFileOffset = parseHex(module.group(3));
            }
        }

        if (lastSignal == null) return "crash_detected=false\n";
        boolean nullExecution = lastSignal == 11 && lastPc != null && lastPc == 0;
        StringBuilder result = new StringBuilder("crash_detected=true\n")
                .append("crash_last_signal=").append(lastSignal).append('\n')
                .append("crash_null_execution=").append(nullExecution).append('\n');

        if (nullExecution && lastLr != null && mappedStart != null && mappedEnd != null
                && mappedFileOffset != null && Long.compareUnsigned(lastLr, mappedStart) >= 0
                && Long.compareUnsigned(lastLr, mappedEnd) < 0) {
            long relative = (lastLr - mappedStart) + mappedFileOffset;
            result.append("crash_guest_caller_offset=0x")
                    .append(Long.toHexString(relative)).append('\n');
        }
        return result.toString();
    }

    private static Long parseHex(String value) {
        try {
            return Long.parseUnsignedLong(value, 16);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
