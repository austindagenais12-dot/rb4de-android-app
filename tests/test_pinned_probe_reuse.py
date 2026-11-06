#!/usr/bin/env python3
"""Ensure a retry never silently swaps v0.12.14's verified ARM64 helpers."""
from hashlib import sha256
from pathlib import Path


project = Path(__file__).resolve().parent.parent
workflow = (project / ".github/workflows/android.yml").read_text()
sources = {
    "native/glibc_file_probe.c": "b4b85d52d9d325cc14de0654e977d42d22e1662885af2cd4de2fc9b7c96db11b",
    "native/glibc_x11_socket_redirect.c": "c53afbb0d6b4f1cffd2e58fa9b17577679799c707e0dcdcf28d2463547027c32",
}
for relative, expected in sources.items():
    actual = sha256((project / relative).read_bytes()).hexdigest()
    assert actual == expected, f"Pinned prebuilt executable no longer matches source: {relative}"

assert "run-id: 42126146668" in workflow
assert "rb4dx-live-heartbeat-debug" in workflow
assert "a3bc456162219d5b44f2903c1ed167ff95b3733c" in workflow
assert "librb4dx_glibc_fileprobe.so librb4dx_x11_redirect.so" in workflow
assert '"lib/arm64-v8a/$name"' in workflow
assert "aarch64-linux-gnu-gcc" not in workflow
print("PASS: native glibc probes reuse an exact, successful v0.12.14 APK")
