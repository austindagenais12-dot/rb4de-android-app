// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.os.SystemClock;
import android.util.SparseIntArray;

/** Lightweight multitouch controller surface; sends input, never claims to render the game. */
final class TouchControllerPanel extends View {
    private static final int BG = Color.rgb(16, 22, 42);
    private static final int TEXT = Color.rgb(236, 242, 255);
    private final Ps4InputState state;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseIntArray stickOwners = new SparseIntArray();
    private boolean guitar;
    private long sampleStartMs = SystemClock.uptimeMillis();
    private float opacity = .83f;
    private int leftX = 128, leftY = 128, rightX = 128, rightY = 128;
    private boolean leftActive, rightActive;
    private long activeButtons;

    TouchControllerPanel(Context context, Ps4InputState controllerState) {
        super(context);
        state = controllerState;
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Touch gamepad. Multitouch supports PS4 face buttons, D-pad, shoulders and analog sticks.");
    }

    void setGuitarMode(boolean enabled) {
        guitar = enabled;
        sampleStartMs = SystemClock.uptimeMillis();
        releaseAll();
        setContentDescription(enabled
                ? "Experimental five-lane touch guitar with moving sample notes. Tap the strike line. This is not synchronized with Rock Band."
                : "Touch gamepad. Use two fingers for buttons and analog sticks.");
        invalidate();
    }

    void setOpacityPercent(int percent) {
        opacity = Math.max(.3f, Math.min(1f, percent / 100f));
        invalidate();
    }

    void releaseAll() {
        stickOwners.clear();
        activeButtons = 0;
        leftActive = rightActive = false;
        leftX = leftY = rightX = rightY = 128;
        state.clearTouch();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseAll();
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            int owner = TouchPadLayout.stickAt(guitar, event.getX(index), event.getY(index),
                    getWidth(), getHeight());
            if (owner != 0 && !hasOwner(owner)) stickOwners.put(event.getPointerId(index), owner);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            stickOwners.delete(event.getPointerId(event.getActionIndex()));
        }
        long buttons = 0;
        leftActive = rightActive = false;
        leftX = leftY = rightX = rightY = 128;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                    && i == event.getActionIndex()) continue;
            float x = event.getX(i), y = event.getY(i);
            int owner = stickOwners.get(event.getPointerId(i), 0);
            if (owner == 1 || owner == 2) {
                float cx = getWidth() * (owner == 1 ? .39f : .60f);
                int[] axes = TouchPadLayout.stickAxis(x, y, cx,
                        getHeight() * .82f, TouchPadLayout.stickRadius(getWidth(), getHeight()));
                if (owner == 1) {
                    leftX = axes[0]; leftY = axes[1]; leftActive = true;
                } else {
                    rightX = axes[0]; rightY = axes[1]; rightActive = true;
                }
            } else {
                buttons |= TouchPadLayout.buttonAt(guitar, x, y, getWidth(), getHeight());
            }
        }
        activeButtons = buttons;
        state.setTouchButtons(buttons);
        state.setTouchAxes(leftX, leftY, rightX, rightY, leftActive, rightActive);
        if (action == MotionEvent.ACTION_UP) stickOwners.clear();
        invalidate();
        return true;
    }

    private boolean hasOwner(int stick) {
        for (int i = 0; i < stickOwners.size(); i++) {
            if (stickOwners.valueAt(i) == stick) return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        paint.setColor(Color.argb((int)(opacity * 230), 10, 18, 35));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(2, 2, width - 2, height - 2, 24, 24, paint);
        paint.setColor(Color.argb((int)(opacity * 220), 73, 124, 239));
        paint.setStrokeWidth(3);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(2, 2, width - 2, height - 2, 24, 24, paint);
        paint.setStyle(Paint.Style.FILL);

        if (guitar) {
            drawHighway(canvas, width, height);
            if (isShown()) postInvalidateDelayed(30);
            return;
        }
        float r = Math.min(width * .073f, height * .135f);
        drawButton(canvas, "OPTIONS", width * .5f, height * .14f, r * .88f,
                0xff747f9c, Ps4InputState.OPTIONS);
        String[] top = {"L2", "L1", "R1", "R2"};
        float[] topX = {.10f, .25f, .75f, .90f};
        long[] topButton = {Ps4InputState.L2, Ps4InputState.L1,
                Ps4InputState.R1, Ps4InputState.R2};
        for (int i = 0; i < top.length; i++) {
            drawButton(canvas, top[i], width * topX[i], height * .14f,
                    r * .87f, 0xff6477ab, topButton[i]);
        }
        drawButton(canvas, "↑", width * .17f, height * .34f, r, 0xff6477ab, Ps4InputState.UP);
        drawButton(canvas, "→", width * .25f, height * .48f, r, 0xff6477ab, Ps4InputState.RIGHT);
        drawButton(canvas, "↓", width * .17f, height * .62f, r, 0xff6477ab, Ps4InputState.DOWN);
        drawButton(canvas, "←", width * .09f, height * .48f, r, 0xff6477ab, Ps4InputState.LEFT);
        drawButton(canvas, "△", width * .85f, height * .34f, r, 0xffd7bd47, Ps4InputState.TRIANGLE);
        drawButton(canvas, "○", width * .94f, height * .48f, r, 0xffe76577, Ps4InputState.CIRCLE);
        drawButton(canvas, "✕", width * .85f, height * .62f, r, 0xff31c56d, Ps4InputState.CROSS);
        drawButton(canvas, "□", width * .76f, height * .48f, r, 0xff67a9ed, Ps4InputState.SQUARE);
        drawStick(canvas, "L", width * .39f, height * .82f,
                leftActive ? leftX : 128, leftActive ? leftY : 128);
        drawStick(canvas, "R", width * .60f, height * .82f,
                rightActive ? rightX : 128, rightActive ? rightY : 128);
    }

    private void drawHighway(Canvas canvas, float width, float height) {
        final int[] colors = {0xff24d36a, 0xfff35366, 0xfff7ce49, 0xff4e9efa, 0xfffa9d45};
        final String[] frets = {"G", "R", "Y", "B", "O"};
        final long[] buttons = {Ps4InputState.CROSS, Ps4InputState.CIRCLE,
                Ps4InputState.TRIANGLE, Ps4InputState.SQUARE, Ps4InputState.R1};

        paint.setColor(0xff111a30);
        canvas.drawRoundRect(width * .035f, height * .20f, width * .965f,
                height * .94f, 14, 14, paint);
        // Five guitar lanes widen toward the strike line for a cinematic runway.
        for (int lane = 0; lane <= 5; lane++) {
            float topX = width * (.13f + lane * .74f / 5f);
            float bottomX = width * (.04f + lane * .92f / 5f);
            paint.setColor(0xff405371);
            paint.setStrokeWidth(lane == 0 || lane == 5 ? 3 : 2);
            canvas.drawLine(topX, height * .22f, bottomX, height * .91f, paint);
        }
        for (int n = 0; n < 7; n++) {
            float p = n / 7f;
            float y = height * (.22f + .69f * p);
            float left = width * (.13f - .09f * p);
            float right = width - left;
            paint.setColor(0xff263451);
            paint.setStrokeWidth(1);
            canvas.drawLine(left, y, right, y, paint);
        }

        long elapsed = SystemClock.uptimeMillis() - sampleStartMs;
        for (NoteHighwayPreview.Note note : NoteHighwayPreview.notesAt(elapsed)) {
            float x = NoteHighwayPreview.trackX(note.lane, note.progress, width);
            float y = height * (.22f + .62f * note.progress);
            float radius = Math.min(width * .083f, height * .05f)
                    * (.62f + .38f * note.progress);
            if (note.sustain) {
                paint.setColor((colors[note.lane] & 0x00ffffff) | 0x77000000);
                paint.setStrokeWidth(radius * .50f);
                canvas.drawLine(x, y - radius * 3.5f, x, y, paint);
            }
            paint.setColor(colors[note.lane]);
            canvas.drawRoundRect(x - radius, y - radius * .40f, x + radius,
                    y + radius * .40f, radius * .38f, radius * .38f, paint);
            paint.setColor(0xfff7faff);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, radius * .07f));
            canvas.drawRoundRect(x - radius, y - radius * .40f, x + radius,
                    y + radius * .40f, radius * .38f, radius * .38f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float hitY = height * .84f;
        paint.setColor(0xffd8ebff);
        paint.setStrokeWidth(4);
        canvas.drawLine(width * .04f, hitY, width * .96f, hitY, paint);
        float r = Math.min(width * .077f, height * .052f);
        for (int i = 0; i < frets.length; i++) {
            float x = NoteHighwayPreview.trackX(i, 1f, width);
            drawButton(canvas, frets[i], x, hitY, r, colors[i], buttons[i]);
        }

        drawLabel(canvas, "RB4DX • TOUCH GUITAR", width * .5f,
                height * .075f, Math.min(19, height * .039f), 0xffe4f0ff);
        drawLabel(canvas, "SAMPLE NOTES • NOT GAME SYNCED", width * .47f,
                height * .145f, Math.min(13, height * .025f), 0xffffb8a9);
        drawButton(canvas, "OPT", width * .88f, height * .13f,
                Math.min(width * .07f, height * .045f), 0xff536989, Ps4InputState.OPTIONS);
        drawLabel(canvas, "Tap the five pads when notes reach the line", width * .5f,
                height * .976f, Math.min(13, height * .022f), 0xffb7cae9);
    }

    private void drawButton(Canvas canvas, String label, float x, float y, float radius,
            int color, long mask) {
        boolean held = (activeButtons & mask) != 0;
        paint.setColor(Color.argb((int)(opacity * (held ? 255 : 205)),
                Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(x, y, held ? radius * 1.06f : radius, paint);
        paint.setColor(held ? 0xffffffff : 0xff14213a);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, radius * .83f, paint);
        paint.setStyle(Paint.Style.FILL);
        drawLabel(canvas, label, x, y, Math.max(11, radius * (label.length() > 2 ? .39f : .7f)),
                0xfff9fbff);
    }

    private void drawStick(Canvas canvas, String label, float x, float y, int ax, int ay) {
        float r = TouchPadLayout.stickRadius(getWidth(), getHeight());
        paint.setColor(0xff324b76);
        canvas.drawCircle(x, y, r, paint);
        paint.setColor(0xff89b2ed);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x + (ax - 128) * r / 127f,
                y + (ay - 128) * r / 127f, r * .46f, paint);
        drawLabel(canvas, label, x, y, Math.max(12, r * .52f), TEXT);
    }

    private void drawLabel(Canvas canvas, String label, float x, float y, float size, int color) {
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(size);
        canvas.drawText(label, x, y - (paint.ascent() + paint.descent()) / 2f, paint);
    }
}
