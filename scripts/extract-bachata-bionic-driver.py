#!/usr/bin/env python3
"""Extract one verified Bionic driver from the verified official BachataS4 APK.

The Bachata 26.1.0 *EMULATOR* package is glibc-only and is intentionally
excluded. Only Turnip_v26.3.0-R5 is suitable for Android/Vortek here.
"""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

DRIVER_ENTRY = "assets/drivers/Turnip_v26.3.0-R5.zip"
NOTICE_ENTRY = "assets/licenses/mesa-turnip-26.3.0-r5-NOTICE.txt"


def main(argv):
    if len(argv) != 5:
        raise ValueError("usage: extract-bachata-bionic-driver.py APK SHA256 OUTPUT_SO NOTICE")
    apk, expected_sha, output, notice = map(str, argv[1:])
    blob = Path(apk).read_bytes()
    actual_sha = hashlib.sha256(blob).hexdigest()
    if actual_sha != expected_sha.lower():
        raise ValueError(f"APK SHA-256 mismatch: expected {expected_sha}, got {actual_sha}")
    with zipfile.ZipFile(io.BytesIO(blob)) as outer:
        with zipfile.ZipFile(io.BytesIO(outer.read(DRIVER_ENTRY))) as inner:
            meta = json.loads(inner.read("meta.json"))
            if meta.get("libraryName") != "libvulkan_freedreno.so":
                raise ValueError("Bachata package is not a compatible Bionic freedreno driver")
            library = inner.read("libvulkan_freedreno.so")
        license_text = outer.read(NOTICE_ENTRY)
    if len(library) < 64 or library[:6] != b"\x7fELF\x02\x01" or library[18:20] != b"\xb7\x00":
        raise ValueError("Bachata driver is not an ARM64 little-endian ELF shared library")
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    Path(notice).parent.mkdir(parents=True, exist_ok=True)
    Path(output).write_bytes(library)
    Path(notice).write_bytes(license_text)
    print(f"Extracted verified Bachata Bionic Turnip driver ({len(library)} bytes)")


if __name__ == "__main__":
    try:
        main(sys.argv)
    except (ValueError, OSError, zipfile.BadZipFile, KeyError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
