package com.mooncast.host;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** The same My TV dialog serves casting and APK transfer. */
final class MyTvPicker extends Dialog {
    interface Listener { void selected(MyTvDevice device); }
    private final Activity activity;
    private final boolean requireApk;
    private final Listener listener;
    private MyTvDiscovery discovery;
    private LinearLayout rows, manual;
    private TextView status;
    private EditText address;
    private Button connect;
    private MyTvDevice remembered;
    private List<MyTvDevice> devices = new ArrayList<>();
    private boolean probing;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchFinished = () -> { if (isShowing() && !probing) status.setText(devices.isEmpty() ? R.string.my_tv_empty : R.string.my_tv_choose); };
    MyTvPicker(Activity activity, boolean requireApk, Listener listener) {
        super(activity); this.activity = activity; this.requireApk = requireApk; this.listener = listener;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout page = new LinearLayout(activity); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(22), dp(22), dp(16)); page.setBackground(PhoneVisuals.surface(activity,PhoneVisuals.CANVAS,28));
        View handle=new View(activity);handle.setBackground(PhoneVisuals.surface(activity,0xffd1d9e5,4));LinearLayout.LayoutParams handleSize=new LinearLayout.LayoutParams(dp(44),dp(5));handleSize.gravity=android.view.Gravity.CENTER_HORIZONTAL;handleSize.bottomMargin=dp(18);page.addView(handle,handleSize);
        TextView title = text(R.string.my_tv_title, 25); title.setTypeface(Typeface.DEFAULT_BOLD); page.addView(title);
        page.addView(text(requireApk ? R.string.my_tv_apk_hint : R.string.my_tv_airplay_hint, 14));
        status = text(R.string.my_tv_searching, 13); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); page.addView(status);
        ScrollView scroll = new ScrollView(activity);
        rows = new LinearLayout(activity); rows.setOrientation(LinearLayout.VERTICAL); rows.setTag("my_tv_rows"); scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(dp(250),activity.getResources().getDisplayMetrics().heightPixels/3)));
        Button refresh = button(R.string.my_tv_refresh); refresh.setTag("my_tv_refresh"); page.addView(refresh);
        refresh.setOnClickListener(v -> restartDiscovery());
        Button manualToggle = button(R.string.my_tv_manual); manualToggle.setTag("my_tv_manual"); page.addView(manualToggle);
        manual = new LinearLayout(activity); manual.setOrientation(LinearLayout.VERTICAL); manual.setVisibility(View.GONE); page.addView(manual);
        address = new EditText(activity); address.setSingleLine(true); address.setHint(R.string.my_tv_ip_hint);
        address.setInputType(InputType.TYPE_CLASS_PHONE); address.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        address.setContentDescription(activity.getString(R.string.my_tv_ip_hint)); address.setTag("my_tv_ip"); manual.addView(address);
        remembered = MyTvStore.last(activity); if (remembered != null) address.setText(remembered.host);
        manual.addView(text(R.string.my_tv_manual_explain, 12));
        connect = button(R.string.my_tv_check); connect.setTag("my_tv_check"); manual.addView(connect);
        manualToggle.setOnClickListener(v -> manual.setVisibility(manual.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        connect.setOnClickListener(v -> probeAddress());
        Button close = button(android.R.string.cancel); close.setOnClickListener(v -> dismiss()); page.addView(close);
        ScrollView container = new ScrollView(activity); container.setFillViewport(false); container.addView(page);
        setContentView(container);
        Window window = getWindow(); if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels, dp(560)), -2);
            window.setGravity(android.view.Gravity.BOTTOM);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.36f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }
    @Override protected void onStart() {
        super.onStart();
        remembered = MyTvStore.last(activity); probing = false; connect.setEnabled(true); address.setError(null); manual.setVisibility(View.GONE);
        address.setText(remembered == null ? "" : remembered.host);
        restartDiscovery();
    }
    private void restartDiscovery() {
        if (discovery != null) discovery.close();
        main.removeCallbacks(searchFinished);
        devices = new ArrayList<>(); probing = false; connect.setEnabled(true); status.setText(R.string.my_tv_searching);
        discovery = new MyTvDiscovery(activity, new MyTvDiscovery.Listener() {
            public void changed(List<MyTvDevice> values) { devices = values; render(); }
            public void failed() { if (!probing) status.setText(R.string.my_tv_discovery_limited); }
        });
        discovery.start(); render(); main.postDelayed(searchFinished, 10000);
        if (remembered != null) discovery.probe(remembered.host, false, value -> { if (value != null) render(); });
    }
    @Override protected void onStop() {
        if (discovery != null) { discovery.close(); discovery = null; }
        main.removeCallbacks(searchFinished); probing = false; super.onStop();
    }
    private void probeAddress() {
        String host = address.getText().toString().trim();
        if (requireApk ? ApkEndpoint.parse(host + ":" + MyTvDevice.APK_PORT) == null : !AirPlayEndpoint.localNumericHost(host)) { address.setError(activity.getString(R.string.my_tv_bad_ip)); return; }
        if (probing || discovery == null) return;
        address.setError(null); probing = true; connect.setEnabled(false); status.setText(R.string.my_tv_checking);
        discovery.probe(host, !requireApk, device -> {
            probing = false; connect.setEnabled(true);
            status.setText(device == null ? R.string.my_tv_apk_unavailable : device.manualAirplay ? R.string.my_tv_unverified_hint : R.string.my_tv_choose);
            if (device != null) { MyTvStore.remember(activity, device); remembered = MyTvStore.last(activity); render(); }
        });
    }
    private void render() {
        if (rows == null) return;
        rows.removeAllViews();
        ArrayList<MyTvDevice> visible = new ArrayList<>(devices);
        if (remembered != null) {
            boolean seen = false;
            // An address may have been reassigned; prefer fresh facts and never show a stale duplicate.
            for (MyTvDevice device : visible) if (remembered.host.equals(device.host)) { seen = true; break; }
            if (!seen) visible.add(0, remembered);
        }
        if (visible.isEmpty()) rows.addView(text(R.string.my_tv_empty, 14));
        for (MyTvDevice device : visible) {
            boolean enabled = requireApk ? device.supportsApk() : device.supportsAirplay();
            LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable background = new GradientDrawable(); background.setColor(remembered != null && remembered.host.equals(device.host)?0xffeaf3ff:0xffffffff); background.setCornerRadius(dp(14)); background.setStroke(dp(1), 0xffe3e9f3); row.setBackground(background);
            LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(-1, -2); margins.setMargins(0, dp(6), 0, dp(6)); rows.addView(row, margins);
            android.widget.ImageView icon=PhoneVisuals.icon(activity,R.drawable.ic_tv,26,PhoneVisuals.INK);row.addView(icon);
            TextView label = text(0, 17); label.setText(device.name); label.setTypeface(Typeface.DEFAULT_BOLD); row.addView(label);
            TextView detail = text(0, 12); detail.setText(remembered != null && remembered.host.equals(device.host) ? activity.getString(R.string.my_tv_last_address, device.host) : device.host); row.addView(detail);
            ArrayList<String> tags = new ArrayList<>();
            if (device.supportsAirplay()) tags.add(activity.getString(device.manualAirplay ? R.string.my_tv_airplay_manual : R.string.my_tv_airplay));
            if (device.supportsApk()) tags.add(activity.getString(R.string.my_tv_apk));
            TextView capabilities = text(0, 13); capabilities.setText(android.text.TextUtils.join(" · ", tags)); capabilities.setTextColor(PhoneVisuals.BLUE); row.addView(capabilities);
            if (!enabled) row.addView(text(requireApk ? R.string.my_tv_apk_unavailable : R.string.my_tv_airplay_unavailable, 12));
            row.setEnabled(enabled); row.setAlpha(enabled ? 1f : .65f); row.setFocusable(enabled); row.setClickable(enabled);
            if (enabled) row.setOnClickListener(v -> { MyTvStore.remember(activity, device); dismiss(); listener.selected(device); });
            row.setContentDescription(device.name + ", " + capabilities.getText());
        }
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private TextView text(int resource, int size) { TextView v = new TextView(activity); if (resource != 0) v.setText(resource); v.setTextSize(size); v.setTextColor(PhoneVisuals.INK); v.setPadding(0, dp(3), 0, dp(3)); return v; }
    private Button button(int resource) { Button v = new Button(activity); v.setText(resource); PhoneVisuals.button(v,resource==R.string.my_tv_check);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.topMargin=dp(8);v.setLayoutParams(layout); return v; }
}
