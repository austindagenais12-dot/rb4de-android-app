"""Guard the controller, audio, Vulkan and foreground Play lifecycle wiring."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
java = root / "app/src/main/java/dev/rb4dx/standalone"
probe = (java / "GameBootProbe.java").read_text()
main = (java / "MainActivity.java").read_text()
play = (java / "GamePlayerActivity.java").read_text()
audio = (java / "BachataAudioSocket.java").read_text()
pixels = (root / "app/src/main/java/com/winlator/xenvironment/components/VortekRendererComponent.java").read_text()
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
cmake = (root / "app/src/main/cpp/CMakeLists.txt").read_text()
native = (root / "app/src/main/cpp/native_runtime_socket.c").read_text()

assert '"MESA_SHADER_CACHE_DIR"' in probe and "Os.setenv" in probe
assert '"BACHATA_ALSA_SOCKET"' in probe and "audio.start();" in probe
assert "BachataAudioSocket" in audio and "AudioTrack" in audio
assert "MAX_AUDIO_PORTS = 8" in audio and "clients.submit(" in audio
assert "PERFORMANCE_MODE_LOW_LATENCY" in audio
assert "native_audio_socket.c" in cmake
assert '"gpu_driver_choice"' in main and '"bachata_compat"' in (java / "GpuDriverChoice.java").read_text()
assert "FramePixelStats.classify" in pixels
assert "input_button_transitions=" in (java / "InputFramePump.java").read_text()
assert "static Result play(" in probe and "process.waitFor();" in probe
assert "ProcessLogDrain.capture" in probe and "readSession(listeningFd, play ? 0 : timeoutMs)" in probe
assert "timeout_ms == 0 ? INT64_MAX" in native
assert "launchPlayer()" in main and "GamePlayerActivity.class" in main
assert main.count("void onResume()") == 1, "Play report must use existing lifecycle hook"
assert "GameBootProbe.play(" in play and "stop.requestStop();" in play
assert 'android:name=".GamePlayerActivity"' in manifest
print("PASS: Bachata audio, driver A/B, pixel stats, bounded Play log and landscape lifecycle wired")
