# RB4DX GPU wait CPU backoff — design

## Intent

On S24 Ultra with Rock Band 4 v2.21 (CUSA02084), the 60-second probe shows repeated GPU `WAIT_REG_MEM` equal-to-zero polls at adjacent game-memory addresses and roughly 500 million GPU task resumes. Vulkan presents continue past the waits, so they are not one permanent deadlock. Reduce wasteful scheduler spinning while preserving the guest's synchronization semantics.

## Constraints and success

- Never bypass `WAIT_REG_MEM`, mutate its target memory, fake a fence, or change game files.
- Do not delay another queued GPU task or GPU command; the backoff applies only when the *one remaining* task yields from a repeatedly unsatisfied graphics wait and no GPU commands are pending.
- Allow the producer to wake the GPU thread with the existing submission condition variable; otherwise recheck after a short bounded timeout (50 microseconds).
- Add wait-completion duration/iteration diagnostics to compare with the original v0.12.18 run. Only an on-device retest can establish performance improvement.
- Keep live Android frame transport and Deluxe loading out of scope; they remain independent unimplemented paths.

## Approaches

1. **Recommended:** Condition-variable timed backoff when a solitary GPU task is repeatedly waiting. Existing wake-ups remain effective; no guest semantics change. Tiny extra wake-up latency on cleared waits.
2. Add sleeps inside every `WAIT_REG_MEM` loop. Rejected: stalls other queued GPU tasks that could satisfy the wait.
3. Ignore or forcibly clear waits. Rejected: violates synchronization and risks corrupted scenes or false progress.

## Components

- `native/patches/rb4dx_gpu_wait_backoff.h`: isolated, host-testable eligibility policy.
- `native/patches/rb4dx-gpu-wait-backoff.patch`: ARM64 shadPS4 GPU coroutine signaller, 50µs conditional scheduler wait, wait-completion metrics.
- `native-runtime.yml`: apply on isolated branch and cache under a distinct key.
- `tests`: exercise backoff eligibility and patch applicability before native build.

The user explicitly requested continued autonomous work and approved GitHub publication and build iterations. This is an experimental performance change, not a claimed game-rendering fix.
