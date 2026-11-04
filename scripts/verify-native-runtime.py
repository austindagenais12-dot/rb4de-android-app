#!/usr/bin/env python3
"""Verify integrity and required components of a built ARM64 runtime bundle.

Uses only the Python standard library and streams ZIP members to bound memory.
This is a package check, not an Android execution/compatibility test.
"""

from __future__ import annotations

import hashlib
import json
import sys
import zipfile
from pathlib import Path, PurePosixPath

REQUIRED = {
    "host/shadps4-arm64-fex",
    "host/ld-linux-aarch64.so.1",
    "host/fexcore-smoke",
    "host/lib/libvulkan_vortek.so",
}


def verify(runtime_path: Path, manifest_path: Path) -> tuple[int, int]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 2 or manifest.get("protocolVersion") != 1:
        raise ValueError("Unsupported native-runtime manifest version")
    files = manifest.get("files")
    if not isinstance(files, list) or not files:
        raise ValueError("Empty or invalid native-runtime manifest")

    by_path = {}
    for item in files:
        name = item["path"]
        if (not isinstance(name, str) or name in by_path or name.startswith("/")
                or "\\" in name or name != str(PurePosixPath(name))
                or any(part in (".", "..", "")
                       for part in PurePosixPath(name).parts)):
            raise ValueError(f"Unsafe or repeated path in manifest: {name}")
        by_path[name] = item
    missing = REQUIRED - by_path.keys()
    if missing:
        raise ValueError(f"Core runtime dependencies missing: {sorted(missing)}")

    total_bytes = 0
    seen = set()
    with zipfile.ZipFile(runtime_path) as archive:
        for entry in archive.infolist():
            name = entry.filename
            if entry.is_dir():
                continue
            if name not in by_path or name in seen:
                raise ValueError(f"Unexpected/duplicate runtime member: {name}")
            seen.add(name)
            declared = by_path[name]
            if declared["size"] != entry.file_size:
                raise ValueError(f"Incorrect size for {name}")
            digest = hashlib.sha256()
            with archive.open(entry) as stream:
                while data := stream.read(1024 * 1024):
                    digest.update(data)
                    total_bytes += len(data)
            if digest.hexdigest() != declared["sha256"]:
                raise ValueError(f"Incorrect SHA256 for {name}")
    if seen != by_path.keys():
        raise ValueError(f"Manifest files missing in ZIP: {sorted(by_path.keys() - seen)}")
    return len(seen), total_bytes


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: verify-native-runtime.py runtime.zip manifest.json", file=sys.stderr)
        return 2
    try:
        count, size = verify(Path(sys.argv[1]), Path(sys.argv[2]))
    except (OSError, KeyError, TypeError, ValueError, zipfile.BadZipFile) as error:
        print(f"FAILED: {error}", file=sys.stderr)
        return 1
    print(f"PASS: {count} runtime files, {size} uncompressed bytes, hashes match")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
