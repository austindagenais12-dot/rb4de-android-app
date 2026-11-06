// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

/** Bounded snapshot publisher and truthful per-session transport counters. */
final class InputFramePump {
    interface Writer { int send(String frame); }

    private final Ps4InputState state;
    private final Writer writer;
    private int delivered, notConnected, errors, buttonsPressed, lastSendError;

    InputFramePump(Ps4InputState state, Writer writer) {
        this.state = state;
        this.writer = writer;
    }

    synchronized void tick() {
        String frame = state.nextFrame();
        if (!frame.contains(" buttons=0x0 ")) buttonsPressed++;
        int result = writer.send(frame);
        if (result == frame.length()) delivered++;
        else if (result == 0) notConnected++;
        else {
            errors++;
            lastSendError = result;
        }
    }

    synchronized String report() {
        return "input_protocol=BACHATA/1_INPUT_SLOT_0\n"
                + "input_frames_delivered_to_socket=" + delivered + '\n'
                + "input_frames_not_connected=" + notConnected + '\n'
                + "input_frames_with_buttons=" + buttonsPressed + '\n'
                + "input_send_failures=" + errors + '\n'
                + "input_last_send_error=" + lastSendError + '\n'
                + "input_guest_acknowledgement=NOT_AVAILABLE\n";
    }
}
