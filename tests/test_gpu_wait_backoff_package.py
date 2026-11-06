"""The next APK must include the newly compiled wait-backoff runtime."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
gradle = (ROOT / "app/build.gradle.kts").read_text()
workflow = (ROOT / ".github/workflows/android.yml").read_text()
native = (ROOT / ".github/workflows/native-runtime.yml").read_text()

assert 'versionCode = 25' in gradle
assert 'versionName = "0.12.19-gpu-wait-backoff"' in gradle
assert 'feature/v01219-gpu-wait-backoff' in workflow
assert 'run_id="42209460284"' in workflow
assert '"$native_sha" != "7e27c0634c1b0bb0bd42397629bd10d3676adb01"' in workflow
assert '"$native_branch" != "feature/v01219-gpu-wait-backoff"' in workflow
assert "rb4dx-gpu-wait-backoff.patch" in native
assert "rb4dx_gpu_wait_backoff.h" in native
assert "rb4dx-gpu-wait-backoff-debug" in workflow
assert "versionCode='25' versionName='0.12.19-gpu-wait-backoff'" in workflow
print("PASS: backoff APK can only use its own verified ARM64 native runtime")
