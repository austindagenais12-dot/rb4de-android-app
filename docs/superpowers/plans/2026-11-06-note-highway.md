# Guitar Touch Note Highway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a five-lane falling-note touch preview to v0.12.15 input-bridge APK while preserving ordinary controller input.

**Architecture:** `NoteHighwayPreview` is a pure-Java deterministic sample chart with looping note positions. `TouchPadLayout` maps the bottom hit zone to five existing PS4 buttons. `TouchControllerPanel` renders the preview into a scalable Android canvas and still forwards actual touch state to `Ps4InputState`; `MainActivity` sizes the guitar panel appropriately.

**Tech Stack:** Android Java 17 Canvas, pure JVM geometry tests, existing Android GitHub Actions build.

**Spec:** `docs/superpowers/specs/2026-11-06-note-highway-design.md`

## Global Constraints

- Sample visual only, explicitly not synchronized with the Rock Band executable.
- No changes to native emulator, Turnip driver pin, previous APKs, or saved content.
- Keep the standard gamepad mode and send the same validated controller wire protocol.

## Review Focus

1. Five hit zones remain distinct on narrow portrait displays.
2. Multiple finger holds remain composable, with button release on lift.
3. Sample note positions wrap smoothly and never spawn notes outside the track.
4. The sample note animation runs only when the view is shown, not in the background.
5. Guitar mode never presents a demo chart as actual RB4 gameplay output.

---

### Task 1: Hit-line mapping and sample note sequence

**Files:** Modify `TouchPadLayout.java`, `TouchPadLayoutTest.java`; create `NoteHighwayPreview.java` and `NoteHighwayPreviewTest.java`; modify `tests/run.sh`.

**Interfaces:** `TouchPadLayout.buttonAt(true,x,y,w,h)` returns a fret only within the bottom strike area; `NoteHighwayPreview.notesAt(timeMs)` returns lane, normalized travel progress and sustain metadata.

- [ ] Add failing tests for the five strike areas and sample timeline loop.
- [ ] Implement geometry and sample chart, run local JVM tests, commit.

### Task 2: Render and verify

**Files:** Modify `TouchControllerPanel.java`, `MainActivity.java`, touch UX copy and tests.

- [ ] Add failing source integration check for true guitar-mode animation and on-screen disclosure.
- [ ] Draw five-lane perspective track, moving notes, colored hitline buttons and a sample-only label. Resize panel responsively, preserve classic mode.
- [ ] Run full regression suite, commit, sync the authorized feature branch, build APK via GitHub Actions and verify the deliverable.
