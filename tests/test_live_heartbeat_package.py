"""The surface diagnostic APK must install separately and reuse verified shadPS4."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
gradle = (ROOT / "app/build.gradle.kts").read_text()
android = (ROOT / ".github/workflows/android.yml").read_text()
runtime = (ROOT / ".github/workflows/native-runtime.yml").read_text()

assert 'versionCode = 22' in gradle
assert 'versionName = "0.12.16-extended-startup"' in gradle
assert 'applicationIdSuffix = ".linuxvk.turnip.inputbridge"' in gradle
assert 'manifestPlaceholders["appLabel"] = "RB4DX Controller Input"' in gradle
assert 'feature/v01214-live-surface-heartbeat' in android
assert 'branch=feature/v01213-gpu-wait-trace' in android
assert 'run_id="42119601182"' in android
assert '"$native_sha" != "18bbb0bc0c914f86816513cf4d10b99178984524"' in android
assert '"$native_branch" != "feature/v01213-gpu-wait-trace"' in android
assert "dev.rb4dx.standalone.linuxvk.turnip.inputbridge" in android
assert "rb4dx-extended-startup-debug" in android
assert "feature/v01213-gpu-wait-trace" in runtime
assert "feature/v01214-live-surface-heartbeat" not in runtime
print("PASS: previous live heartbeat path retained; controller APK pins verified v0.12.13 runtime")
