"""Ensure the Android guitar UI consumes the pure timing model and discloses demo status."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
panel = (root / "app/src/main/java/dev/rb4dx/standalone/TouchControllerPanel.java").read_text()
activity = (root / "app/src/main/java/dev/rb4dx/standalone/MainActivity.java").read_text()

assert "NoteHighwayPreview.notesAt" in panel, "must render the real sample timeline"
assert "postInvalidateDelayed" in panel, "visible falling notes require animation frames"
assert "NOT GAME SYNCED" in panel, "must disclose animation is a sample"
assert "Tap the five pads" in panel, "must explain tap strike behavior"
assert "touchPanel.setGuitarMode" in activity, "guitar layout mode must remain selectable"
assert "setPanelHeightForGuitar" in activity, "sample view needs a tall portrait touch area"
print("PASS: sample note highway renders moving fret guide with honest status")
