// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

/** Decodes the five-byte Bachata audio socket header and PREPARE command. */
final class BachataAudioProtocol {
    static final int HEADER_BYTES = 5;
    static final int MAX_PACKET_BYTES = 1 << 20;

    static final class Format {
        final int channels, sampleRate, encoding, bufferBytes;
        Format(int channels, int sampleRate, int encoding, int bufferBytes) {
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.encoding = encoding;
            this.bufferBytes = bufferBytes;
        }
    }

    private BachataAudioProtocol() {}

    static int opcode(byte[] header) {
        if (header == null || header.length != HEADER_BYTES)
            throw new IllegalArgumentException("Bachata header must contain five bytes");
        return header[0] & 255;
    }

    static int payloadLength(byte[] header) {
        if (header == null || header.length != HEADER_BYTES)
            throw new IllegalArgumentException("Bachata header must contain five bytes");
        long length = readU32(header, 1);
        if (length > MAX_PACKET_BYTES) throw new IllegalArgumentException("Audio payload exceeds cap");
        return (int) length;
    }

    static boolean isAllowedLength(int count) { return count >= 0 && count <= MAX_PACKET_BYTES; }

    static Format parsePrepare(byte[] payload) {
        if (payload == null || payload.length != 10)
            throw new IllegalArgumentException("Bachata prepare must contain ten bytes");
        int channels = payload[0] & 255;
        int sampleType = payload[1] & 255;
        int sampleRate = (int) readU32(payload, 2);
        int bufferBytes = (int) readU32(payload, 6);
        if ((channels != 1 && channels != 2) || sampleType != 1
                || sampleRate < 8000 || sampleRate > 192000
                || bufferBytes <= 0 || bufferBytes > MAX_PACKET_BYTES)
            throw new IllegalArgumentException("Unsupported Bachata audio format");
        // AUDIO_FORMAT_PCM_16BIT; shadPS4's Bachata backend emits S16LE stereo.
        return new Format(channels, sampleRate, 2, bufferBytes);
    }

    private static long readU32(byte[] data, int offset) {
        return (data[offset] & 255L) | ((data[offset + 1] & 255L) << 8)
                | ((data[offset + 2] & 255L) << 16) | ((data[offset + 3] & 255L) << 24);
    }
}
