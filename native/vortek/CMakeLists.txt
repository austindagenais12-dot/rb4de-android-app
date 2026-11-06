cmake_minimum_required(VERSION 3.22.1)
project(RB4DXVortekAndroid LANGUAGES C CXX)

# Upstream source is fetched at locked, full Git revisions by Android CI.
# Do not use an unpinned Winlator binary or import the original app's UI.
if(NOT DEFINED WINLATOR_SOURCE OR NOT DEFINED VORTEK_SERVER_SOURCE)
  message(FATAL_ERROR "WINLATOR_SOURCE and VORTEK_SERVER_SOURCE are required")
endif()

set(WINLATOR_NATIVE "${WINLATOR_SOURCE}/app/src/main/cpp")
# Several pinned upstream utility headers rely on standard C declarations
# supplied by their original Android build. Include them in this standalone
# build without changing the upstream source tree.
add_compile_options(-include "${CMAKE_CURRENT_SOURCE_DIR}/upstream_compat.h")
include_directories(
  "${WINLATOR_NATIVE}/winlator/include"
  "${WINLATOR_NATIVE}/libadrenotools/include")

add_subdirectory("${WINLATOR_NATIVE}/winlator" "${CMAKE_BINARY_DIR}/upstream-winlator")
add_subdirectory("${WINLATOR_NATIVE}/libadrenotools" "${CMAKE_BINARY_DIR}/upstream-adrenotools")
add_subdirectory("${VORTEK_SERVER_SOURCE}" "${CMAKE_BINARY_DIR}/upstream-vortek")

add_library(rb4dx_vortek_bridge SHARED vortek_bridge.c rb4dx_window_images.c rb4dx_frame_ring.c)
target_compile_features(rb4dx_vortek_bridge PRIVATE c_std_11)
# The pinned Vulkan wrapper header exposes Android-only entrypoints and
# internally compares signed loop counters with unsigned array sizes.
target_compile_definitions(rb4dx_vortek_bridge PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
target_compile_options(rb4dx_vortek_bridge PRIVATE -Wall -Wextra -Werror -Wno-sign-compare)
target_include_directories(rb4dx_vortek_bridge PRIVATE "${VORTEK_SERVER_SOURCE}/include")
target_link_libraries(rb4dx_vortek_bridge PRIVATE vortekrenderer log android dl)
