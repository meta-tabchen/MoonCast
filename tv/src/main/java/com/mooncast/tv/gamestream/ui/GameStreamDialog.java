// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.mooncast.tv.R;
import com.mooncast.tv.gamestream.hosts.DiscoveredHost;
import com.mooncast.tv.gamestream.hosts.HostEndpoint;
import com.mooncast.tv.gamestream.hosts.HostRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Main-thread-only, D-pad accessible host picker and explicit first-pairing consent.
 * The owner performs discovery, persistence, identity generation, trust checks and networking.
 * No constructor or display operation creates a pairing identity or probes a host.
 */
public final class GameStreamDialog {
    public interface Listener {
        /** The optional saved record is a UI hint. Revalidate endpoint and trust before use. */
        void onHostSelected(HostEndpoint endpoint, String displayName, HostRecord savedHost);
        /** Only emitted from the user's explicit Pair button, never from discovery/selection. */
        void onPairConfirmed(HostEndpoint endpoint, String displayName);
        /** Only emitted after confirming local removal. This never means remote revocation. */
        void onForgetHost(HostRecord host);
        void onQualitySelected(Quality quality);
        /** Cancel pending inspect/pair/connect work, but do not stop an established stream. */
        void onDismissed();
        default void onRefreshRequested() { }
    }

    /** Actual requested session configuration, not postprocessing or adaptive media limits. */
    public enum Quality {
        HD_720_60(1280, 720, 60, 15000, R.string.gamestream_quality_720_60),
        FULL_HD_1080_60(1920, 1080, 60, 30000, R.string.gamestream_quality_1080_60),
        FULL_HD_1080_30(1920, 1080, 30, 20000, R.string.gamestream_quality_1080_30);
        public final int width, height, fps, bitrateKbps;
        public final int labelRes;
        Quality(int width, int height, int fps, int bitrateKbps, int labelRes) {
            this.width = width; this.height = height; this.fps = fps;
            this.bitrateKbps = bitrateKbps; this.labelRes = labelRes;
        }
    }

    private static final int INK = 0xff0e161c;
    private static final int PANEL = 0xff14212a;
    private static final int CREAM = 0xfff4f3e9;
    private static final int MUTED = 0xffa4b6bb;
    private static final int MINT = 0xff81dfcf;
    private static final int WARNING = 0xffefd2a1;
    private static final int BORDER = 0xff31434c;
    private final Activity activity;
    private final Listener listener;
    private final List<HostRecord> saved = new ArrayList<>();
    private final List<DiscoveredHost> discovered = new ArrayList<>();
    private final List<View> actions = new ArrayList<>();
    private Dialog picker;
    private AlertDialog auxiliary;
    private AlertDialog pairing;
    private LinearLayout hosts;
    private ScrollView hostScroll;
    private TextView status, error;
    private Button manualButton, qualityButton;
    private Quality quality = Quality.FULL_HD_1080_60;
    private boolean busy;
    private String discoveryStatus;
    private String lastError = "";
    private Runnable cancelPairing;

    public GameStreamDialog(Activity activity, Listener listener) {
        requireMainThread();
        this.activity = Objects.requireNonNull(activity);
        this.listener = Objects.requireNonNull(listener);
        discoveryStatus = text(R.string.gamestream_discovery_searching);
    }

    public void show() {
        requireMainThread();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (picker != null && picker.isShowing()) return;
        Dialog created = new Dialog(activity);
        created.requestWindowFeature(Window.FEATURE_NO_TITLE);
        picker = created;
        actions.clear();
        LinearLayout content = column();
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackground(background(PANEL, BORDER, 22));
        add(content, label(text(R.string.gamestream_title), 25, CREAM), 0);
        TextView introduction = label(text(R.string.gamestream_introduction), 14, MUTED);
        introduction.setLineSpacing(dp(3), 1);
        add(content, introduction, 8);

        ScrollView scroll = new ScrollView(activity);
        hostScroll = scroll;
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, dp(4), 0);
        LinearLayout body = column();
        status = label("", 12, MINT);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(body, status, 8);
        error = label(lastError, 13, WARNING);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(lastError.isEmpty() ? View.GONE : View.VISIBLE);
        add(body, error, 8);
        hosts = column();
        add(body, hosts, 4);
        TextView protocol = label(text(R.string.gamestream_protocol_note), 12, MUTED);
        protocol.setLineSpacing(dp(2), 1);
        add(body, protocol, 14);
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        scrollParams.topMargin = dp(8);
        scrollParams.bottomMargin = dp(10);
        content.addView(scroll, scrollParams);

        qualityButton = button("", v -> showQuality());
        qualityButton.setTag("quality");
        add(content, qualityButton, 0);
        actions.add(qualityButton);
        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        manualButton = button(text(R.string.gamestream_manual), v -> showManual());
        manualButton.setTag("manual");
        Button refresh = button(text(R.string.gamestream_rescan), v -> {
            if (busy) return;
            clearError();
            listener.onRefreshRequested();
        });
        refresh.setTag("refresh");
        Button close = button(text(R.string.gamestream_close), v -> dismiss());
        close.setTag("close");
        addWeighted(footer, manualButton, 1.6f, false);
        addWeighted(footer, refresh, 1f, true);
        addWeighted(footer, close, 1f, true);
        actions.add(manualButton); actions.add(refresh);
        add(content, footer, 8);
        created.setContentView(content);
        created.setCanceledOnTouchOutside(false);
        created.setOnDismissListener(dialog -> {
            if (picker != created) return;
            picker = null;
            dismissAuxiliary();
            dismissPairing();
            hosts = null; hostScroll = null; status = null; error = null;
            manualButton = null; qualityButton = null;
            actions.clear();
            busy = false;
            listener.onDismissed();
        });
        created.show();
        Window window = created.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            window.setLayout(Math.min(dp(740), Math.max(dp(280), screenWidth - dp(96))),
                    Math.min(dp(600), Math.max(dp(280), screenHeight - dp(72))));
            window.setGravity(Gravity.CENTER);
        }
        updateStatus(); updateQuality(); rebuildHosts(); updateEnabled();
        View first = firstHostButton();
        (first == null ? manualButton : first).requestFocus();
    }

    /** Programmatic close still notifies the owner once; it must distinguish a running stream. */
    public void dismiss() {
        requireMainThread();
        dismissAuxiliary();
        dismissPairing();
        if (picker != null) picker.dismiss();
    }

    public boolean isShowing() {
        requireMainThread();
        return picker != null && picker.isShowing();
    }

    public void updateHosts(List<HostRecord> savedHosts, List<DiscoveredHost> nearbyHosts) {
        requireMainThread();
        saved.clear(); discovered.clear();
        if (savedHosts != null) for (HostRecord host : savedHosts) if (host != null) saved.add(host);
        if (nearbyHosts != null) for (DiscoveredHost host : nearbyHosts) if (host != null) discovered.add(host);
        rebuildHosts();
    }

    /** Pass localized owner status or an empty string after the bounded scan ends. */
    public void setDiscoveryStatus(String message) {
        requireMainThread();
        discoveryStatus = message == null ? "" : message;
        updateStatus();
    }

    /** Busy disables repeated actions but always leaves Close/Back available to cancel. */
    public void setBusy(boolean value) {
        requireMainThread();
        busy = value;
        updateEnabled(); updateStatus();
    }

    public void setQuality(Quality value) {
        requireMainThread();
        quality = Objects.requireNonNull(value);
        updateQuality();
    }

    /** Does not reopen a picker that its owner has already closed. */
    public void showError(String message) {
        requireMainThread();
        dismissAuxiliary(); dismissPairing(); setBusy(false);
        lastError = message == null || message.trim().isEmpty()
                ? text(R.string.gamestream_error_generic) : message;
        if (error != null) { error.setText(lastError); error.setVisibility(View.VISIBLE); }
        if (hostScroll != null) hostScroll.smoothScrollTo(0, 0);
    }

    /** The owner must revalidate this endpoint before pairing; discovery is not authenticated. */
    public void confirmPairing(HostEndpoint endpoint, String displayName) {
        requireMainThread();
        Objects.requireNonNull(endpoint);
        if (!isShowing()) return;
        dismissAuxiliary(); setBusy(false);
        String name = safeName(displayName, endpoint);
        auxiliary = new AlertDialog.Builder(activity)
                .setTitle(R.string.gamestream_pair_title)
                .setMessage(activity.getString(R.string.gamestream_pair_confirmation, name, endpoint.toString()))
                .setNegativeButton(R.string.gamestream_cancel, null)
                .setPositiveButton(R.string.gamestream_pair, (dialog, which) -> {
                    if (busy || !isShowing()) return;
                    clearError(); setBusy(true);
                    listener.onPairConfirmed(endpoint, name);
                }).create();
        showAlert(auxiliary, false);
    }

    /**
     * The owner generates the PIN after consent. User Cancel/Back fires onCancel once.
     * dismissPairing(), showError(), and owner teardown dismiss silently instead.
     */
    public void showPairingPin(String displayName, String pin, Runnable onCancel) {
        requireMainThread();
        if (pin == null || !pin.matches("[0-9]{4}")) throw new IllegalArgumentException("Expected four-digit pairing PIN");
        Objects.requireNonNull(onCancel);
        if (!isShowing()) return;
        dismissAuxiliary(); dismissPairing(); setBusy(true);
        cancelPairing = onCancel;
        LinearLayout content = column();
        content.setPadding(dp(24), dp(10), dp(24), dp(12));
        TextView instructions = label(activity.getString(R.string.gamestream_pin_instruction,
                safeName(displayName, null)), 16, CREAM);
        add(content, instructions, 0);
        TextView code = label(pin, 52, MINT);
        code.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        code.setLetterSpacing(0.22f);
        code.setGravity(Gravity.CENTER);
        code.setContentDescription(activity.getString(R.string.gamestream_pin_accessibility,
                pin.substring(0, 1), pin.substring(1, 2), pin.substring(2, 3), pin.substring(3, 4)));
        add(content, code, 18);
        add(content, label(text(R.string.gamestream_pin_waiting), 13, MUTED), 16);
        AlertDialog created = new AlertDialog.Builder(activity)
                .setTitle(R.string.gamestream_pair_title).setView(content)
                .setNegativeButton(R.string.gamestream_cancel, (dialog, which) -> cancelPairingByUser())
                .create();
        pairing = created;
        created.setOnCancelListener(dialog -> cancelPairingByUser());
        showAlert(created, false);
    }

    /** Completion/teardown path; never invokes the user's cancellation callback. */
    public void dismissPairing() {
        requireMainThread();
        cancelPairing = null;
        AlertDialog old = pairing; pairing = null;
        if (old != null) { old.setOnCancelListener(null); old.dismiss(); }
    }

    private void cancelPairingByUser() {
        Runnable callback = cancelPairing;
        dismissPairing(); setBusy(false);
        if (callback != null) callback.run();
    }

    private void showManual() {
        if (busy || !isShowing()) return;
        dismissAuxiliary();
        LinearLayout body = column();
        body.setPadding(dp(24), dp(8), dp(24), dp(12));
        add(body, label(text(R.string.gamestream_manual_note), 14, MUTED), 0);
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(20); input.setTextColor(CREAM); input.setHintTextColor(MUTED);
        input.setHint(R.string.gamestream_manual_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789.:"));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setSelectAllOnFocus(true);
        input.setContentDescription(text(R.string.gamestream_manual_label));
        add(body, input, 12);
        AlertDialog created = new AlertDialog.Builder(activity)
                .setTitle(R.string.gamestream_manual_title).setView(body)
                .setNegativeButton(R.string.gamestream_cancel, null)
                .setPositiveButton(R.string.gamestream_connect, null).create();
        auxiliary = created;
        created.setOnShowListener(dialog -> {
            styleAlertButtons(created);
            Button connect = created.getButton(AlertDialog.BUTTON_POSITIVE);
            connect.setOnClickListener(v -> {
                if (busy || !isShowing()) return;
                HostEndpoint endpoint;
                try { endpoint = HostEndpoint.parse(input.getText().toString()); }
                catch (IllegalArgumentException invalid) {
                    input.setError(text(R.string.gamestream_invalid_address)); input.requestFocus(); return;
                }
                HostRecord record = findSaved(endpoint);
                String name = record == null ? findDiscoveredName(endpoint) : record.displayName;
                created.dismiss();
                selectHost(endpoint, name, record);
            });
            input.setOnEditorActionListener((view, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    connect.performClick(); return true;
                }
                return false;
            });
            input.requestFocus();
        });
        created.setCanceledOnTouchOutside(false);
        created.show();
    }

    private void showQuality() {
        if (busy || !isShowing()) return;
        dismissAuxiliary();
        Quality[] options = Quality.values();
        CharSequence[] names = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) names[i] = text(options[i].labelRes);
        AlertDialog created = new AlertDialog.Builder(activity)
                .setTitle(R.string.gamestream_quality_title)
                .setSingleChoiceItems(names, quality.ordinal(), (dialog, which) -> {
                    if (busy || !isShowing()) return;
                    Quality selected = options[which];
                    setQuality(selected);
                    dialog.dismiss();
                    listener.onQualitySelected(selected);
                })
                .setNegativeButton(R.string.gamestream_cancel, null).create();
        auxiliary = created;
        showAlert(created, true);
    }

    private void confirmForget(HostRecord host) {
        if (busy || !isShowing()) return;
        dismissAuxiliary();
        auxiliary = new AlertDialog.Builder(activity)
                .setTitle(R.string.gamestream_forget_title)
                .setMessage(activity.getString(R.string.gamestream_forget_confirmation, host.displayName))
                .setNegativeButton(R.string.gamestream_cancel, null)
                .setPositiveButton(R.string.gamestream_forget, (dialog, which) -> {
                    if (!busy && isShowing()) listener.onForgetHost(host);
                }).create();
        showAlert(auxiliary, false);
    }

    private void selectHost(HostEndpoint endpoint, String name, HostRecord record) {
        if (busy || !isShowing()) return;
        clearError(); setBusy(true);
        listener.onHostSelected(endpoint, safeName(name, endpoint), record);
    }

    private void rebuildHosts() {
        if (hosts == null) return;
        View focused = picker == null ? null : picker.getCurrentFocus();
        Object oldTag = focused == null ? null : focused.getTag();
        boolean hostWasFocused = oldTag instanceof String && ((String)oldTag).startsWith("host:");
        actions.removeIf(view -> view.getTag() instanceof String && ((String)view.getTag()).startsWith("host:"));
        hosts.removeAllViews();
        Map<HostEndpoint, HostRecord> savedByAddress = new LinkedHashMap<>();
        for (HostRecord host : saved) savedByAddress.put(host.endpoint, host);
        Map<HostEndpoint, DiscoveredHost> nearbyByAddress = new LinkedHashMap<>();
        for (DiscoveredHost host : discovered) if (!savedByAddress.containsKey(host.endpoint)) nearbyByAddress.put(host.endpoint, host);
        add(hosts, section(R.string.gamestream_saved), 10);
        if (savedByAddress.isEmpty()) add(hosts, label(text(R.string.gamestream_no_saved), 13, MUTED), 7);
        for (HostRecord host : savedByAddress.values()) {
            addHost(host.endpoint, host.displayName, host,
                    host.isPaired() ? R.string.gamestream_paired : R.string.gamestream_unpaired);
        }
        add(hosts, section(R.string.gamestream_nearby), 16);
        if (nearbyByAddress.isEmpty()) add(hosts, label(text(R.string.gamestream_no_nearby), 13, MUTED), 7);
        for (DiscoveredHost host : nearbyByAddress.values()) addHost(host.endpoint, host.displayName, null, R.string.gamestream_discovered);
        updateEnabled();
        if (hostWasFocused) {
            View replacement = hosts.findViewWithTag(oldTag);
            (replacement == null ? manualButton : replacement).requestFocus();
        }
    }

    private void addHost(HostEndpoint endpoint, String name, HostRecord record, int stateRes) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String label = activity.getString(R.string.gamestream_host_label, safeName(name, endpoint), endpoint.toString(), text(stateRes));
        Button select = button(label, v -> selectHost(endpoint, name, record));
        select.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        select.setTag("host:select:" + endpoint);
        select.setTextSize(14); select.setMinHeight(dp(64));
        select.setMaxLines(3); select.setEllipsize(TextUtils.TruncateAt.END);
        addWeighted(row, select, 1, false); actions.add(select);
        if (record != null) {
            Button forget = button(text(R.string.gamestream_forget), v -> confirmForget(record));
            forget.setTag("host:forget:" + endpoint);
            forget.setContentDescription(activity.getString(R.string.gamestream_forget_accessibility, record.displayName));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = dp(8); row.addView(forget, params); actions.add(forget);
            select.setNextFocusRightId(forget.getId()); forget.setNextFocusLeftId(select.getId());
        }
        add(hosts, row, 8);
    }

    private View firstHostButton() {
        for (View action : actions) if (action.getTag() instanceof String
                && ((String)action.getTag()).startsWith("host:select:")) return action;
        return null;
    }
    private HostRecord findSaved(HostEndpoint endpoint) {
        for (HostRecord host : saved) if (host.endpoint.equals(endpoint)) return host;
        return null;
    }
    private String findDiscoveredName(HostEndpoint endpoint) {
        for (DiscoveredHost host : discovered) if (host.endpoint.equals(endpoint)) return host.displayName;
        return endpoint.address;
    }
    private void updateStatus() {
        if (status == null) return;
        status.setText(busy ? text(R.string.gamestream_connecting) : discoveryStatus);
        status.setVisibility(busy || !discoveryStatus.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private void updateQuality() {
        if (qualityButton != null) qualityButton.setText(activity.getString(R.string.gamestream_quality_button, text(quality.labelRes)));
    }
    private void updateEnabled() {
        View focused = picker == null ? null : picker.getCurrentFocus();
        boolean moveToCancel = busy && focused != null && actions.contains(focused);
        for (View action : actions) { action.setEnabled(!busy); action.setAlpha(busy ? 0.45f : 1f); }
        if (moveToCancel && picker != null && picker.getWindow() != null) {
            View close = picker.getWindow().getDecorView().findViewWithTag("close");
            if (close != null) close.requestFocus();
        }
    }
    private void clearError() {
        lastError = "";
        if (error != null) { error.setText(""); error.setVisibility(View.GONE); }
    }
    private void dismissAuxiliary() {
        AlertDialog old = auxiliary; auxiliary = null;
        if (old != null) old.dismiss();
    }
    private void showAlert(AlertDialog dialog, boolean focusList) {
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(unused -> {
            styleAlertButtons(dialog);
            if (focusList && dialog.getListView() != null) dialog.getListView().requestFocus();
            else {
                Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (cancel != null) cancel.requestFocus();
            }
        });
        dialog.show();
    }
    private void styleAlertButtons(AlertDialog dialog) {
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) styleButton(button);
        }
    }
    private TextView section(int resource) {
        TextView view = label(text(resource), 13, CREAM);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
        return view;
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private TextView label(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setIncludeFontPadding(true); view.setFocusable(false);
        return view;
    }
    private Button button(String value, View.OnClickListener click) {
        Button button = new Button(activity);
        button.setId(View.generateViewId());
        button.setText(value); button.setOnClickListener(click);
        styleButton(button);
        return button;
    }
    private void styleButton(Button button) {
        button.setAllCaps(false); button.setTextSize(14);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(14), dp(9), dp(14), dp(9));
        button.setFocusable(true); button.setFocusableInTouchMode(true);
        button.setStateListAnimator(null);
        button.setOnFocusChangeListener((view, focused) -> paintButton(button, focused));
        paintButton(button, button.hasFocus());
    }
    private void paintButton(Button button, boolean focused) {
        button.setTextColor(focused ? INK : CREAM);
        button.setBackground(background(focused ? MINT : 0xff1b2d36, focused ? MINT : BORDER, 11));
    }
    private GradientDrawable background(int fill, int line, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), line);
        return drawable;
    }
    private void add(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin); parent.addView(child, params);
    }
    private void addWeighted(LinearLayout parent, View child, float weight, boolean gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = gap ? dp(8) : 0; parent.addView(child, params);
    }
    private String text(int resource) { return activity.getString(resource); }
    private String safeName(String value, HostEndpoint endpoint) {
        if (value == null || value.trim().isEmpty()) return endpoint == null ? text(R.string.gamestream_phone) : endpoint.address;
        // Discovery/store sanitize names too. Keep controller-supplied labels single-line and bounded.
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length() && result.length() < 96;) {
            int point = value.codePointAt(i); i += Character.charCount(point);
            int type = Character.getType(point);
            if (!Character.isISOControl(point) && type != Character.FORMAT && type != Character.LINE_SEPARATOR
                    && type != Character.PARAGRAPH_SEPARATOR && type != Character.SURROGATE) result.appendCodePoint(point);
        }
        String cleaned = result.toString().trim();
        return cleaned.isEmpty() ? text(R.string.gamestream_phone) : cleaned;
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("GameStream UI requires the main thread");
    }
}
