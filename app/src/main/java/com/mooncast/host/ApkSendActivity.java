package com.mooncast.host;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** Explicit auxiliary file transfer. This Activity never controls casting or launches an installer. */
public final class ApkSendActivity extends Activity {
    private static final int PICK_APK = 81;
    private static final int INK = PhoneVisuals.INK, MUTED = PhoneVisuals.MUTED, BLUE = PhoneVisuals.BLUE;
    private ApkSendController controller;
    private MyTvPicker picker;
    private Uri chosenDocument;
    private ApkStager.Selection selection;
    private ApkEndpoint endpoint;
    private String receiverName = "";
    private boolean resumed, awaitingDocument, busy, sending, destroyed;
    private Uri pendingDocument;
    private TextView fileDetail, fileSummary, tvDetail, status, percentage;
    private Button choose, chooseTv, send, cancel;
    private ProgressBar progress;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Restage the selected document after rotation; never resume an upload automatically.
        if (state != null) {
            awaitingDocument = state.getBoolean("awaitingDocument");
            String document = state.getString("document");
            if (document != null) pendingDocument = chosenDocument = Uri.parse(document);
            endpoint = ApkEndpoint.parse(state.getString("endpoint"));
            receiverName = state.getString("receiverName", "");
        }
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(PhoneVisuals.CANVAS); getWindow().setNavigationBarColor(PhoneVisuals.CANVAS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(PhoneVisuals.CANVAS);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22), dp(18), dp(22), dp(28)); scroll.addView(page);
        LinearLayout header=new LinearLayout(this);header.setGravity(android.view.Gravity.CENTER_VERTICAL);page.addView(header);
        Button back = button(R.string.apk_back, false);back.setText("");back.setContentDescription(getString(R.string.apk_back));android.graphics.drawable.Drawable backIcon=getDrawable(R.drawable.ic_back);backIcon.setBounds(0,0,dp(24),dp(24));back.setCompoundDrawables(backIcon,null,null,null);back.setBackgroundColor(Color.TRANSPARENT); back.setOnClickListener(v -> finish());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(52)));
        TextView title = text(getString(R.string.apk_title), 27, INK); title.setTypeface(Typeface.DEFAULT_BOLD); title.setGravity(android.view.Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));View spacer=new View(this);header.addView(spacer,new LinearLayout.LayoutParams(dp(48),dp(1)));
        page.addView(text(getString(R.string.apk_intro), 14, MUTED));

        LinearLayout file = card(page); file.addView(PhoneVisuals.icon(this,R.drawable.ic_package,36,BLUE));file.addView(text(getString(R.string.apk_file_heading), 18, INK));
        fileSummary = text(getString(R.string.apk_no_file), 14, INK); file.addView(fileSummary);
        Button details = button(R.string.apk_details, false);
        details.setOnClickListener(v -> fileDetail.setVisibility(fileDetail.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)); file.addView(details);
        fileDetail = text(getString(R.string.apk_no_file), 13, MUTED); fileDetail.setTextIsSelectable(true); fileDetail.setTag("apk_file_detail"); file.addView(fileDetail); fileDetail.setVisibility(View.GONE);
        choose = button(R.string.apk_choose, true); choose.setTag("apk_choose"); choose.setOnClickListener(v -> chooseDocument()); file.addView(choose);

        LinearLayout target = card(page); target.addView(PhoneVisuals.icon(this,R.drawable.ic_tv,32,INK));target.addView(text(getString(R.string.apk_tv_heading), 18, INK));
        target.addView(text(getString(R.string.apk_tv_hint), 13, MUTED));
        tvDetail = text(getString(R.string.apk_no_tv), 14, BLUE); tvDetail.setTag("apk_tv_detail"); target.addView(tvDetail);
        chooseTv = button(R.string.apk_choose_tv, false); chooseTv.setTag("apk_choose_tv");
        chooseTv.setOnClickListener(v -> showTvPicker()); target.addView(chooseTv);
        picker = new MyTvPicker(this, true, device -> {
            ApkEndpoint value = ApkEndpoint.parse(device.host + ":" + device.apkPort);
            if (value != null) selectTv(value, device.name);
        });
        if (endpoint != null) tvDetail.setText(getString(R.string.apk_selected_tv, receiverName, endpoint.display()));

        LinearLayout warning = card(page); warning.setBackground(background(0xfffff3db));
        warning.addView(text(getString(R.string.apk_lan_warning), 14, 0xff755314));
        warning.addView(text(getString(R.string.apk_validation_limit), 12, 0xff755314));

        LinearLayout actions = card(page);actions.setTag("apk_progress_card");
        percentage=text("",42,BLUE);percentage.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));percentage.setTag("apk_percentage");percentage.setVisibility(View.GONE);actions.addView(percentage);
        send = button(R.string.apk_send, true); send.setTag("apk_send"); send.setOnClickListener(v -> send()); actions.addView(send);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe4eaf3));progress.setMinimumHeight(dp(10)); progress.setVisibility(View.GONE); actions.addView(progress);
        status = text(getString(state == null ? R.string.apk_idle : R.string.apk_recreated), 13, MUTED);
        status.setTag("apk_status"); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); actions.addView(status);
        cancel = button(android.R.string.cancel, false); cancel.setTag("apk_cancel"); cancel.setOnClickListener(v -> reset(R.string.apk_canceled)); actions.addView(cancel);
        page.removeView(warning);page.addView(warning);
        setContentView(scroll);
        controller = new ApkSendController(this, new ApkSendController.Listener() {
            public void preparing(long bytes) {
                busy = true; sending = false; progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true);
                status.setText(getString(R.string.apk_preparing, size(bytes))); fileSummary.setText(status.getText()); updateControls();
            }
            public void ready(ApkStager.Selection value) {
                selection = value; busy = false; progress.setVisibility(View.GONE);
                fileSummary.setText(getString(R.string.apk_summary, value.label, value.version, size(value.bytes)));
                fileDetail.setText(getString(R.string.apk_metadata, value.label, value.packageName, value.version,
                    ApkStager.displayText(value.name, 240), size(value.bytes), value.sha256, value.signers));
                status.setText(R.string.apk_ready); updateControls();
                if (endpoint == null && resumed) showTvPicker();
            }
            public void sending(long sent, long total) {
                busy = true; sending = true; progress.setVisibility(View.VISIBLE); progress.setIndeterminate(sent == 0);
                if (sent > 0) progress.setProgress((int) Math.min(1000, sent * 1000 / Math.max(1, total)));
                percentage.setText(sent==0?"":String.format(Locale.getDefault(),"%d%%",Math.min(100,sent*100/Math.max(1,total))));
                status.setText(sent == 0 ? getString(R.string.apk_pairing) : getString(R.string.apk_sending, size(sent), size(total))); updateControls();
            }
            public void received() {
                clearFile(); busy = false; sending = false; progress.setVisibility(View.GONE);
                status.setText(R.string.apk_received); choose.setText(R.string.apk_choose_another); updateControls();
            }
            public void failed(boolean network, String reason) {
                clearFile(); busy = false; sending = false; progress.setVisibility(View.GONE);
                showFailure(network, reason);
                choose.setText(R.string.apk_retry_choose); updateControls();
            }
        });
        updateControls();
    }
    private void chooseDocument() {
        controller.cancel(); clearFile(); busy = false; sending = false;
        progress.setVisibility(View.GONE); pendingDocument = null; awaitingDocument = true;
        // MIME filters vary by provider. The copied document's real name/content is checked separately.
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, PICK_APK); }
        catch (ActivityNotFoundException | SecurityException ignored) { awaitingDocument = false; status.setText(R.string.apk_picker_missing); }
        updateControls();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_APK || !awaitingDocument) return;
        awaitingDocument = false;
        if (result != RESULT_OK) { status.setText(R.string.apk_idle); updateControls(); return; }
        Uri uri = selectedDocument(data);
        if (uri == null || !"content".equals(uri.getScheme())) {
            showFailure(false, getString(R.string.apk_no_document_result)); updateControls(); return;
        }
        chosenDocument = uri; pendingDocument = uri;
        if (resumed) preparePending();
    }
    static Uri selectedDocument(Intent data) {
        if (data == null) return null;
        if (data.getData() != null) return data.getData();
        android.content.ClipData clips = data.getClipData();
        return clips != null && clips.getItemCount() == 1 ? clips.getItemAt(0).getUri() : null;
    }
    private void showFailure(boolean network, String reason) {
        String detail = getString(network ? R.string.apk_send_failed_detail : R.string.apk_prepare_failed_detail, reason);
        status.setText(detail); fileSummary.setText(detail);
        if (resumed && !isFinishing() && !destroyed)
            new android.app.AlertDialog.Builder(this).setTitle(R.string.apk_failed_title)
                .setMessage(detail).setPositiveButton(android.R.string.ok, null).show();
    }
    private void preparePending() {
        if (pendingDocument == null || destroyed) return;
        Uri uri = pendingDocument; pendingDocument = null; controller.prepare(uri);
    }
    private void send() {
        if (busy || selection == null || endpoint == null) return;
        picker.dismiss(); controller.send(endpoint);
    }
    private void showTvPicker() { if (!busy && selection != null && resumed && !destroyed) picker.show(); }
    private void selectTv(ApkEndpoint value, String name) {
        if (busy) return;
        endpoint = value; receiverName = ApkStager.displayText(name, 80);
        tvDetail.setText(getString(R.string.apk_selected_tv, receiverName, value.display())); updateControls();
    }
    private void clearFile() { selection = null; chosenDocument = null; fileDetail.setText(R.string.apk_no_file); fileSummary.setText(R.string.apk_no_file); }
    private void reset(int message) {
        controller.cancel(); clearFile(); busy = false; sending = false; pendingDocument = null;
        progress.setVisibility(View.GONE); status.setText(message); choose.setText(R.string.apk_retry_choose);
        if (picker != null) picker.dismiss(); updateControls();
    }
    private void updateControls() {
        if (send == null) return;
        percentage.setVisibility(sending?View.VISIBLE:View.GONE);
        send.setVisibility(busy?View.GONE:View.VISIBLE);
        choose.setEnabled(!busy && !awaitingDocument);
        send.setEnabled(!busy && selection != null && endpoint != null);
        chooseTv.setEnabled(!busy && selection != null);
        cancel.setEnabled(busy || selection != null);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; preparePending(); }
    @Override protected void onPause() { resumed = false; if (picker != null) picker.dismiss(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("awaitingDocument", awaitingDocument);
        if (!sending && chosenDocument != null) out.putString("document", chosenDocument.toString());
        if (endpoint != null) out.putString("endpoint", endpoint.display());
        out.putString("receiverName", receiverName);
    }
    @Override protected void onStop() {
        // Cancel network work and clear the bounded spool when leaving. Rotation only restages,
        // using the still-granted document URI in instance state, and requires Send again.
        if (!awaitingDocument) reset(R.string.apk_background_canceled);
        super.onStop();
    }
    @Override protected void onDestroy() {
        destroyed = true; if (picker != null) picker.dismiss(); pendingDocument = null;
        if (controller != null) controller.close(); super.onDestroy();
    }
    private TextView text(String value, int sp, int color) { TextView text = new TextView(this); text.setText(value); text.setTextSize(sp); text.setTextColor(color); text.setPadding(0, dp(7), 0, dp(7)); text.setSaveEnabled(false); return text; }
    private Button button(int label, boolean primary) {
        Button button = new Button(this); if (label != 0) button.setText(label);PhoneVisuals.button(button,primary);
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.topMargin=dp(8);button.setLayoutParams(layout);return button;
    }
    private LinearLayout card(LinearLayout page) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(12), dp(18), dp(16)); box.setBackground(background(Color.WHITE));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.topMargin = dp(16); page.addView(box, layout); return box;
    }
    private GradientDrawable background(int color) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(20)); return drawable; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private String size(long bytes) { return String.format(Locale.getDefault(), "%.2f MiB", bytes / (1024.0 * 1024.0)); }
}
