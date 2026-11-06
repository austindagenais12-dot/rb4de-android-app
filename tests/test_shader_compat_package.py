"""RB4 v2.21 shader fix must ship in a freshly built native Android runtime."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NATIVE = (ROOT / ".github/workflows/native-runtime.yml").read_text()
ANDROID = (ROOT / ".github/workflows/android.yml").read_text()
GRADLE = (ROOT / "app/build.gradle.kts").read_text()
SHADER = (ROOT / "native/patches/rb4dx-shader-divergence.patch").read_text()
COMPUTE = (ROOT / "native/patches/rb4dx-compute-wait-resolution.patch").read_text()

assert 'versionCode = 28' in GRADLE
assert 'versionName = "0.12.22-shader-compat"' in GRADLE
assert 'feature/v01222-shader-compat' in NATIVE
assert 'feature/v01222-shader-compat' in ANDROID
assert 'rb4dx-shader-divergence.patch' in NATIVE
assert 'rb4dx-compute-wait-resolution.patch' in NATIVE
assert 'v7-rb4dx-shader-compat' in NATIVE, "new compiler requires a fresh ARM64 binary"
assert 'branch=feature/v01222-shader-compat' in ANDROID
assert '"$native_branch" != "feature/v01222-shader-compat"' in ANDROID
assert 'v0.12.22 requires' in ANDROID

assert 'IR::Opcode::LaneId' in SHADER
assert 'IR::Attribute::LocalInvocationIndex' in SHADER
assert 'IR::Opcode::MaskedBitCount32' not in SHADER, "pinned IR lacks this opcode"
assert 'IR::Attribute::LocalInvocationId' in SHADER
assert 'ir.Barrier();' not in SHADER, "retain original barrier placement, change divergence detection only"

assert 'RB4DX_WAIT_RESOLVED queue=asc{}' in COMPUTE
assert 'wait_iterations >= 100\'000' in COMPUTE
assert 'YIELD_ASC(vqid);' in COMPUTE, "must preserve the PM4 wait comparison"
assert 'BACHATA_PIPELINE_WARMUP_SKIP' not in SHADER
assert 'pipeline_cache_enabled' not in SHADER
print("PASS: v0.12.22 pinned shader divergence and compute wait runtime wiring")
