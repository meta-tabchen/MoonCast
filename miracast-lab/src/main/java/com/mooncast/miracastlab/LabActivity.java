// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.miracastlab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Co-installable experiment. Permission and external settings flows require a deliberate click. */
public final class LabActivity extends Activity {
    private static final int REQUEST_WIFI = 10, SAVE_REPORT = 11;
    private CapabilityProbe probe;
    private TextView report, summary;
    private String reportToSave;
    private boolean askingPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(40), dp(24), dp(40), dp(28)); root.setBackgroundColor(Color.rgb(11, 21, 27));
        scroll.addView(root); setContentView(scroll);
        addText(root, getString(R.string.title), 28, 0xFFA5E9D1, true);
        addText(root, getString(R.string.intro), 17, 0xFFD7E4E8, false);
        summary = addText(root, "", 18, 0xFFFFD494, false);
        LinearLayout first = row(root);
        Button run = button(first, R.string.run_probe, this::requestProbe);
        button(first, R.string.refresh, () -> probe.refresh());
        LinearLayout second = row(root);
        button(second, R.string.open_cast, () -> new AlertDialog.Builder(this).setTitle(R.string.cast_confirm_title).setMessage(R.string.cast_note).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.open_label, (d, w) -> openSystem(Settings.ACTION_CAST_SETTINGS)).show());
        button(second, R.string.open_wifi, () -> openSystem(Settings.ACTION_WIFI_SETTINGS));
        LinearLayout third = row(root);
        button(third, R.string.copy, this::copyReport);
        button(third, R.string.save, this::saveReport);
        addText(root, getString(R.string.details), 19, 0xFFA5E9D1, true);
        report = addText(root, "", 15, 0xFFE4ECEF, false); report.setTypeface(Typeface.MONOSPACE); report.setTextIsSelectable(true); report.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable(); background.setColor(0xFF14252E); background.setCornerRadius(dp(10)); report.setBackground(background);
        probe = new CapabilityProbe(this, () -> { if (probe != null) { report.setText(probe.report()); summary.setText(probe.summary()); } });
        probe.refresh(); run.requestFocus();
    }
    private void requestProbe() {
        if (askingPermission) return;
        if (Build.VERSION.SDK_INT < 27) { probe.refresh(); probe.runP2pReads(); return; }
        if (probe.hasRuntimePermission()) { probe.refresh(); probe.runP2pReads(); return; }
        new AlertDialog.Builder(this).setTitle(R.string.permission_title)
                .setMessage(Build.VERSION.SDK_INT >= 33 ? R.string.permission_nearby : R.string.permission_location)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.continue_label, (d, w) -> {
                    askingPermission = true;
                    try { requestPermissions(Build.VERSION.SDK_INT >= 33 ? new String[]{Manifest.permission.NEARBY_WIFI_DEVICES} : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_WIFI); }
                    catch (RuntimeException error) { askingPermission = false; probe.put("PERMISSION_REQUEST_ERROR", CapabilityProbe.error(error)); }
                }).show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != REQUEST_WIFI) return;
        askingPermission = false; probe.refresh();
        if (probe.hasRuntimePermission()) probe.runP2pReads();
        else { probe.put("PERMISSION_REQUEST", getString(R.string.permission_denied)); toast(R.string.permission_denied); }
    }
    private void openSystem(String action) {
        try {
            Intent intent = probe.systemIntent(action);
            if (intent == null) { probe.put("SETTINGS_OPEN", action + ": NO_ACCESSIBLE_SYSTEM_HANDLER"); toast(R.string.settings_unavailable); return; }
            startActivity(intent); probe.put("SETTINGS_OPEN", "Opened " + intent.getComponent().flattenToShortString() + "; receiver capability unverified");
        } catch (RuntimeException error) { probe.put("SETTINGS_OPEN", CapabilityProbe.error(error)); toast(R.string.settings_unavailable); }
    }
    private void copyReport() {
        try { ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); if (clipboard == null) throw new IllegalStateException("Clipboard service unavailable"); clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), probe.report())); toast(R.string.copied); }
        catch (RuntimeException error) { probe.put("COPY_ERROR", CapabilityProbe.error(error)); }
    }
    private void saveReport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE, "MoonCast-Miracast-device-report.txt");
        try { if (intent.resolveActivity(getPackageManager()) == null) { toast(R.string.save_unavailable); return; } reportToSave = probe.report(); startActivityForResult(intent, SAVE_REPORT); }
        catch (RuntimeException error) { reportToSave = null; probe.put("SAVE_ERROR", CapabilityProbe.error(error)); toast(R.string.save_unavailable); }
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE_REPORT) return;
        String snapshot = reportToSave; reportToSave = null;
        if (result != RESULT_OK || data == null || data.getData() == null || snapshot == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (out == null) throw new java.io.IOException("Selected document returned no output stream");
            out.write(snapshot.getBytes(StandardCharsets.UTF_8)); toast(R.string.saved);
        } catch (Exception error) { probe.put("SAVE_ERROR", CapabilityProbe.error(error)); toast(R.string.save_failed); }
    }
    @Override public void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("reportToSave", reportToSave); }
    @Override public void onRestoreInstanceState(Bundle state) { super.onRestoreInstanceState(state); reportToSave = state.getString("reportToSave"); }
    @Override public void onStop() { if (probe != null) probe.cancel(); super.onStop(); }
    private LinearLayout row(LinearLayout parent) { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); parent.addView(row, new LinearLayout.LayoutParams(-1, -2)); return row; }
    private Button button(LinearLayout parent, int label, Runnable action) { Button b = new Button(this); b.setText(label); b.setTextSize(16); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setOnClickListener(v -> action.run()); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(0, dp(3), dp(10), dp(3)); parent.addView(b, p); return b; }
    private TextView addText(LinearLayout parent, String text, int sp, int color, boolean bold) { TextView view = new TextView(this); view.setText(text); view.setTextSize(sp); view.setTextColor(color); view.setPadding(0, dp(5), 0, dp(9)); if (bold) view.setTypeface(null, Typeface.BOLD); parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
