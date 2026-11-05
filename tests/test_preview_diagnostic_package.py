"""Safety checks for the isolated preview diagnostic APK configuration.

The CI job additionally inspects the *built* APK manifest with aapt;
this local check catches accidental reuse before spending CI minutes.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
gradle = (ROOT / "app/build.gradle.kts").read_text()
workflow = (ROOT / ".github/workflows/android.yml").read_text()
main = (ROOT / "app/src/main/java/dev/rb4dx/standalone/MainActivity.java").read_text()
probe = (ROOT / "app/src/main/java/dev/rb4dx/standalone/GameBootProbe.java").read_text()

assert 'applicationIdSuffix = ".linuxvk.turnip.previewdiag"' in gradle
assert 'versionName = "0.12.12-preview-diagnostics"' in gradle
assert 'manifestPlaceholders["appLabel"] = "RB4DX Preview Diagnostic"' in gradle

assert "feature/v01212-preview-failure-diagnostics" in workflow
assert "dev.rb4dx.standalone.linuxvk.turnip.previewdiag" in workflow
assert "rb4dx-preview-failure-diagnostics-debug" in workflow

assert "preferences.getBoolean(GPU_TURNIP_ENABLED, true)" in main
assert 'gpuPreferences.getBoolean("gpu_turnip_enabled", true)' in probe

print("PASS: separate preview-diagnostic APK identity and Turnip first-run defaults")
