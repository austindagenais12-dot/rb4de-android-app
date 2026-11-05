"""The live display signal must be driven by Vortek, not a synthetic timer."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
component = (ROOT / "app/src/main/java/com/winlator/xenvironment/components/VortekRendererComponent.java").read_text()
boot = (ROOT / "app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java").read_text()
activity = (ROOT / "app/src/main/java/dev/rb4dx/standalone/MainActivity.java").read_text()

callback = component.split("private void updateWindowContent(int windowId, int imageIndex)", 1)[1].split("private native", 1)[0]
assert "nativeNotePresentedImage(windowId, imageIndex)" in callback
assert "renderLiveHeartbeat()" in callback
assert "getLastPresentedNativeHardwareBuffer()" not in callback
assert "Bitmap.wrapHardwareBuffer" not in callback
assert "private void renderLiveHeartbeat()" in component
assert "surface.lockHardwareCanvas()" in component
assert "surface.unlockCanvasAndPost(canvas)" in component
assert "heartbeat.tryBegin(" in component
assert "heartbeat.recordFailed(" in component
assert "heartbeat.recordPosted()" in component
assert "previewOnly = true;" in component
assert "android_live_transport_heartbeat_attempted=" in component
assert "android_live_transport_heartbeat_posted=" in component
assert "android_live_transport_heartbeat_failed=" in component
assert "vortek.heartbeatReport()" in boot
assert '.append("android_surface_attached=false\\n")' in boot
assert "NOT game video" in activity
print("PASS: Vortek present callbacks drive bounded Android-only heartbeat and diagnostics")
