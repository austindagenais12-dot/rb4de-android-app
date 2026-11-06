// Eligibility must avoid slowing other queued GPU work or changing short waits.
#include <cassert>
#include <cstdint>

#include "rb4dx_gpu_wait_backoff.h"

int main() {
    using AmdGpu::Rb4dx::ShouldBackoffGpuWait;
    constexpr std::uint64_t threshold = 4096;
    assert(ShouldBackoffGpuWait(true, threshold, 1, 0));
    assert(ShouldBackoffGpuWait(true, threshold + 1, 1, 0));

    assert(!ShouldBackoffGpuWait(false, threshold, 1, 0));
    assert(!ShouldBackoffGpuWait(true, threshold - 1, 1, 0));
    assert(!ShouldBackoffGpuWait(true, threshold, 0, 0));
    assert(!ShouldBackoffGpuWait(true, threshold, 2, 0));
    assert(!ShouldBackoffGpuWait(true, threshold, 1, 1));
    assert(!ShouldBackoffGpuWait(true, threshold, 2, 1));
}
