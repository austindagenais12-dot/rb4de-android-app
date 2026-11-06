cmake_minimum_required(VERSION 3.22.1)
project(rb4dx_native_bridge C)

add_library(rb4dx_bridge SHARED
        native_runtime_socket.c
        rb4dx_socket_cancel.c
        rb4dx_input_transport.c
        native_vulkan_probe.c
        native_vulkan_frame.c
        native_file_access.c)
target_compile_features(rb4dx_bridge PRIVATE c_std_11)
target_compile_options(rb4dx_bridge PRIVATE -Wall -Wextra -Werror)
target_link_libraries(rb4dx_bridge PRIVATE android vulkan)

