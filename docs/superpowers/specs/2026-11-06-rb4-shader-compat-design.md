# RB4DX v0.12.22 shader compatibility design

## Objective

Preserve v0.12.21 live game frames, GPU wait backoff, and controller input while addressing the RB4 v2.21 compute-shader barrier divergence suspected at the first 3D scene. Distinguish an unresolved asynchronous compute fence from a GPU-render or input failure.

## Implementation

- Apply the existing RB4DX GPU wait-trace and bounded-backoff patches to the pinned ARM64 runtime.
- Backport the applicable upstream `SharedMemoryBarrierPass` divergence detection for `LaneId` and `LocalInvocationIndex`. The pinned source has no `MaskedBitCount32` opcode, so do not reference it.
- Log long *resolved* compute-queue `WAIT_REG_MEM` operations, leaving actual wait/synchronization unchanged.
- Require a fresh native runtime built from this branch before an APK may be packaged. Keep the branch separate from v0.12.21.

## Shader cache safety

The pinned Bachata runtime deliberately disables persistent pipeline warmup due to guest-pointer-bearing serialized pipeline metadata causing SIGSEGV. Do not enable that unsafe path merely to increase performance. Treat a safe shader/pipeline cache as its own project phase, requiring pointer-free or validated serialization, build/driver keyed entries, and tested invalidation. Precompilation cannot fix the divergent-barrier bug by itself.

## Verification

Run focused tests, statically validate native patch compatibility against the exact pinned tree with `git apply --check`, run Java/C tests in `tests/run.sh`, then use native GitHub Actions to obtain an ARM64 binary. No claims of actual playability without on-device testing.
