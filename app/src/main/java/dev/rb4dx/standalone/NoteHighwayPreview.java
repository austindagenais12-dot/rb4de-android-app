// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import java.util.ArrayList;
import java.util.List;

/** Deterministic visual TEST chart. Never sourced from a Rock Band game or song file. */
final class NoteHighwayPreview {
    static final long CYCLE_MS = 6400;
    private static final long TRAVEL_MS = 1800;
    private static final int[] LANES = {0, 2, 1, 3, 4, 0, 1, 2, 3, 4, 1, 3, 0};

    static final class Note {
        final int lane;
        final float progress;
        final boolean sustain;

        Note(int lane, float progress, boolean sustain) {
            this.lane = lane;
            this.progress = progress;
            this.sustain = sustain;
        }
    }

    private NoteHighwayPreview() {}

    static float trackX(int lane, float progress, float width) {
        float slot = (lane + .5f) / 5f;
        float nearTop = width * (.13f + slot * .74f);
        float nearHitLine = width * (.04f + slot * .92f);
        float t = Math.max(0, Math.min(1, progress));
        return nearTop + (nearHitLine - nearTop) * t;
    }

    static List<Note> notesAt(long elapsedMs) {
        long phase = Math.floorMod(elapsedMs, CYCLE_MS);
        List<Note> visible = new ArrayList<>();
        for (int i = 0; i < LANES.length; i++) {
            long scheduledHit = 1400 + i * 400L;
            long untilHit = Math.floorMod(scheduledHit - phase, CYCLE_MS);
            if (untilHit <= TRAVEL_MS) {
                visible.add(new Note(LANES[i],
                        (TRAVEL_MS - untilHit) / (float) TRAVEL_MS,
                        i % 6 == 4));
            }
        }
        visible.sort((a, b) -> Float.compare(b.progress, a.progress));
        return visible;
    }
}
