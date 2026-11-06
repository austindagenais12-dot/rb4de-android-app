package dev.rb4dx.standalone;

import java.util.List;

public final class NoteHighwayPreviewTest {
    public static void main(String[] args) {
        List<NoteHighwayPreview.Note> atStart = NoteHighwayPreview.notesAt(0);
        check(!atStart.isEmpty(), "preview has descending sample notes");
        check(atStart.get(0).lane == 0, "first sample enters green lane");
        check(Math.abs(atStart.get(0).progress - (400f / 1800)) < .001f,
                "first sample appears above hit line");
        List<NoteHighwayPreview.Note> atHit = NoteHighwayPreview.notesAt(1400);
        check(atHit.stream().anyMatch(n -> n.lane == 0 && n.progress > .999f),
                "note reaches hit line at exact scheduled time");
        List<NoteHighwayPreview.Note> afterLoop = NoteHighwayPreview.notesAt(6400);
        check(atStart.size() == afterLoop.size(), "loop preserves sample count");
        check(Math.abs(atStart.get(0).progress - afterLoop.get(0).progress) < .0001f,
                "loop restarts at same note position");
        for (long time = 0; time < 6400; time += 37) {
            for (NoteHighwayPreview.Note note : NoteHighwayPreview.notesAt(time)) {
                check(note.lane >= 0 && note.lane < 5, "never draws outside five lanes");
                check(note.progress >= 0 && note.progress <= 1, "sample notes stay on visible highway");
            }
        }
        check(NoteHighwayPreview.trackX(0, 1, 400) < NoteHighwayPreview.trackX(0, 0, 400),
                "left lane widens toward bottom of perspective track");
        check(NoteHighwayPreview.trackX(4, 1, 400) > NoteHighwayPreview.trackX(4, 0, 400),
                "right lane widens toward bottom of perspective track");
        check(NoteHighwayPreview.trackX(2, 0, 400) == 200f,
                "middle lane remains centered near horizon");
        System.out.println("PASS: five-lane sample note highway timing and seamless looping");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
