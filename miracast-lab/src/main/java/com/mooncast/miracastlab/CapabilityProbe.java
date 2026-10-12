// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.miracastlab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.location.LocationManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only diagnostic. Never advertises WFD, scans peers, or creates/connects a P2P group. */
public final class CapabilityProbe {
    static final String CONFIGURE_WFD = "android.permission.CONFIGURE_WIFI_DISPLAY";
    private final Context context;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, String> rows = new LinkedHashMap<>();
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private int generation;
    private boolean hasP2p;
    private String summary;

    CapabilityProbe(Context context, Runnable changed) {
        this.context = context.getApplicationContext(); this.changed = changed;
    }
    public String summary() { return summary == null ? "" : summary; }
    public String report() {
        StringBuilder out = new StringBuilder("MoonCast Miracast Lab 0.1.0-diagnostic\n");
        out.append(context.getString(R.string.not_receiver)).append('\n').append(summary()).append("\n\n");
        for (Map.Entry<String, String> row : rows.entrySet()) out.append(row.getKey()).append(":\n").append(row.getValue()).append("\n\n");
        return out.toString();
    }
    void put(String key, String value) { rows.put(key, value); changed.run(); }
    static String error(Throwable error) {
        String message = error.getMessage();
        if (message == null) return error.getClass().getSimpleName();
        // Preserve real permission/API errors without exporting addresses embedded by a vendor.
        message = message.replaceAll("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}", "[MAC omitted]")
                .replaceAll("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])", "[IP omitted]");
        return error.getClass().getSimpleName() + ": " + message;
    }
    private boolean granted(String permission) { return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    public String runtimePermission() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.NEARBY_WIFI_DEVICES : Manifest.permission.ACCESS_FINE_LOCATION; }
    public boolean hasRuntimePermission() { return granted(runtimePermission()); }

    public void refresh() {
        cancel(); rows.clear();
        PackageManager pm = context.getPackageManager();
        rows.put("DEVICE", Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT);
        rows.put("IMPLEMENTED", "Capability reads + user-opened system settings. No Miracast RTSP/RTP receiver, media session, or WFD advertisement.");
        hasP2p = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
        rows.put("FEATURE_WIFI_DIRECT", Boolean.toString(hasP2p));
        rows.put("FEATURE_WIFI", Boolean.toString(pm.hasSystemFeature(PackageManager.FEATURE_WIFI)));
        try { manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE); rows.put("WIFI_P2P_SERVICE", manager == null ? "NULL" : "PRESENT"); }
        catch (RuntimeException | LinkageError error) { manager = null; rows.put("WIFI_P2P_SERVICE", error(error)); }
        rows.put("PUBLIC_setWfdInfo_API", Build.VERSION.SDK_INT >= 34 ? "API 34+ entry point; not invoked" : "Not in public SDK on API " + Build.VERSION.SDK_INT + "; no hidden API fallback");
        rows.put("CONFIGURE_WIFI_DISPLAY", granted(CONFIGURE_WFD) ? "GRANTED" : "NOT_GRANTED; not requested by this app");
        try {
            PermissionInfo info = pm.getPermissionInfo(CONFIGURE_WFD, 0);
            int base = info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
            String level = base == PermissionInfo.PROTECTION_SIGNATURE ? "signature" : base == PermissionInfo.PROTECTION_DANGEROUS ? "dangerous" : base == PermissionInfo.PROTECTION_NORMAL ? "normal" : "other";
            rows.put("WFD_PERMISSION_PROTECTION", level + " · raw=0x" + Integer.toHexString(info.protectionLevel));
        } catch (PackageManager.NameNotFoundException | RuntimeException error) { rows.put("WFD_PERMISSION_PROTECTION", error(error)); }
        rows.put("PERMISSION_ACCESS_WIFI_STATE", Boolean.toString(granted(Manifest.permission.ACCESS_WIFI_STATE)));
        rows.put("PERMISSION_CHANGE_WIFI_STATE", Boolean.toString(granted(Manifest.permission.CHANGE_WIFI_STATE)));
        rows.put("RUNTIME_PERMISSION", runtimePermission() + " = " + (hasRuntimePermission() ? "GRANTED" : "NOT_GRANTED"));
        try { WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE); rows.put("WIFI_ENABLED", wifi == null ? "NO_SERVICE" : Boolean.toString(wifi.isWifiEnabled())); }
        catch (RuntimeException | LinkageError error) { rows.put("WIFI_ENABLED", error(error)); }
        try {
            LocationManager location = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            rows.put("LOCATION_SERVICE_ENABLED", location == null ? "NO_SERVICE" : Build.VERSION.SDK_INT >= 28 ? Boolean.toString(location.isLocationEnabled()) : "Not read below API 28");
        } catch (RuntimeException | LinkageError error) { rows.put("LOCATION_SERVICE_ENABLED", error(error)); }
        try {
            StringBuilder names = new StringBuilder();
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) for (NetworkInterface network : Collections.list(interfaces)) {
                if (names.length() > 0) names.append("; ");
                names.append(network.getName()).append(network.isUp() ? " [up]" : " [down]");
            }
            rows.put("NETWORK_INTERFACES", names.length() == 0 ? "NONE_VISIBLE_TO_APP; inconclusive" : names + "\nNames only; a p2p interface is not proof of a receiver.");
        } catch (Exception | LinkageError error) { rows.put("NETWORK_INTERFACES", error(error)); }
        readDecoders();
        rows.put("SYSTEM_CAST_SETTINGS", systemTarget(Settings.ACTION_CAST_SETTINGS));
        rows.put("SYSTEM_WIFI_SETTINGS", systemTarget(Settings.ACTION_WIFI_SETTINGS));
        rows.put("P2P_READ_CHECK", context.getString(R.string.not_requested));
        summary = context.getString(!hasP2p ? R.string.summary_no_p2p : manager == null ? R.string.summary_service : Build.VERSION.SDK_INT < 34 ? R.string.summary_old_api : !granted(CONFIGURE_WFD) ? R.string.summary_permission : R.string.summary_available, Build.VERSION.RELEASE);
        changed.run();
    }
    private void readDecoders() {
        try {
            StringBuilder out = new StringBuilder();
            for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (codec.isEncoder()) continue;
                for (String type : codec.getSupportedTypes()) {
                    if (!MediaFormat.MIMETYPE_VIDEO_AVC.equalsIgnoreCase(type)) continue;
                    if (out.length() > 0) out.append('\n');
                    out.append(codec.getName());
                    if (Build.VERSION.SDK_INT >= 29) out.append(codec.isHardwareAccelerated() ? " [hardware]" : " [not hardware]");
                    try {
                        MediaCodecInfo.VideoCapabilities caps = codec.getCapabilitiesForType(type).getVideoCapabilities();
                        out.append(" · 1080p30 advertised=").append(caps != null && caps.areSizeAndRateSupported(1920, 1080, 30));
                    } catch (RuntimeException error) { out.append(" · ").append(error(error)); }
                }
            }
            rows.put("H264_DECODERS", out.length() == 0 ? "NONE_REPORTED" : out + "\nCodec metadata only; decoding and casting are not tested.");
        } catch (RuntimeException | LinkageError error) { rows.put("H264_DECODERS", error(error)); }
    }
    public Intent systemIntent(String action) {
        Intent request = new Intent(action);
        for (ResolveInfo resolved : context.getPackageManager().queryIntentActivities(request, PackageManager.MATCH_DEFAULT_ONLY)) {
            ActivityInfo info = resolved.activityInfo;
            if (info == null || !info.enabled || !info.exported || info.applicationInfo == null) continue;
            if ((info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0) continue;
            if (info.permission != null && !info.permission.isEmpty() && !granted(info.permission)) continue;
            return request.setComponent(new ComponentName(info.packageName, info.name));
        }
        return null;
    }
    private String systemTarget(String action) {
        try { Intent intent = systemIntent(action); return intent == null ? "NO_ACCESSIBLE_SYSTEM_HANDLER" : intent.getComponent().flattenToShortString() + "\nOpening is user-controlled; this is not evidence of a Miracast sink."; }
        catch (RuntimeException error) { return error(error); }
    }
    @SuppressLint("MissingPermission") // User-triggered, guarded below. OEM denials are retained in the report.
    public void runP2pReads() {
        cancel();
        if (Build.VERSION.SDK_INT < 27) { put("P2P_READ_CHECK", "NOT_RUN on API 26: public Channel.close requires API 27; avoiding an unclosable diagnostic channel"); return; }
        if (!hasRuntimePermission()) { put("P2P_READ_CHECK", "NOT_RUN: " + runtimePermission() + " not granted"); return; }
        if (manager == null) { put("P2P_READ_CHECK", "NOT_RUN: WifiP2pManager unavailable"); return; }
        final int token = ++generation;
        try {
            channel = manager.initialize(context, Looper.getMainLooper(), () -> {
                if (token == generation) { put("P2P_CHANNEL", "DISCONNECTED_BY_SYSTEM"); finishPending(context.getString(R.string.cancelled)); }
            });
            if (channel == null) { put("P2P_CHANNEL", "initialize returned NULL"); return; }
            put("P2P_CHANNEL", "INITIALIZED");
        } catch (RuntimeException | LinkageError error) { put("P2P_CHANNEL", error(error)); return; }
        put("P2P_READ_CHECK", "Read-only queries started; no discovery, connection, group creation, or WFD advertisement.");
        if (Build.VERSION.SDK_INT >= 29) {
            query("P2P_STATE", () -> manager.requestP2pState(channel, state -> result(token, "P2P_STATE", state == WifiP2pManager.WIFI_P2P_STATE_ENABLED ? "ENABLED" : state == WifiP2pManager.WIFI_P2P_STATE_DISABLED ? "DISABLED" : "UNKNOWN(" + state + ")")));
            query("P2P_DISCOVERY_STATE", () -> manager.requestDiscoveryState(channel, state -> result(token, "P2P_DISCOVERY_STATE", "current=" + state + "; this app did not start discovery")));
            query("LOCAL_WFD_INFO", () -> manager.requestDeviceInfo(channel, device -> {
                if (device == null) { result(token, "LOCAL_WFD_INFO", "NO_DEVICE_INFO"); return; }
                if (Build.VERSION.SDK_INT < 30) { result(token, "LOCAL_WFD_INFO", "Device returned; public WFD read requires API 30. Names/addresses omitted."); return; }
                WifiP2pWfdInfo wfd = device.getWfdInfo();
                result(token, "LOCAL_WFD_INFO", wfd == null ? "NO_WFD_INFO; not proof that OEM receiver is absent" : "enabled=" + wfd.isEnabled() + "; type=" + wfdType(wfd.getDeviceType()) + "; sessionAvailable=" + wfd.isSessionAvailable() + "; controlPort=" + wfd.getControlPort() + "\nSystem metadata only; no connection or playback tested.");
            }));
        } else {
            put("P2P_STATE", "Public requestP2pState requires API 29; not queried");
            put("LOCAL_WFD_INFO", "Public requestDeviceInfo requires API 29; not queried");
        }
        query("P2P_CONNECTION", () -> manager.requestConnectionInfo(channel, info -> result(token, "P2P_CONNECTION", info == null ? "NULL" : "groupFormed=" + info.groupFormed + "; isGroupOwner=" + info.isGroupOwner + "; group-owner address omitted")));
        query("P2P_GROUP", () -> manager.requestGroupInfo(channel, group -> result(token, "P2P_GROUP", group == null ? "NO_CURRENT_GROUP" : "group present; isOwner=" + group.isGroupOwner() + "; clientCount=" + group.getClientList().size() + "; interface=" + group.getInterface() + "; network name, addresses and passphrase omitted")));
        main.postDelayed(() -> { if (token == generation) { finishPending(context.getString(R.string.timed_out)); ++generation; releaseChannel(); } }, 6000);
    }
    private void query(String key, Runnable query) {
        put(key, context.getString(R.string.waiting));
        try { query.run(); } catch (RuntimeException | LinkageError error) { put(key, error(error)); }
    }
    private void result(int token, String key, String value) { if (token == generation) put(key, value); }
    private static String wfdType(int type) {
        return switch (type) { case 0 -> "SOURCE (sender)"; case 1 -> "PRIMARY_SINK"; case 2 -> "SECONDARY_SINK"; case 3 -> "SOURCE_OR_PRIMARY_SINK"; default -> "UNKNOWN(" + type + ")"; };
    }
    private void finishPending(String status) {
        String waiting = context.getString(R.string.waiting);
        for (Map.Entry<String, String> row : rows.entrySet()) if (waiting.equals(row.getValue())) row.setValue(status);
        changed.run();
    }
    private void releaseChannel() {
        WifiP2pManager.Channel old = channel; channel = null;
        if (old != null && Build.VERSION.SDK_INT >= 27) try { old.close(); } catch (RuntimeException | LinkageError error) { put("P2P_CHANNEL_CLOSE", error(error)); }
    }
    public void cancel() { ++generation; main.removeCallbacksAndMessages(null); finishPending(context.getString(R.string.cancelled)); releaseChannel(); }
}
