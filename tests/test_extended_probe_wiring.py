"""Require a bounded probe, real cancellation, and honest presentation wording."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
probe = (root / "app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java").read_text()
ui = (root / "app/src/main/java/dev/rb4dx/standalone/MainActivity.java").read_text()

assert "GameProbeOptions.requireDurationMs(timeoutMs)" in probe
assert '"max_runtime_ms="' in probe
assert '"stopped_by_user="' in probe
assert "private static Result failed(StringBuilder report, String reason, ProbeStopToken stop)" in probe
assert '"timed_out=false\\nstopped_by_user=" + stop.isStopped()' in probe
assert "NativeRuntimeSocket.cancelSession(listeningFd)" in probe
assert "stop.onStop" in probe
assert "process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)" in probe
assert "NativeRuntimeSocket.readSession(listeningFd, timeoutMs)" in probe
finally_block = probe.rsplit("} finally {", 1)[1]
assert "NativeRuntimeSocket.cancelSession(serverFd)" in finally_block, (
    "exception paths must interrupt the native listener before closing the descriptor"
)
assert "listener.awaitTermination(" in finally_block, (
    "native listener must finish before the server descriptor can be reused"
)
assert finally_block.index("NativeRuntimeSocket.cancelSession(serverFd)") < finally_block.index(
    "NativeRuntimeSocket.closeServer(serverFd)"
)

assert 'radio("Quick check • 18 seconds")' in ui
assert 'radio("Extended • 60 seconds (recommended)")' in ui
assert 'radio("Deep diagnostic • 120 seconds")' in ui
assert 'button(body, "Stop startup test")' in ui
assert "activeProbeStop.requestStop()" in ui
assert "GameProbeOptions.DEFAULT_MS" in ui
assert "not yet verified gameplay" in ui
print("PASS: Android UI and emulator lifecycle support 18/60/120s and immediate Stop")
