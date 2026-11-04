#!/usr/bin/env python3
"""Smoke tests for verifying an ARM64 backend runtime artifact."""

from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-native-runtime.py"


def run_validator(tmp: Path, corrupt: bool = False) -> subprocess.CompletedProcess[str]:
    entries = {
        "host/shadps4-arm64-fex": b"ARM64 program placeholder",
        "host/ld-linux-aarch64.so.1": b"ARM64 loader placeholder",
        "host/fexcore-smoke": b"FEXCore placeholder",
        "host/lib/libvulkan_vortek.so": b"Vortek placeholder",
    }
    archive = tmp / "runtime.zip"
    manifest = tmp / "manifest.json"
    with zipfile.ZipFile(archive, "w") as target:
        for name, data in entries.items():
            target.writestr(name, data)
    files = [
        {"path": path, "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
        for path, data in entries.items()
    ]
    if corrupt:
        files[0]["sha256"] = "0" * 64
    manifest.write_text(json.dumps({
        "schemaVersion": 2,
        "protocolVersion": 1,
        "files": files,
    }), encoding="utf-8")
    return subprocess.run([sys.executable, str(VERIFY), str(archive), str(manifest)],
                          capture_output=True, text=True, check=False)


with tempfile.TemporaryDirectory(prefix="rb4dx-runtime-test-") as directory:
    tmp = Path(directory)
    good = run_validator(tmp)
    if good.returncode != 0:
        raise AssertionError(good.stderr)
    bad = run_validator(tmp, corrupt=True)
    if bad.returncode == 0 or "Incorrect SHA256" not in bad.stderr:
        raise AssertionError("Tampered runtime was not rejected")

print("PASS: native runtime manifest and hash verification")
