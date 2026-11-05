// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal game-specific UI; native emulation is intentionally not faked. */
public final class MainActivity extends Activity {
    private static final int PICK_GAME = 100;
    private static final int PICK_DELUXE = 101;
    private static final int EXPORT_GAME_LOG = 102;
    private static final String PREFS = "rb4dx";
    private static final String GAME_URI = "game_tree_uri";
    private static final String TITLE_ID = "title_id";
    private static final String GPU_TURNIP_ENABLED = "gpu_turnip_enabled";
    private static final int BG = Color.rgb(15, 16, 24);
    private static final int FG = Color.rgb(239, 240, 247);
    private static final int MUTED = Color.rgb(174, 179, 197);
    private static final int ACCENT = Color.rgb(87, 115, 235);

    private final ExecutorService importer = Executors.newSingleThreadExecutor();
    private final ExecutorService runtimeWorker = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private TextView gameStatus;
    private TextView modStatus;
    private TextView detailStatus;
    private TextView runtimeStatus;
    private TextView probeStatus;
    private TextView bridgeStatus;
    private TextView gamePreflightStatus;
    private TextView vulkanStatus;
    private TextView allFilesAccessStatus;
    private TextView nativeFileStatus;
    private TextView glibcFileStatus;
    private TextView vulkanFrameStatus;
    private TextView gameBootStatus;
    private TextView turnipStatus;
    private RadioGroup gpuDrivers;
    private RadioButton systemGpuRadio;
    private RadioButton turnipGpuRadio;
    private Button importButton;
    private Button probeButton;
    private Button copyProbeButton;
    private Button bridgeButton;
    private Button copyBridgeButton;
    private Button gamePreflightButton;
    private Button copyGamePreflightButton;
    private Button vulkanButton;
    private Button copyVulkanButton;
    private Button nativeFileButton;
    private Button copyNativeFileButton;
    private Button glibcFileButton;
    private Button copyGlibcFileButton;
    private Button vulkanFrameButton;
    private Button copyVulkanFrameButton;
    private Button gameBootButton;
    private Button copyGameBootButton;
    private Button exportGameBootLogButton;
    private SurfaceView vulkanSurface;
    private Path preparedRuntimeDirectory;
    private String lastProbeReport;
    private String lastBridgeReport;
    private String lastGamePreflightReport;
    private String lastVulkanReport;
    private String lastNativeFileReport;
    private String lastGlibcFileReport;
    private String lastVulkanFrameReport;
    private String lastGameBootReport;
    private Path lastGameBootFullLog;
    private boolean smokeRunning;
    private boolean bridgeRunning;
    private boolean gamePreflightRunning;
    private boolean vulkanRunning;
    private boolean nativeFileRunning;
    private boolean glibcFileRunning;
    private boolean vulkanFrameRunning;
    private boolean gameBootRunning;
    private String selectedTitleId;
    private boolean busy;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedTitleId = preferences.getString(TITLE_ID, DeluxeInstaller.US_TITLE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(30), dp(24), dp(30));
        scroll.addView(body);

        addText(body, "ROCK BAND 4", 32, FG, true);
        addText(body, "DELUXE  •  ANDROID", 20, ACCENT, true);
        addText(body, "Dedicated Rock Band 4 project", 15, MUTED, false);
        addGap(body, 24);

        addText(body, "1. Choose game folder", 19, FG, true);
        gameStatus = addText(body, "Not selected", 14, MUTED, false);
        Button gameButton = button(body, "Select Rock Band 4 folder");
        gameButton.setOnClickListener(v -> chooseGameFolder());
        addGap(body, 22);

        addText(body, "2. Game region", 19, FG, true);
        RadioGroup regions = new RadioGroup(this);
        regions.setOrientation(RadioGroup.VERTICAL);
        RadioButton us = radio("USA • CUSA02084");
        RadioButton eu = radio("Europe • CUSA02901");
        regions.addView(us);
        regions.addView(eu);
        regions.check(selectedTitleId.equals(DeluxeInstaller.EU_TITLE) ? eu.getId() : us.getId());
        regions.setOnCheckedChangeListener((group, checked) -> {
            selectedTitleId = checked == eu.getId()
                    ? DeluxeInstaller.EU_TITLE : DeluxeInstaller.US_TITLE;
            preferences.edit().putString(TITLE_ID, selectedTitleId).apply();
            refreshStatus();
        });
        body.addView(regions);
        addGap(body, 18);

        addText(body, "3. Inspect your game files", 19, FG, true);
        addText(body,
                "Checks eboot.bin and sce_sys/param.sfo through your saved folder permission. "
                        + "Verifies the game region and whether the native emulator can read the raw path. "
                        + "No game files are copied or modified.",
                14, MUTED, false);
        gamePreflightStatus = addText(body, "Select your Rock Band 4 folder above first.",
                13, MUTED, false);
        gamePreflightButton = button(body, "Inspect game folder (read only)");
        gamePreflightButton.setEnabled(false);
        gamePreflightButton.setOnClickListener(v -> runGamePreflight());
        copyGamePreflightButton = button(body, "Copy game inspection report");
        copyGamePreflightButton.setEnabled(false);
        copyGamePreflightButton.setOnClickListener(v -> copyGamePreflightReport());
        addGap(body, 22);

        addText(body, "4. Direct native game-file access", 19, FG, true);
        addText(body,
                "Optional Android All files access permission. It permits this app to access shared "
                        + "storage broadly, not just Rock Band 4, but cannot unlock other apps' "
                        + "protected Android/data folders. We use it only for a read-only game-file test. "
                        + "You may revoke it in Android Settings at any time.",
                14, MUTED, false);
        allFilesAccessStatus = addText(body, "Checking Android storage permission…",
                13, MUTED, false);
        Button storageSettingsButton = button(body, "Open Android All files access settings");
        storageSettingsButton.setOnClickListener(v -> openAllFilesSettings());
        nativeFileStatus = addText(body, "Waiting for permission and a selected game folder.",
                13, MUTED, false);
        nativeFileButton = button(body, "Verify direct native eboot access (read only)");
        nativeFileButton.setEnabled(false);
        nativeFileButton.setOnClickListener(v -> runNativeFileProbe());
        copyNativeFileButton = button(body, "Copy native file-access report");
        copyNativeFileButton.setEnabled(false);
        copyNativeFileButton.setOnClickListener(v -> copyReport("RB4DX direct native storage", lastNativeFileReport));
        addText(body, "Linux glibc game-file check", 18, FG, true);
        addText(body,
                "Uses the very same Linux ARM64 glibc dynamic loader as shadPS4 to open "
                        + "your existing eboot.bin. Reads 16 bytes and exits without launching the game.",
                14, MUTED, false);
        glibcFileStatus = addText(body, "Waiting for permission, a game folder and native runtime…",
                13, MUTED, false);
        glibcFileButton = button(body, "Verify game access through Linux glibc");
        glibcFileButton.setEnabled(false);
        glibcFileButton.setOnClickListener(v -> runGlibcFileProbe());
        copyGlibcFileButton = button(body, "Copy Linux game-file report");
        copyGlibcFileButton.setEnabled(false);
        copyGlibcFileButton.setOnClickListener(v -> copyReport("RB4DX Linux glibc game-file", lastGlibcFileReport));
        addGap(body, 22);

        addText(body, "5. Import Rock Band 4 Deluxe", 19, FG, true);
        addText(body, "Select your existing RB4DX-PS4.zip. The app unpacks it into its own private storage.",
                14, MUTED, false);
        modStatus = addText(body, "Not imported", 14, MUTED, false);
        importButton = button(body, "Import Deluxe ZIP");
        importButton.setOnClickListener(v -> chooseDeluxeArchive());
        detailStatus = addText(body, "", 13, MUTED, false);
        addGap(body, 26);

        addText(body, "6. Native emulator runtime", 19, FG, true);
        runtimeStatus = addText(body, "Checking bundled ARM64 runtime…", 14, MUTED, false);
        addText(body,
                "The runtime is installed and verified automatically in this test build. No root or Shizuku is needed.",
                14, MUTED, false);
        addGap(body, 12);
        addText(body, "Native execution diagnostic", 18, FG, true);
        addText(body, "Runs loader startup, a restricted-syscall workaround, and FEXCore CPU tests. No game files or mod import required.",
                14, MUTED, false);
        probeStatus = addText(body, "Preparing emulator files…", 13, MUTED, false);
        probeButton = button(body, "Run full ARM64 diagnostics");
        probeButton.setEnabled(false);
        probeButton.setOnClickListener(v -> runSmokeTest());
        copyProbeButton = button(body, "Copy diagnostic report");
        copyProbeButton.setEnabled(false);
        copyProbeButton.setOnClickListener(v -> copyProbeReport());
        addGap(body, 22);

        addText(body, "7. Android runtime control bridge", 19, FG, true);
        addText(body,
                "Connects to the real emulator's local control socket and checks startup and error events. "
                        + "Uses a deliberately nonexistent game file; no game or mod files are touched.",
                14, MUTED, false);
        bridgeStatus = addText(body, "Preparing emulator files…", 13, MUTED, false);
        bridgeButton = button(body, "Test native control channel");
        bridgeButton.setEnabled(false);
        bridgeButton.setOnClickListener(v -> runBridgeProbe());
        copyBridgeButton = button(body, "Copy bridge report");
        copyBridgeButton.setEnabled(false);
        copyBridgeButton.setOnClickListener(v -> copyBridgeReport());
        addGap(body, 22);

        addText(body, "8. Android Vulkan display bridge", 19, FG, true);
        addText(body, "Vortek graphics driver", 18, FG, true);
        addText(body,
                "Mesa Turnip is the default for this diagnostic app, based on the earlier "
                        + "System Adreno Vortek crash. The APK bundles a pinned ARM64 Turnip driver. "
                        + "Changing the selection does not replace your phone's system driver. "
                        + "The blue Vulkan surface tests still use system Adreno.",
                14, MUTED, false);
        turnipStatus = addText(body, "Checking bundled Turnip driver…", 13, MUTED, false);
        gpuDrivers = new RadioGroup(this);
        gpuDrivers.setOrientation(RadioGroup.VERTICAL);
        systemGpuRadio = radio("System Adreno (optional)");
        turnipGpuRadio = radio("Mesa Turnip (default for game test)");
        gpuDrivers.addView(systemGpuRadio);
        gpuDrivers.addView(turnipGpuRadio);
        gpuDrivers.check(preferences.getBoolean(GPU_TURNIP_ENABLED, true)
                ? turnipGpuRadio.getId() : systemGpuRadio.getId());
        gpuDrivers.setOnCheckedChangeListener((group, id) -> {
            boolean turnip = id == turnipGpuRadio.getId();
            if (turnip && !turnipDriverExists()) {
                Toast.makeText(this, "Bundled Turnip library is missing from this APK",
                        Toast.LENGTH_LONG).show();
                group.check(systemGpuRadio.getId());
                return;
            }
            preferences.edit().putBoolean(GPU_TURNIP_ENABLED, turnip).apply();
            refreshTurnipStatus();
        });
        body.addView(gpuDrivers);
        refreshTurnipStatus();
        addGap(body, 12);
        addText(body,
                "Tests the real Vulkan driver against an Android SurfaceView: GPU enumeration, "
                        + "surface creation, present-capable queues, and swapchain support. "
                        + "This does not launch the emulator's renderer yet.",
                14, MUTED, false);
        vulkanStatus = addText(body, "Waiting for the Android display surface…",
                13, MUTED, false);
        vulkanSurface = new SurfaceView(this);
        LinearLayout.LayoutParams surfaceParams = new LinearLayout.LayoutParams(-1, dp(210));
        surfaceParams.topMargin = dp(12);
        body.addView(vulkanSurface, surfaceParams);
        vulkanButton = button(body, "Test Vulkan surface and GPU");
        vulkanButton.setEnabled(false);
        vulkanButton.setOnClickListener(v -> runVulkanProbe());
        copyVulkanButton = button(body, "Copy Vulkan report");
        copyVulkanButton.setEnabled(false);
        copyVulkanButton.setOnClickListener(v -> copyVulkanReport());
        addText(body, "GPU frame presentation test", 18, FG, true);
        addText(body,
                "Creates a Vulkan swapchain, clears a GPU image blue and presents it to the "
                        + "rectangle above. This tests a real displayed GPU frame, not PS4 graphics.",
                14, MUTED, false);
        vulkanFrameStatus = addText(body, "Waiting for the Android display surface…",
                13, MUTED, false);
        vulkanFrameButton = button(body, "Render one blue Vulkan test frame");
        vulkanFrameButton.setEnabled(false);
        vulkanFrameButton.setOnClickListener(v -> runVulkanFrameProbe());
        copyVulkanFrameButton = button(body, "Copy Vulkan frame report");
        copyVulkanFrameButton.setEnabled(false);
        copyVulkanFrameButton.setOnClickListener(v -> copyReport("RB4DX Vulkan frame", lastVulkanFrameReport));
        vulkanSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                vulkanStatus.setText("Android display surface ready for Vulkan testing.");
                if (!vulkanRunning) vulkanButton.setEnabled(true);
                if (!vulkanFrameRunning) vulkanFrameButton.setEnabled(true);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                vulkanButton.setEnabled(false);
                vulkanFrameButton.setEnabled(false);
                vulkanStatus.setText("Android display surface not currently available.");
                vulkanFrameStatus.setText("Android display surface not currently available.");
            }
        });
        addGap(body, 22);

        addText(body, "9. Real Rock Band 4 startup diagnostic", 19, FG, true);
        addText(body,
                "Starts the actual shadPS4 emulator on your selected vanilla Rock Band 4 "
                        + "eboot.bin for at most 18 seconds, then stops it. It checks the "
                        + "game version automatically and records the startup stage, "
                        + "socket messages and emulator errors. If the GPU produced a window "
                        + "buffer, a still-frame preview may appear in the display above after "
                        + "the process exits. It does NOT launch Deluxe or provide live gameplay. "
                        + "It may temporarily use substantial RAM.",
                14, MUTED, false);
        gameBootStatus = addText(body,
                "Waiting for the verified native runtime, game folder and All files access…",
                13, MUTED, false);
        gameBootButton = button(body, "Test real Rock Band 4 initialization (18 sec)");
        gameBootButton.setEnabled(false);
        gameBootButton.setOnClickListener(v -> runGameBootProbe());
        copyGameBootButton = button(body, "Copy real-game startup report");
        copyGameBootButton.setEnabled(false);
        copyGameBootButton.setOnClickListener(
                v -> copyReport("RB4DX real game startup", lastGameBootReport));
        exportGameBootLogButton = button(body, "Export complete startup log (.log)");
        exportGameBootLogButton.setEnabled(false);
        exportGameBootLogButton.setOnClickListener(v -> exportGameBootLog());
        addGap(body, 22);

        addText(body, "10. Play", 19, FG, true);
        Button play = button(body, "PLAY • Android bridge not connected");
        play.setEnabled(false);
        addText(body,
                "The native control channel is an early milestone. Android graphics, audio, input and game-folder mounting still need integration. Play remains disabled until actual game launching works.",
                14, MUTED, false);
        addGap(body, 16);
        addText(body,
                "Independent test install • Preserves the previous RB4DX app and Bachata S4",
                12, MUTED, false);

        setContentView(scroll);
        refreshStatus();
        prepareNativeRuntime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNativeAccessStatus();
        refreshTurnipStatus();
    }

    private void openAllFilesSettings() {
        Intent appSettings = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        appSettings.setData(Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(appSettings);
        } catch (ActivityNotFoundException error) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (ActivityNotFoundException missing) {
                Toast.makeText(this, "Android did not expose All files access settings",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshNativeAccessStatus() {
        if (allFilesAccessStatus == null) return;
        boolean granted = Environment.isExternalStorageManager();
        allFilesAccessStatus.setText(granted
                ? "All files access: GRANTED by Android"
                : "All files access: NOT GRANTED (tap settings and enable it if comfortable)");
        String selected = preferences.getString(GAME_URI, null);
        nativeFileButton.setEnabled(granted && selected != null && !nativeFileRunning);
        if (glibcFileButton != null) {
            glibcFileButton.setEnabled(granted && selected != null
                    && preparedRuntimeDirectory != null && !glibcFileRunning);
        }
        if (gameBootButton != null) {
            gameBootButton.setEnabled(granted && selected != null
                    && preparedRuntimeDirectory != null && !gameBootRunning);
        }
        if (!granted) {
            nativeFileStatus.setText("Waiting for optional All files access permission.");
        } else if (selected == null) {
            nativeFileStatus.setText("Select the Rock Band 4 folder at the top first.");
        } else if (lastNativeFileReport == null && !nativeFileRunning) {
            nativeFileStatus.setText("Ready to test opening eboot.bin from native Android code.");
        }
    }

    private void runNativeFileProbe() {
        if (nativeFileRunning || !Environment.isExternalStorageManager()) return;
        String saved = preferences.getString(GAME_URI, null);
        if (saved == null) return;
        nativeFileRunning = true;
        nativeFileButton.setEnabled(false);
        nativeFileStatus.setText("Opening only eboot.bin from native code (read-only)…");
        String folder = GamePreflight.rawPrimaryPath(Uri.parse(saved));
        importer.submit(() -> {
            String report;
            if (folder == null) {
                report = "RB4DX_DIRECT_NATIVE_ACCESS_FAILED\n"
                        + "reason=Selected document provider does not map to primary shared storage\n"
                        + "note=Use a game folder in ordinary shared storage, not protected Android/data\n";
            } else {
                try {
                    report = NativeFileAccessProbe.inspect(Path.of(folder, "eboot.bin").toString());
                } catch (Throwable error) {
                    report = "RB4DX_DIRECT_NATIVE_ACCESS_FAILED\n"
                            + error.getClass().getSimpleName() + ": " + error.getMessage();
                }
            }
            String finalReport = report;
            runOnUiThread(() -> {
                nativeFileRunning = false;
                lastNativeFileReport = finalReport;
                copyNativeFileButton.setEnabled(true);
                nativeFileButton.setEnabled(Environment.isExternalStorageManager());
                nativeFileStatus.setText(finalReport.substring(0,
                        Math.min(1600, finalReport.length())));
            });
        });
    }

    private void runGlibcFileProbe() {
        if (glibcFileRunning || preparedRuntimeDirectory == null
                || !Environment.isExternalStorageManager()) return;
        String saved = preferences.getString(GAME_URI, null);
        if (saved == null) return;
        String folder = GamePreflight.rawPrimaryPath(Uri.parse(saved));
        if (folder == null) {
            glibcFileStatus.setText("Selected folder cannot be mapped to primary shared storage.");
            return;
        }
        glibcFileRunning = true;
        glibcFileButton.setEnabled(false);
        glibcFileStatus.setText("Launching Linux ARM64 glibc file-read probe (up to 12 seconds)…");
        Path runtime = preparedRuntimeDirectory;
        Path eboot = Path.of(folder, "eboot.bin");
        runtimeWorker.submit(() -> {
            GlibcGameProbe.Result result = GlibcGameProbe.inspect(this, runtime, eboot);
            runOnUiThread(() -> {
                glibcFileRunning = false;
                lastGlibcFileReport = result.report;
                copyGlibcFileButton.setEnabled(true);
                glibcFileButton.setEnabled(Environment.isExternalStorageManager());
                glibcFileStatus.setText(result.report.substring(0,
                        Math.min(1800, result.report.length())));
            });
        });
    }

    private void prepareNativeRuntime() {
        runtimeWorker.submit(() -> {
            try {
                final long[] lastProgress = {0};
                RuntimeProvisioner.Result result = RuntimeProvisioner.prepare(
                        getAssets(), getFilesDir().toPath(), (bytes, total) -> {
                            if (bytes - lastProgress[0] >= 8 * 1024 * 1024 || bytes == total) {
                                lastProgress[0] = bytes;
                                runOnUiThread(() -> runtimeStatus.setText(
                                        "Verifying ARM64 files: " + (bytes / (1024 * 1024))
                                        + " / " + (total / (1024 * 1024)) + " MiB"));
                            }
                        });
                runOnUiThread(() -> {
                    preparedRuntimeDirectory = result.directory;
                    runtimeStatus.setText("ARM64 runtime verified • " + result.version
                            + " • " + result.files + " files"
                            + (result.reused ? " (already prepared)" : " (installed)"));
                    probeStatus.setText("Ready for the FEXCore execution test.");
                    probeButton.setEnabled(true);
                    bridgeStatus.setText("Ready for the native control-channel test.");
                    bridgeButton.setEnabled(true);
                    refreshNativeAccessStatus();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    runtimeStatus.setText("Native runtime not ready: " + error.getMessage());
                    probeStatus.setText("Cannot run the test until the runtime is ready.");
                    bridgeStatus.setText("Cannot run the test until the runtime is ready.");
                    glibcFileStatus.setText("Linux game-file test requires the verified runtime.");
                    gameBootStatus.setText("Real-game startup diagnostic requires the runtime.");
                });
            }
        });
    }

    private void runGameBootProbe() {
        if (gameBootRunning || preparedRuntimeDirectory == null
                || !Environment.isExternalStorageManager()) return;
        String saved = preferences.getString(GAME_URI, null);
        if (saved == null) return;
        gameBootRunning = true;
        gameBootButton.setEnabled(false);
        exportGameBootLogButton.setEnabled(false);
        gameBootStatus.setText("Starting real shadPS4 with Rock Band 4; safety timeout 18 seconds…");
        Path runtime = preparedRuntimeDirectory;
        String title = selectedTitleId;
        Uri gameUri = Uri.parse(saved);
        runtimeWorker.submit(() -> {
            GameBootProbe.Result result = GameBootProbe.run(this, runtime, gameUri,
                    title, vulkanSurface.getHolder());
            runOnUiThread(() -> {
                gameBootRunning = false;
                lastGameBootReport = result.report;
                lastGameBootFullLog = result.fullLog;
                copyGameBootButton.setEnabled(true);
                exportGameBootLogButton.setEnabled(result.fullLog != null
                        && Files.isRegularFile(result.fullLog));
                refreshNativeAccessStatus();
                gameBootStatus.setText(result.report.substring(0,
                        Math.min(1900, result.report.length())));
            });
        });
    }

    private void runSmokeTest() {
        if (preparedRuntimeDirectory == null || smokeRunning) return;
        smokeRunning = true;
        probeButton.setEnabled(false);
        probeStatus.setText("Testing Linux loader and FEXCore (up to 50 seconds)…");
        Path runtimeDirectory = preparedRuntimeDirectory;
        runtimeWorker.submit(() -> {
            Arm64SmokeRunner.Result result = Arm64SmokeRunner.run(this, runtimeDirectory);
            runOnUiThread(() -> {
                smokeRunning = false;
                probeButton.setEnabled(true);
                lastProbeReport = result.report;
                copyProbeButton.setEnabled(true);
                probeStatus.setText((result.succeeded ? "PASS" : "NOT YET WORKING")
                        + "\n" + result.report.substring(0, Math.min(1400, result.report.length())));
            });
        });
    }

    private void copyProbeReport() {
        if (lastProbeReport == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("RB4DX ARM64 diagnostics", lastProbeReport));
        Toast.makeText(this, "Diagnostic report copied", Toast.LENGTH_SHORT).show();
    }

    private void runBridgeProbe() {
        if (preparedRuntimeDirectory == null || bridgeRunning) return;
        bridgeRunning = true;
        bridgeButton.setEnabled(false);
        bridgeStatus.setText("Opening Android Unix socket and starting the native emulator (up to 30 seconds)…");
        Path runtimeDirectory = preparedRuntimeDirectory;
        runtimeWorker.submit(() -> {
            RuntimeBridgeProbe.Result result = RuntimeBridgeProbe.run(this, runtimeDirectory);
            runOnUiThread(() -> {
                bridgeRunning = false;
                bridgeButton.setEnabled(true);
                lastBridgeReport = result.report;
                copyBridgeButton.setEnabled(true);
                bridgeStatus.setText((result.succeeded ? "CONTROL CHANNEL PASS" : "CONTROL CHANNEL NOT YET WORKING")
                        + "\n" + result.report.substring(0, Math.min(1800, result.report.length())));
            });
        });
    }

    private void copyBridgeReport() {
        if (lastBridgeReport == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("RB4DX control bridge", lastBridgeReport));
        Toast.makeText(this, "Bridge report copied", Toast.LENGTH_SHORT).show();
    }

    private void runGamePreflight() {
        String saved = preferences.getString(GAME_URI, null);
        if (saved == null || gamePreflightRunning) return;
        gamePreflightRunning = true;
        gamePreflightButton.setEnabled(false);
        gamePreflightStatus.setText("Reading Rock Band 4 metadata through Android's folder permission…");
        Uri uri = Uri.parse(saved);
        String title = selectedTitleId;
        importer.submit(() -> {
            GamePreflight.Result result = GamePreflight.inspect(this, uri, title);
            runOnUiThread(() -> {
                gamePreflightRunning = false;
                gamePreflightButton.setEnabled(true);
                lastGamePreflightReport = result.report;
                copyGamePreflightButton.setEnabled(true);
                gamePreflightStatus.setText((result.succeeded ? "GAME FILES VERIFIED" : "GAME FILES NEED ATTENTION")
                        + "\n" + result.report.substring(0, Math.min(1800, result.report.length())));
            });
        });
    }

    private void copyGamePreflightReport() {
        copyReport("RB4DX game inspection", lastGamePreflightReport);
    }

    private void runVulkanProbe() {
        if (vulkanRunning || vulkanFrameRunning || vulkanSurface == null
                || !vulkanSurface.getHolder().getSurface().isValid()) return;
        vulkanRunning = true;
        vulkanButton.setEnabled(false);
        vulkanFrameButton.setEnabled(false);
        vulkanStatus.setText("Querying Android Vulkan GPU and display surface…");
        android.view.Surface surface = vulkanSurface.getHolder().getSurface();
        runtimeWorker.submit(() -> {
            String report;
            try {
                report = NativeVulkanSurfaceProbe.run(surface);
            } catch (Throwable error) {
                report = "RB4DX_VULKAN_SURFACE_FAILED\n"
                        + error.getClass().getSimpleName() + ": " + error.getMessage();
            }
            String finalReport = report;
            runOnUiThread(() -> {
                vulkanRunning = false;
                vulkanButton.setEnabled(vulkanSurface.getHolder().getSurface().isValid());
                vulkanFrameButton.setEnabled(vulkanSurface.getHolder().getSurface().isValid());
                lastVulkanReport = finalReport;
                copyVulkanButton.setEnabled(true);
                vulkanStatus.setText(finalReport.substring(0,
                        Math.min(1800, finalReport.length())));
            });
        });
    }

    private void copyVulkanReport() {
        copyReport("RB4DX Vulkan surface", lastVulkanReport);
    }

    private void runVulkanFrameProbe() {
        if (vulkanFrameRunning || vulkanRunning || vulkanSurface == null
                || !vulkanSurface.getHolder().getSurface().isValid()) return;
        vulkanFrameRunning = true;
        vulkanButton.setEnabled(false);
        vulkanFrameButton.setEnabled(false);
        vulkanFrameStatus.setText("Creating Vulkan swapchain and presenting one blue GPU frame…");
        android.view.Surface surface = vulkanSurface.getHolder().getSurface();
        runtimeWorker.submit(() -> {
            String report;
            try {
                report = NativeVulkanFrameProbe.presentFrame(surface);
            } catch (Throwable error) {
                report = "RB4DX_VULKAN_FRAME_FAILED\n"
                        + error.getClass().getSimpleName() + ": " + error.getMessage();
            }
            String finalReport = report;
            runOnUiThread(() -> {
                vulkanFrameRunning = false;
                boolean available = vulkanSurface.getHolder().getSurface().isValid();
                vulkanButton.setEnabled(available);
                vulkanFrameButton.setEnabled(available);
                lastVulkanFrameReport = finalReport;
                copyVulkanFrameButton.setEnabled(true);
                vulkanFrameStatus.setText(finalReport.substring(0,
                        Math.min(1800, finalReport.length())));
            });
        });
    }

    private void copyReport(String label, String report) {
        if (report == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, report));
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show();
    }

    private void exportGameBootLog() {
        if (lastGameBootFullLog == null || !Files.isRegularFile(lastGameBootFullLog)) {
            Toast.makeText(this, "No complete startup log to export yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "RB4DX-Startup-v0.12.9.log");
        try {
            startActivityForResult(intent, EXPORT_GAME_LOG);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "No document picker available", Toast.LENGTH_LONG).show();
        }
    }

    private void chooseGameFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_GAME);
    }

    private void chooseDeluxeArchive() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_DELUXE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == EXPORT_GAME_LOG) {
            Path logPath = lastGameBootFullLog;
            if (logPath == null) return;
            runtimeWorker.submit(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null) throw new IOException("Document provider returned no stream");
                    long copied = StartupLogStore.copyTo(logPath, output);
                    runOnUiThread(() -> Toast.makeText(this,
                            "Startup log saved (" + copied + " bytes)", Toast.LENGTH_LONG).show());
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Log export failed: " + error.getClass().getSimpleName(),
                            Toast.LENGTH_LONG).show());
                }
            });
            return;
        }
        if (requestCode == PICK_GAME) {
            try {
                if (!containsEboot(uri)) {
                    gameStatus.setText("This folder does not contain eboot.bin. Select the extracted Rock Band 4 game directory.");
                    return;
                }
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                preferences.edit().putString(GAME_URI, uri.toString()).apply();
                refreshStatus();
            } catch (Exception error) {
                gameStatus.setText("Game folder could not be opened: " + error.getMessage());
            }
        } else if (requestCode == PICK_DELUXE) {
            runDeluxeImport(uri);
        }
    }

    private boolean turnipDriverExists() {
        return Files.isRegularFile(Path.of(getApplicationInfo().nativeLibraryDir,
                "libvulkan_freedreno.so"));
    }

    private void refreshTurnipStatus() {
        if (turnipStatus == null) return;
        if (!turnipDriverExists()) {
            turnipStatus.setText("Bundled ARM64 Turnip library missing. Use system Adreno.");
        } else {
            boolean enabled = preferences.getBoolean(GPU_TURNIP_ENABLED, true);
            turnipStatus.setText((enabled ? "Selected: Mesa Turnip" : "Selected: System Adreno")
                    + " | Bundled ARM64 Turnip library ready");
        }
    }

    private void runDeluxeImport(Uri uri) {
        if (busy) return;
        busy = true;
        importButton.setEnabled(false);
        String titleId = selectedTitleId;
        detailStatus.setText("Importing Deluxe… please leave this screen open.");

        importer.submit(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Document cannot be read");
                DeluxeInstaller.Result result = DeluxeInstaller.install(input,
                        getFilesDir().toPath(), titleId, (count, bytes) -> {
                            runOnUiThread(() -> detailStatus.setText(
                                    "Unpacked " + count + " files • " + (bytes / (1024 * 1024)) + " MiB"));
                        });
                runOnUiThread(() -> {
                    busy = false;
                    importButton.setEnabled(true);
                    detailStatus.setText("Import complete: " + result.files + " files ("
                            + (result.bytes / (1024 * 1024)) + " MiB). Plugin staged.");
                    refreshStatus();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    busy = false;
                    importButton.setEnabled(true);
                    detailStatus.setText("Import failed: " + error.getMessage());
                    refreshStatus();
                });
            }
        });
    }

    private boolean containsEboot(Uri tree) {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        try (Cursor cursor = getContentResolver().query(children,
                new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (cursor == null) return false;
            while (cursor.moveToNext()) {
                if ("eboot.bin".equalsIgnoreCase(cursor.getString(0))) return true;
            }
        }
        return false;
    }

    private void refreshStatus() {
        String uri = preferences.getString(GAME_URI, null);
        gameStatus.setText(uri == null
                ? "No game folder selected"
                : "Game folder selected (read access saved)");
        if (gamePreflightButton != null) {
            gamePreflightButton.setEnabled(uri != null && !gamePreflightRunning);
            if (uri == null) gamePreflightStatus.setText("Select your Rock Band 4 folder above first.");
        }
        refreshNativeAccessStatus();
        Path user = getFilesDir().toPath().resolve("shadps4/user");
        Path data = user.resolve("data").resolve(selectedTitleId).resolve("GoldHEN/RB4DX");
        Path plugin = user.resolve("custom_modules").resolve(selectedTitleId)
                .resolve("RB4DX-Plugin.prx");
        modStatus.setText(Files.isDirectory(data) && Files.isRegularFile(plugin)
                ? "Deluxe assets and plugin staged for " + selectedTitleId
                : "Deluxe not imported for " + selectedTitleId);
    }

    private RadioButton radio(String title) {
        RadioButton radio = new RadioButton(this);
        radio.setId(View.generateViewId());
        radio.setText(title);
        radio.setTextColor(FG);
        radio.setTextSize(15);
        radio.setPadding(0, dp(6), 0, dp(6));
        return radio;
    }

    private Button button(LinearLayout parent, String label) {
        Button result = new Button(this);
        result.setAllCaps(false);
        result.setText(label);
        result.setTextColor(Color.WHITE);
        result.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.topMargin = dp(10);
        parent.addView(result, params);
        return result;
    }

    private TextView addText(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(3), 0, dp(3));
        parent.addView(view);
        return view;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The executor may still own a ZIP stream; no cancellation or data
        // deletion is attempted from Activity teardown.
    }
}
