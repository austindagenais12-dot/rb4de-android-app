"""The extended diagnostic upgrades the current controller app in place."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
build = (root / "app/build.gradle.kts").read_text()
workflow = (root / ".github/workflows/android.yml").read_text()

assert 'versionCode = 27' in build
assert 'versionName = "0.12.21-live-geometry"' in build
assert 'applicationIdSuffix = ".linuxvk.turnip.inputbridge"' in build
assert 'feature/v01218-high-signal-logs' in workflow
assert 'run_id="42119601182"' in workflow
assert '"$native_sha" != "18bbb0bc0c914f86816513cf4d10b99178984524"' in workflow
assert 'run-id: 42126146668' in workflow
assert "versionCode='27' versionName='0.12.21-live-geometry'" in workflow
print("PASS: extended diagnostic APK upgrades controller version without unpinning runtime")
