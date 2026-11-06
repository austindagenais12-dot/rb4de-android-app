// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** App-private AF_UNIX to Android AudioTrack bridge used by the Bachata shadPS4 core. */
final class BachataAudioSocket implements AutoCloseable {
    static { System.loadLibrary("rb4dx_bridge"); }

    private static native int nativeOpen(String path);
    private static native int nativeAccept(int serverFd);
    private static native int nativeRead(int clientFd, byte[] buffer, int start, int length);
    private static native void nativeClose(int fd);

    private final Path socketPath;
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final AtomicInteger activeClients = new AtomicInteger();
    private volatile boolean running;
    private volatile int serverFd = -1;
    private final AtomicInteger sessions = new AtomicInteger();
    private final AtomicInteger prepareCount = new AtomicInteger();
    private final AtomicInteger pcmPackets = new AtomicInteger();
    private static final int MAX_AUDIO_PORTS = 8;
    private final AtomicLong pcmBytes = new AtomicLong();
    private volatile String lastError = "NONE";

    BachataAudioSocket(Path socketPath) { this.socketPath = socketPath; }

    void start() throws IOException {
        if (serverFd >= 0) throw new IllegalStateException("Already started");
        Files.createDirectories(socketPath.getParent());
        int fd = nativeOpen(socketPath.toString());
        if (fd < 0) throw new IOException("Audio AF_UNIX bind errno=" + (-fd));
        serverFd = fd;
        running = true;
        reader.submit(this::acceptLoop);
    }

    String socketPath() { return socketPath.toString(); }

    String report() {
        return "android_audio_socket_ready=" + (serverFd >= 0) + '\n'
                + "android_audio_sessions=" + sessions.get() + '\n'
                + "android_audio_active_ports=" + activeClients.get() + '\n'
                + "android_audio_prepare_requests=" + prepareCount.get() + '\n'
                + "android_audio_pcm_packets=" + pcmPackets.get() + '\n'
                + "android_audio_pcm_bytes=" + pcmBytes.get() + '\n'
                + "android_audio_last_error=" + lastError + '\n';
    }

    private void acceptLoop() {
        while (running) {
            int client = nativeAccept(serverFd);
            if (client == -11) continue; // EAGAIN after a 250-ms polling slice.
            if (client < 0) {
                if (running) lastError = "ACCEPT_ERRNO_" + (-client);
                break;
            }
            sessions.incrementAndGet();
            if (activeClients.incrementAndGet() > MAX_AUDIO_PORTS) {
                activeClients.decrementAndGet();
                nativeClose(client);
                lastError = "TOO_MANY_AUDIO_PORTS";
                continue;
            }
            try {
                clients.submit(() -> {
                    try { serve(client); }
                    catch (IOException | RuntimeException error) {
                        lastError = error.getClass().getSimpleName() + "_" + error.getMessage();
                    } finally {
                        nativeClose(client);
                        activeClients.decrementAndGet();
                    }
                });
            } catch (RejectedExecutionException closed) {
                    nativeClose(client);
                    activeClients.decrementAndGet();
                return;
            }
        }
    }

    private void serve(int client) throws IOException {
        AudioTrack track = null;
        try {
            while (running) {
                byte[] header = new byte[BachataAudioProtocol.HEADER_BYTES];
                if (!readFully(client, header)) return;
                int opcode = BachataAudioProtocol.opcode(header);
                int len = BachataAudioProtocol.payloadLength(header);
                byte[] data = new byte[len];
                if (len > 0 && !readFully(client, data)) return;
                if (opcode == 0) return;
                if (opcode == 4) {
                    BachataAudioProtocol.Format format = BachataAudioProtocol.parsePrepare(data);
                    if (track != null) { track.stop(); track.release(); }
                    track = makeTrack(format);
                    prepareCount.incrementAndGet();
                    track.play();
                } else if (opcode == 5) {
                    pcmPackets.incrementAndGet();
                    pcmBytes.addAndGet(len);
                    if (track != null) {
                        int pos = 0;
                        while (pos < data.length && running) {
                            int written = track.write(data, pos, data.length - pos,
                                    AudioTrack.WRITE_BLOCKING);
                            if (written <= 0) throw new IOException("AudioTrack.write=" + written);
                            pos += written;
                        }
                    }
                } else if (opcode == 1 && track != null) track.play();
                else if (opcode == 2 && track != null) { track.stop(); track.flush(); }
                else if (opcode == 3 && track != null) track.pause();
                else if (opcode == 6 && track != null) track.flush();
                // Query opcode 7 requires an upstream response. The current
                // pinned shadPS4 backend only sends 0/4/5; report unsupported.
                else if (opcode == 7) lastError = "UNSUPPORTED_AUDIO_QUERY_7";
            }
        } finally {
            if (track != null) {
                try { track.pause(); track.flush(); }
                catch (IllegalStateException ignored) { }
                track.release();
            }
        }
    }

    private AudioTrack makeTrack(BachataAudioProtocol.Format format) throws IOException {
        int channels = format.channels == 2 ? AudioFormat.CHANNEL_OUT_STEREO
                : AudioFormat.CHANNEL_OUT_MONO;
        int min = AudioTrack.getMinBufferSize(format.sampleRate, channels,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IOException("Invalid AudioTrack minimum buffer: " + min);
        int buffer = Math.min(BachataAudioProtocol.MAX_PACKET_BYTES,
                Math.max(min, format.bufferBytes));
        AudioTrack result = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(channels).setSampleRate(format.sampleRate).build())
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(buffer).build();
        if (result.getState() != AudioTrack.STATE_INITIALIZED) {
            result.release();
            throw new IOException("AudioTrack failed to initialize");
        }
        return result;
    }

    private boolean readFully(int client, byte[] bytes) throws IOException {
        int offset = 0;
        while (offset < bytes.length && running) {
            int size = nativeRead(client, bytes, offset,
                    Math.min(bytes.length - offset, 65536));
            if (size == -11) continue;
            if (size < 0) throw new IOException("Audio recv errno=" + (-size));
            if (size == 0) return false;
            offset += size;
        }
        return running && offset == bytes.length;
    }

    @Override public void close() {
        running = false;
        reader.shutdownNow();
        clients.shutdownNow();
        try { reader.awaitTermination(1300, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        try { clients.awaitTermination(1500, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (serverFd >= 0) { nativeClose(serverFd); serverFd = -1; }
        try { Files.deleteIfExists(socketPath); }
        catch (IOException ignored) { }
    }
}
