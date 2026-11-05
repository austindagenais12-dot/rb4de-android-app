"""Experimental GPU-wait APK must not overwrite earlier package identities."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
gradle = (ROOT / "app/build.gradle.kts").read_text()
android = (ROOT / ".github/workflows/android.yml").read_text()
runtime = (ROOT / ".github/workflows/native-runtime.yml").read_text()
patch = ROOT / "native/patches/rb4dx-gpu-wait-trace.patch"

assert 'applicationIdSuffix = ".linuxvk.turnip.gpuwaittrace"' in gradle
assert 'versionName = "0.12.13-gpu-wait-trace"' in gradle
assert 'manifestPlaceholders["appLabel"] = "RB4DX GPU Wait Trace"' in gradle
assert "feature/v01213-gpu-wait-trace" in android
assert "feature/v01213-gpu-wait-trace" in runtime
assert "dev.rb4dx.standalone.linuxvk.turnip.gpuwaittrace" in android
assert "rb4dx-gpu-wait-trace-debug" in android
assert "rb4dx-gpu-wait-trace.patch" in runtime
assert patch.exists()
source = patch.read_text()
assert "RB4DX_WAIT_BEGIN" in source
assert "debug_resume_count % 5'000'000" in source
assert "!wait_reg_mem->Test(regs.reg_array)" in source
print("PASS: isolated GPU wait trace APK and native patch wiring")
