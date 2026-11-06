package dev.rb4dx.standalone;

public final class BachataAudioProtocolTest {
    public static void main(String[] args) {
        byte[] header = {4, 10, 0, 0, 0};
        check(BachataAudioProtocol.opcode(header) == 4, "prepare opcode");
        check(BachataAudioProtocol.payloadLength(header) == 10, "length little endian");
        byte[] prepare = {2, 1, (byte) 0x80, (byte) 0xbb, 0, 0, 0, 4, 0, 0};
        BachataAudioProtocol.Format fmt = BachataAudioProtocol.parsePrepare(prepare);
        check(fmt.channels == 2 && fmt.sampleRate == 48000 && fmt.encoding == 2, "stereo s16 48k");
        check(BachataAudioProtocol.isAllowedLength(16384), "bounded PCM");
        check(!BachataAudioProtocol.isAllowedLength(8 * 1024 * 1024), "reject huge PCM");
        boolean rejected = false;
        try { BachataAudioProtocol.parsePrepare(new byte[]{8, 9}); }
        catch (IllegalArgumentException ok) { rejected = true; }
        check(rejected, "reject malformed prepare");
        System.out.println("PASS: Bachata audio wire protocol validates stereo S16 and bounded packets");
    }
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
