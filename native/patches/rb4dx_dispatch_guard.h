#pragma once

namespace RB4DX {

// Advertised optional Vulkan extensions are unsafe when their dynamic entrypoint
// was not loaded by the Vulkan-Hpp device dispatcher (e.g., via a proxy ICD).
template <typename Command>
[[nodiscard]] constexpr bool HasUsableOptionalDispatch(bool extension_advertised,
                                                       Command entrypoint) noexcept {
    return extension_advertised && entrypoint != nullptr;
}

} // namespace RB4DX
