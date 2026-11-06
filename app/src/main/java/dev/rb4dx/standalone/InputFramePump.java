// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

/** Bounded snapshot publisher and truthful per-session transport counters. */
final class InputFramePump {
    private static final int MAX_TRANSITION_EVENTS = 24;
    interface Writer { int send(String frame); }

    private final Ps4InputState state;
    private final Writer writer;
    private int delivered, notConnected, errors, buttonsPressed, lastSendError;
    private int buttonTransitions, omittedTransitions;
    private String lastButtonMask = "0x0";
    private final StringBuilder transitionTrace = new StringBuilder();

    InputFramePump(Ps4InputState state, Writer writer) {
        this.state = state;
        this.writer = writer;
    }

    synchronized void tick() {
        String frame = state.nextFrame();
        if (!frame.contains(" buttons=0x0 ")) buttonsPressed++;
        int result = writer.send(frame);
        int maskStart = frame.indexOf(" buttons=");
        int maskEnd = maskStart < 0 ? -1 : frame.indexOf(' ', maskStart + 9);
        if (maskEnd > maskStart) {
            String mask = frame.substring(maskStart + 9, maskEnd);
            if (!mask.equals(lastButtonMask)) {
                buttonTransitions++;
                if (buttonTransitions <= MAX_TRANSITION_EVENTS) {
                    int seqStart = frame.indexOf(" seq=");
                    int seqEnd = seqStart < 0 ? -1 : frame.indexOf(' ', seqStart + 5);
                    String sequence = seqEnd > seqStart
                            ? frame.substring(seqStart + 5, seqEnd) : "unknown";
                    transitionTrace.append("input_transition_").append(buttonTransitions)
                            .append("=seq:").append(sequence)
                            .append(" buttons=").append(mask)
                            .append(" delivered=").append(result == frame.length())
                            .append('\n');
                } else omittedTransitions++;
                lastButtonMask = mask;
            }
        }
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
                + "input_button_transitions=" + buttonTransitions + '\n'
                + "input_button_transitions_omitted=" + omittedTransitions + '\n'
                + transitionTrace
                + "input_guest_acknowledgement=NOT_AVAILABLE\n";
    }
}
