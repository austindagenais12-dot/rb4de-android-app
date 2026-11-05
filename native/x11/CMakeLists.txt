cmake_minimum_required(VERSION 3.22)
project(rb4dx_android_x11 C)

if(NOT DEFINED RB4DX_X11_SOURCE)
  message(FATAL_ERROR "RB4DX_X11_SOURCE must point to the pinned embedded X11 checkout")
endif()

add_subdirectory("${RB4DX_X11_SOURCE}/lorie/src/main/cpp" engine)

add_executable(rb4dx-x11-server standalone_server.c)
target_include_directories(rb4dx-x11-server PRIVATE
  "${RB4DX_X11_SOURCE}/lorie/src/main/cpp/lorie")
target_link_libraries(rb4dx-x11-server PRIVATE LorieNative android log)
target_link_options(rb4dx-x11-server PRIVATE
  -nostdlib++ -Wl,--no-undefined -Wl,--gc-sections)
