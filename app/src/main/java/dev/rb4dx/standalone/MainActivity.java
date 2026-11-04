// SPDX-License-Identifier: GPL-2.0-or-later
package dev.rb4dx.standalone;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal game-specific UI; native emulation is intentionally not faked. */
public final class MainActivity extends Activity {
    private static final int PICK_GAME = 100;
    private static final int PICK_DELUXE = 101;
    private static final String PREFS = "rb4dx";
    private static final String GAME_URI = "game_tree_uri";
    private static final String TITLE_ID = "title_id";
    private static final int BG = Color.rgb(15, 16, 24);
    private static final int FG = Color.rgb(239, 240, 247);
    private static final int MUTED = Color.rgb(174, 179, 197);
    private static final int ACCENT = Color.rgb(87, 115, 235);

    private final ExecutorService importer = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private TextView gameStatus;
    private TextView modStatus;
    private TextView detailStatus;
    private Button importButton;
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

        addText(body, "3. Import Rock Band 4 Deluxe", 19, FG, true);
        addText(body, "Select your existing RB4DX-PS4.zip. The app unpacks it into its own private storage.",
                14, MUTED, false);
        modStatus = addText(body, "Not imported", 14, MUTED, false);
        importButton = button(body, "Import Deluxe ZIP");
        importButton.setOnClickListener(v -> chooseDeluxeArchive());
        detailStatus = addText(body, "", 13, MUTED, false);
        addGap(body, 26);

        addText(body, "4. Launch", 19, FG, true);
        Button play = button(body, "PLAY • emulator core not connected");
        play.setEnabled(false);
        addText(body,
                "This first build imports the game/mod configuration only. A separate native shadPS4/FEX backend is still required before Play can be enabled.",
                14, MUTED, false);
        addGap(body, 16);
        addText(body,
                "Independent package dev.rb4dx.standalone • Does not modify Bachata S4",
                12, MUTED, false);

        setContentView(scroll);
        refreshStatus();
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
