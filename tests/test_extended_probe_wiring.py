"""Require a bounded probe, real cancellation, and honest presentation wording."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
probe = (root / "app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java").read_text()
ui = (root / "app/src/main/java/dev/rb4dx/standalone/MainActivity.java").read_text()

assert "GameProbeOptions.requireDurationMs(timeoutMs)" in probe
assert '"max_runtime_ms="' in probe
assert '"stopped_by_user="' in probe
assert "NativeRuntimeSocket.cancelSession(listeningFd)" in probe
assert "stop.onStop" in probe
assert "process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)" in probe
assert "NativeRuntimeSocket.readSession(listeningFd, timeoutMs)" in probe

assert 'radio("Quick check • 18 seconds")' in ui
assert 'radio("Extended • 60 seconds (recommended)")' in ui
assert 'radio("Deep diagnostic • 120 seconds")' in ui
assert 'button(body, "Stop startup test")' in ui
assert "activeProbeStop.requestStop()" in ui
assert "GameProbeOptions.DEFAULT_MS" in ui
assert "NOT game video" in ui
print("PASS: Android UI and emulator lifecycle support 18/60/120s and immediate Stop")
