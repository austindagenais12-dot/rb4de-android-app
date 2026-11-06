# High-signal RB4DX startup diagnostics

## Intent

The v0.12.17 60-second diagnostic produced a roughly 50 MB raw log. Its
8 MB head-and-tail export still contained tens of thousands of repetitive
instrument-controller polling messages, obscuring Vulkan stalls and present
events in the omitted middle. Austin wants us to keep progressing toward a
stable, visible Rock Band 4 game rather than repeating uninformative probes.

## Scope

Keep all non-controller-polling lines in their original order whenever they
fit. Collapse only four known repetitive `joypad_reader` calls (open, close,
reset orientation, extended device-class stub), keeping representative first
occurrences and a count of omitted calls. Continue bounding exports at 8 MB
using the existing start-and-end fallback for exceptional logs. The original
raw log remains intact during analysis and is never rewritten in place.

This is a **diagnostic visibility fix**, not an emulator graphics fix. No live
frame or Deluxe claim follows from this change.

## Approach

Use a single streaming, loss-minimizing filtering pass before the existing
bounded retention method. This keeps non-pad evidence easy to inspect without
altering native logs, and avoids a risky Vulkan synchronization change before
we know why the game stalls. Alternatives rejected: raising the cap to ~50 MB
(burdensome phone exports); filtering all pad errors (could hide new problems);
and keeping arbitrary middle chunks (could still miss the important event).
