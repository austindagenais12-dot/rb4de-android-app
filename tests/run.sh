#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_dir="$project_dir/tests/build"
mkdir -p "$build_dir"
sources=(
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/GameProbeOptions.java"
  "$project_dir/tests/GameProbeOptionsTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/ShaderCachePaths.java"
  "$project_dir/tests/ShaderCachePathsTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/GpuDriverChoice.java"
  "$project_dir/tests/GpuDriverChoiceTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/BachataAudioProtocol.java"
  "$project_dir/tests/BachataAudioProtocolTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/ProcessLogDrain.java"
  "$project_dir/tests/ProcessLogDrainTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/ProbeStopToken.java"
  "$project_dir/tests/ProbeStopTokenTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/DeluxeInstaller.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/Ps4InputState.java"
  "$project_dir/tests/Ps4InputStateTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/InputFramePump.java"
  "$project_dir/tests/InputFramePumpTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/TouchPadLayout.java"
  "$project_dir/tests/TouchPadLayoutTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/NoteHighwayPreview.java"
  "$project_dir/tests/NoteHighwayPreviewTest.java"
  "$project_dir/tests/InstallerSmokeTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/StartupLogStore.java"
  "$project_dir/tests/StartupLogStoreTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/NativeCrashSummary.java"
  "$project_dir/tests/NativeCrashSummaryTest.java"
  "$project_dir/app/src/main/java/dev/rb4dx/standalone/GpuWaitSummary.java"
  "$project_dir/tests/GpuWaitSummaryTest.java"
  "$project_dir/app/src/main/java/com/winlator/xenvironment/components/FramePreviewTrace.java"
  "$project_dir/tests/FramePreviewTraceTest.java"
  "$project_dir/app/src/main/java/com/winlator/xenvironment/components/LiveHeartbeatTracker.java"
  "$project_dir/tests/LiveHeartbeatTrackerTest.java"
  "$project_dir/app/src/main/java/com/winlator/xenvironment/components/LiveFrameGeometry.java"
  "$project_dir/tests/LiveFrameGeometryTest.java"
  "$project_dir/app/src/main/java/com/winlator/xenvironment/components/FramePixelStats.java"
  "$project_dir/tests/FramePixelStatsTest.java"
)
if command -v javac >/dev/null 2>&1; then
  javac -d "$build_dir" "${sources[@]}"
else
  java --module jdk.compiler/com.sun.tools.javac.Main -d "$build_dir" "${sources[@]}"
fi
java -cp "$build_dir" InstallerSmokeTest
java -cp "$build_dir" dev.rb4dx.standalone.GameProbeOptionsTest
java -cp "$build_dir" dev.rb4dx.standalone.ShaderCachePathsTest
java -cp "$build_dir" dev.rb4dx.standalone.GpuDriverChoiceTest
java -cp "$build_dir" dev.rb4dx.standalone.BachataAudioProtocolTest
java -cp "$build_dir" dev.rb4dx.standalone.ProcessLogDrainTest
java -cp "$build_dir" dev.rb4dx.standalone.ProbeStopTokenTest
java -cp "$build_dir" dev.rb4dx.standalone.Ps4InputStateTest
java -cp "$build_dir" dev.rb4dx.standalone.InputFramePumpTest
java -cp "$build_dir" dev.rb4dx.standalone.TouchPadLayoutTest
java -cp "$build_dir" dev.rb4dx.standalone.NoteHighwayPreviewTest
java -cp "$build_dir" dev.rb4dx.standalone.StartupLogStoreTest
java -cp "$build_dir" dev.rb4dx.standalone.NativeCrashSummaryTest
java -cp "$build_dir" dev.rb4dx.standalone.GpuWaitSummaryTest
java -cp "$build_dir" com.winlator.xenvironment.components.FramePreviewTraceTest
java -cp "$build_dir" com.winlator.xenvironment.components.LiveHeartbeatTrackerTest
java -cp "$build_dir" com.winlator.xenvironment.components.LiveFrameGeometryTest
java -cp "$build_dir" com.winlator.xenvironment.components.FramePixelStatsTest
c++ -std=c++20 -Wall -Wextra -Werror \
  -I "$project_dir/native/patches" \
  "$project_dir/tests/VulkanDispatchGuardTest.cpp" \
  -o "$build_dir/VulkanDispatchGuardTest"
"$build_dir/VulkanDispatchGuardTest"
echo "PASS: optional Vulkan extension requires a valid dispatcher entrypoint"
c++ -std=c++20 -Wall -Wextra -Werror \
  -I "$project_dir/native/patches" \
  "$project_dir/tests/GpuWaitBackoffTest.cpp" \
  -o "$build_dir/GpuWaitBackoffTest"
"$build_dir/GpuWaitBackoffTest"
echo "PASS: GPU wait backoff only applies to solitary prolonged memory waits"
cc -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror \
  -I "$project_dir/tests/include" \
  -c "$project_dir/app/src/main/cpp/native_audio_socket.c" \
  -o "$build_dir/native_audio_socket.o"
echo "PASS: native Bachata audio AF_UNIX socket builds warning-free"
cc -std=c11 -Wall -Wextra -Werror \
  -I "$project_dir/native/vortek" \
  "$project_dir/tests/VortekFrameRingTest.c" \
  "$project_dir/native/vortek/rb4dx_frame_ring.c" \
  -o "$build_dir/VortekFrameRingTest"
"$build_dir/VortekFrameRingTest"
cc -std=c11 -Wall -Wextra -Werror \
  -I "$project_dir/native/vortek" \
  "$project_dir/tests/VortekWindowImagesTest.c" \
  "$project_dir/native/vortek/rb4dx_window_images.c" \
  -o "$build_dir/VortekWindowImagesTest"
"$build_dir/VortekWindowImagesTest"
cc -std=c11 -Wall -Wextra -Werror -pthread \
  -I "$project_dir/app/src/main/cpp" \
  "$project_dir/tests/InputTransportTest.c" \
  "$project_dir/app/src/main/cpp/rb4dx_input_transport.c" \
  -o "$build_dir/InputTransportTest"
"$build_dir/InputTransportTest"
cc -std=c11 -Wall -Wextra -Werror -pthread \
  -I "$project_dir/app/src/main/cpp" \
  "$project_dir/tests/SocketCancelTest.c" \
  "$project_dir/app/src/main/cpp/rb4dx_socket_cancel.c" \
  -o "$build_dir/SocketCancelTest"
"$build_dir/SocketCancelTest"
cc -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror -pthread \
  -I "$project_dir/tests/include" \
  -I "$project_dir/app/src/main/cpp" \
  "$project_dir/tests/NativeListenerSessionTest.c" \
  "$project_dir/app/src/main/cpp/native_runtime_socket.c" \
  "$project_dir/app/src/main/cpp/rb4dx_socket_cancel.c" \
  "$project_dir/app/src/main/cpp/rb4dx_input_transport.c" \
  -o "$build_dir/NativeListenerSessionTest"
if "$build_dir/NativeListenerSessionTest"; then
  :
else
  status=$?
  if [ "$status" -ne 77 ]; then exit "$status"; fi
fi
python3 "$project_dir/tests/test_native_extended_listener.py"
python3 "$project_dir/tests/test_extended_probe_wiring.py"
python3 "$project_dir/tests/test_extended_apk_package.py"
python3 "$project_dir/tests/test_runtime_verifier.py"
python3 "$project_dir/tests/test_bachata_driver_extraction.py"
python3 "$project_dir/tests/test_gpu_wait_trace_package.py"
python3 "$project_dir/tests/test_gpu_wait_backoff_package.py"
python3 "$project_dir/tests/test_shader_compat_package.py"
python3 "$project_dir/tests/test_bachata_play_wiring.py"
python3 "$project_dir/tests/test_live_heartbeat_wiring.py"
python3 "$project_dir/tests/test_live_heartbeat_package.py"
python3 "$project_dir/tests/test_live_surface_transport_wiring.py"
python3 "$project_dir/tests/test_input_bridge_package.py"
python3 "$project_dir/tests/test_note_highway_ui.py"
python3 "$project_dir/tests/test_pinned_probe_reuse.py"
