// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <cstdint>

namespace AmdGpu::Rb4dx {

// The GPU dispatcher must not delay a runnable queue or a pending command.
// Let short guest waits spin normally before opting into a bounded timed wait.
constexpr bool ShouldBackoffGpuWait(bool yielded_from_wait, std::uint64_t unsuccessful_polls,
                                    unsigned int queued_submits, unsigned int queued_commands) {
    return yielded_from_wait && unsuccessful_polls >= 4096 && queued_submits == 1 &&
           queued_commands == 0;
}

} // namespace AmdGpu::Rb4dx
