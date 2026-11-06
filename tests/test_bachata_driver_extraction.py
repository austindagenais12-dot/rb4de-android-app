"""Integration regression for extracting the *Bionic* driver from an official-style APK."""
import hashlib
import io
import json
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "extract-bachata-bionic-driver.py"


def zipped(entries):
    blob = io.BytesIO()
    with zipfile.ZipFile(blob, "w") as archive:
        for name, value in entries.items():
            archive.writestr(name, value)
    return blob.getvalue()


with tempfile.TemporaryDirectory() as temp:
    tmp = pathlib.Path(temp)
    # Valid ELF64 LE AArch64 header: e_machine 183 at byte offset 18.
    elf = bytearray(64)
    elf[:6] = b"\x7fELF\x02\x01"
    elf[18:20] = (183).to_bytes(2, "little")
    inner = zipped({"meta.json": json.dumps({"libraryName": "libvulkan_freedreno.so"}),
                    "libvulkan_freedreno.so": bytes(elf)})
    apk = tmp / "source.apk"
    apk.write_bytes(zipped({"assets/drivers/Turnip_v26.3.0-R5.zip": inner,
                            "assets/licenses/mesa-turnip-26.3.0-r5-NOTICE.txt": b"Mesa notice"}))
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    output = tmp / "libvulkan_freedreno_compat.so"
    notice = tmp / "NOTICE.txt"
    result = subprocess.run([sys.executable, str(SCRIPT), str(apk), digest,
                             str(output), str(notice)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert output.read_bytes() == bytes(elf)
    assert notice.read_bytes() == b"Mesa notice"
    bad = subprocess.run([sys.executable, str(SCRIPT), str(apk), "0" * 64,
                          str(output), str(notice)], capture_output=True, text=True)
    assert bad.returncode != 0 and "SHA-256" in bad.stderr

print("PASS: official Bachata Bionic Turnip extraction checks APK digest and ARM64 ELF")
