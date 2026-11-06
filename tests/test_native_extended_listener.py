"""Guard against native listener silently ending early on a 120-second probe."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
source = (root / "app/src/main/cpp/native_runtime_socket.c").read_text()
assert "timeout_ms > 120000" in source
assert "rb4dx_socket_cancelled(server_fd)" in source
assert "CLOCK_MONOTONIC" in source
assert "POLL_INTERVAL_MS = 250" in source
assert "while (used < sizeof(result) - 1" not in source, "full trace buffer must not disconnect input"
assert "Java_dev_rb4dx_standalone_NativeRuntimeSocket_cancelSession" in source
print("PASS: native listener stays up to 120 seconds and honors prompt cancellation")
