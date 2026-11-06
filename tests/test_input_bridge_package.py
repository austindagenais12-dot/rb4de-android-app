from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
gradle = (ROOT / "app/build.gradle.kts").read_text()
workflow = (ROOT / ".github/workflows/android.yml").read_text()

assert 'versionName = "0.12.19-gpu-wait-backoff"' in gradle, "controller app version must advance"
assert 'applicationIdSuffix = ".linuxvk.turnip.inputbridge"' in gradle, "preserve prior app data in separate package"
assert 'feature/v01215-controller-input' in workflow, "CI must trigger new input branch"
assert '42119601182' in workflow, "pin previously verified guest runtime"
assert '18bbb0bc0c914f86816513cf4d10b99178984524' in workflow, "reject altered runtime SHA"
print("PASS: isolated controller APK preserves prior builds and pins native input-capable runtime")
