// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen foreground renderer; retains the original probe as a separate mode. */
public final class GamePlayerActivity extends Activity implements SurfaceHolder.Callback {
    static final String EXTRA_RUNTIME = "rb4dx.runtime";
    static final String EXTRA_GAME = "rb4dx.game";
    static final String EXTRA_TITLE = "rb4dx.title";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Ps4InputState controller = new Ps4InputState();
    private final ProbeStopToken stop = new ProbeStopToken();
    private boolean bootStarted;
    private volatile boolean closing;
    private SurfaceView surface;
    private TouchControllerPanel controls;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        surface.getHolder().addCallback(this);

        controls = new TouchControllerPanel(this, controller);
        controls.setOpacityPercent(70);
        FrameLayout.LayoutParams pad = new FrameLayout.LayoutParams(-1, dp(185), Gravity.BOTTOM);
        controls.setVisibility(View.GONE);
        root.addView(controls, pad);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setBackgroundColor(0xa0000000);
        status.setText("Starting Rock Band 4…");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.LEFT);
        statusParams.setMargins(dp(10), dp(8), dp(155), 0);
        root.addView(status, statusParams);

        Button stopButton = new Button(this);
        stopButton.setText("Stop game");
        stopButton.setAllCaps(false);
        stopButton.setOnClickListener(v -> stopGame());
        FrameLayout.LayoutParams stopParams = new FrameLayout.LayoutParams(dp(130), dp(52),
                Gravity.TOP | Gravity.RIGHT);
        root.addView(stopButton, stopParams);

        Button controlsButton = new Button(this);
        controlsButton.setText("Controls");
        controlsButton.setAllCaps(false);
        controlsButton.setOnClickListener(v -> {
            if (controls.getVisibility() == View.VISIBLE) {
                controls.releaseAll();
                controls.setVisibility(View.GONE);
            } else controls.setVisibility(View.VISIBLE);
        });
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(dp(130), dp(52),
                Gravity.TOP | Gravity.RIGHT);
        controlsParams.topMargin = dp(55);
        root.addView(controlsButton, controlsParams);
        setContentView(root);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (closing || bootStarted) return;
        bootStarted = true;
        String runtime = getIntent().getStringExtra(EXTRA_RUNTIME);
        String game = getIntent().getStringExtra(EXTRA_GAME);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (runtime == null || game == null || title == null) {
            status.setText("No verified game/runtime selected. Return to the launcher.");
            return;
        }
        worker.submit(() -> {
            GameBootProbe.Result result = GameBootProbe.play(this, Path.of(runtime),
                    Uri.parse(game), title, holder, controller, stop);
            getSharedPreferences("rb4dx", MODE_PRIVATE).edit()
                    .putString("last_play_report", result.report).apply();
            runOnUiThread(() -> {
                if (closing) return;
                status.setText("Game session ended. Report saved in launcher.");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                finish();
            });
        });
    }

    private void stopGame() {
        status.setText("Stopping emulator and releasing audio/GPU resources…");
        stop.requestStop();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) { stop.requestStop(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return AndroidGamepadInput.handleKey(event, controller)
                || super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return AndroidGamepadInput.handleJoystick(event, controller)
                || super.dispatchGenericMotionEvent(event);
    }

    @Override public void onBackPressed() {
        stopGame();
    }

    @Override protected void onStop() {
        stop.requestStop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        closing = true;
        stop.requestStop();
        worker.shutdownNow();
        controls.releaseAll();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
