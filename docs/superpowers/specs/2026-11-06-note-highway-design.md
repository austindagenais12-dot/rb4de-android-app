# Guitar Touch Note Highway — UI Design

## Intent

Austin's screenshot of a Guitar Hero-style mobile game shows descending colored notes, a five-lane perspective track and a hit area at the bottom. The RB4DX guitar touch mode should feel like that: fingers tap or hold the correct lane as notes reach the bottom. Keep normal Android Bluetooth/USB gamepad and existing classic touch pad selectable.

## Approaches considered

1. **Static colored fret buttons:** simple and already implemented, but misses descending notes; rejected.
2. **Full-screen real note highway linked to RB4 gameplay:** ultimate goal, but requires a functioning live game renderer and a timing/notes feed from Rock Band; not yet attainable without further integration.
3. **Animated, clearly labeled note-highway preview built on the existing controller bridge:** selected for the next test APK. It proves responsive five-lane UI and runs harmless sample notes while retaining actual standard PS4 button snapshot output. Real song timing remains a separate integration milestone.

## Initial design

- Five lanes in Rock Band guitar order: green, red, yellow, blue, orange.
- Dark cinematic perspective track, softly colored lane guides, falling demo note gems, glowing horizontal strike line, tappable colored pads at the bottom.
- Tap and hold multiple frets at the strike area; map the five lanes to Cross, Circle, Triangle, Square and R1 in the existing standard-pad diagnostic channel.
- Visibly label notes `SAMPLE NOTES - NOT GAME SYNCED` and label instrument status `STANDARD PAD MAPPING` to avoid mistaking them for Rock Band chart data.
- Make the guitar panel taller in portrait and responsive in landscape; continue to offer regular gamepad view, opacity adjustment and one-tap hide.
- Never modify game files or weaken Vulkan, startup or controller socket diagnostics.

## Future live integration (not part of sample preview)

An imported song's five-fret chart/timing, or a verified real-time emulator/game event source, must drive displayed notes. Need time alignment to audio and eventual PS4 guitar instrument class mapping; ordinary PS4 pad buttons do not certify Rock Band will recognize a guitar.
