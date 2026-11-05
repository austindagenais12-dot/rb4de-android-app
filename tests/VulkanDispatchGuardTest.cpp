#include <cassert>

#include "rb4dx_dispatch_guard.h"

namespace {
void SupportedCommand(int) {}
using Command = void (*)(int);
}

int main() {
    const Command valid = &SupportedCommand;
    const Command missing = nullptr;

    // A Vulkan extension is usable only if both the feature and entrypoint exist.
    assert(RB4DX::HasUsableOptionalDispatch(true, valid));
    assert(!RB4DX::HasUsableOptionalDispatch(true, missing));
    assert(!RB4DX::HasUsableOptionalDispatch(false, valid));
    assert(!RB4DX::HasUsableOptionalDispatch(false, missing));
}
