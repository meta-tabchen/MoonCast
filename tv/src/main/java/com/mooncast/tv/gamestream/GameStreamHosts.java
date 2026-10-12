// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.limelight.nvstream.http.ComputerDetails;
import com.mooncast.receiver.MoonlightHostClient;
import com.mooncast.tv.R;
import com.mooncast.tv.gamestream.hosts.*;
import com.mooncast.tv.gamestream.ui.GameStreamDialog;
import com.mooncast.tv.net.LanAccess;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Activity-scoped host browser. Pairing trust is created only after the TV's explicit confirmation. */
public final class GameStreamHosts {
    public interface Listener { void onConnect(HostRecord host, GameStreamDialog.Quality quality); }
    private final Activity activity;
    private final SharedPreferences preferences;
    private final Supplier<LanAccess> currentLan;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final HostStore store;
    private final Object lock = new Object();
    private volatile int epoch;
    private boolean closed,storeReady;
    private GameStreamDialog dialog;
    private HostDiscovery discovery;
    private MoonlightHostClient pairing;
    private List<HostRecord> saved = Collections.emptyList();
    private List<DiscoveredHost> found = Collections.emptyList();
    private GameStreamDialog.Quality quality;

    public GameStreamHosts(Activity activity, SharedPreferences preferences, Supplier<LanAccess> currentLan, Listener listener) {
        this.activity = activity; this.preferences = preferences; this.currentLan = currentLan; this.listener = listener;
        store = new HostStore(activity);
        try { quality = GameStreamDialog.Quality.valueOf(preferences.getString("gamestream.quality", "FULL_HD_1080_60")); }
        catch (IllegalArgumentException e) { quality = GameStreamDialog.Quality.FULL_HD_1080_60; }
    }
    public void show() {
        if (closed) return;
        dismiss(); saved=Collections.emptyList(); found=Collections.emptyList(); storeReady=false; final int token = ++epoch;
        dialog = new GameStreamDialog(activity, new GameStreamDialog.Listener() {
            public void onHostSelected(HostEndpoint endpoint, String name, HostRecord record) {
                if (!active(token)) return;
                if (!storeReady) { error(R.string.gs_store_failed); return; }
                try { endpoint.requireOnLan(requireLan()); }
                catch (RuntimeException e) { error(R.string.gs_network_required); return; }
                if (record == null || !record.isPaired()) dialog.confirmPairing(endpoint, name);
                else connect(record);
            }
            public void onPairConfirmed(HostEndpoint endpoint, String name) { if (active(token)&&storeReady) pair(endpoint, name, token); }
            public void onForgetHost(HostRecord host) {
                if (!active(token)||!storeReady) return;
                storeReady=false;dialog.setBusy(true);
                worker.execute(() -> {
                    try {
                        if (!active(token)) return;
                        store.forget(host.endpoint);
                        load(token);
                    } catch (Exception e) { post(token, () -> error(R.string.gs_store_failed)); }
                });
            }
            public void onQualitySelected(GameStreamDialog.Quality selected) {
                quality = selected; preferences.edit().putString("gamestream.quality", selected.name()).apply();
            }
            public void onRefreshRequested() { if (active(token)) scan(token); }
            public void onDismissed() { if (active(token)) cancel(); }
        });
        dialog.setQuality(quality); dialog.show(); dialog.setBusy(true);
        worker.execute(() -> load(token)); scan(token);
    }
    private LanAccess requireLan() {
        LanAccess lan = currentLan.get(); if (lan == null) throw new IllegalStateException("No selected LAN"); return lan;
    }
    private void connect(HostRecord host) {
        GameStreamDialog.Quality selected = quality;
        dismiss(); listener.onConnect(host, selected);
    }
    private void scan(int token) {
        if (discovery != null) discovery.stop();
        LanAccess lan = currentLan.get();
        if (lan == null) { error(R.string.gs_network_required); return; }
        found = Collections.emptyList(); update();
        discovery = new HostDiscovery(activity, lan, new HostDiscovery.Listener() {
            public void onHostsChanged(List<DiscoveredHost> hosts) { if (active(token)) { found = hosts; update(); } }
            public void onError(String ignored) { if (active(token)) dialog.setDiscoveryStatus(activity.getString(R.string.gs_discovery_unavailable)); }
            public void onDiscoveryStateChanged(boolean scanning) {
                if (active(token)) dialog.setDiscoveryStatus(activity.getString(scanning ? R.string.gs_scanning : R.string.gs_scan_finished));
            }
        });
        discovery.start();
    }
    private void load(int token) {
        try { List<HostRecord> records = store.load(); post(token, () -> { saved = records; storeReady=true; dialog.setBusy(false); update(); }); }
        catch (Exception e) { post(token, () -> error(R.string.gs_store_failed)); }
    }
    private void pair(HostEndpoint endpoint, String name, int token) {
        final LanAccess lan;
        try { lan = requireLan(); endpoint.requireOnLan(lan); }
        catch (RuntimeException e) { error(R.string.gs_network_required); return; }
        final String pin = String.format(java.util.Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
        dialog.setBusy(true);
        dialog.showPairingPin(name, pin, () -> { cancel(); if (!closed) show(); });
        worker.execute(() -> {
            MoonlightHostClient operation = null;
            try {
                synchronized (lock) { if (!active(token)) return; }
                // This method is reached only from the explicitly confirmed Pair action above.
                AndroidPairingIdentity identity = AndroidPairingIdentity.getOrCreateForUserPairing(activity);
                operation = new MoonlightHostClient(activity, identity, identity.getClientId());
                synchronized (lock) { if (!active(token)) { operation.close(); return; } pairing = operation; }
                ComputerDetails.AddressTuple address = new ComputerDetails.AddressTuple(endpoint.address, endpoint.httpPort);
                ComputerDetails info = operation.inspect(address, 0, null);
                X509Certificate pinCert = operation.pair(address, info.httpsPort, pin);
                ComputerDetails verified = operation.inspect(address, info.httpsPort, pinCert);
                HostRecord record = new HostRecord(endpoint, name, verified.httpsPort, verified.uuid, pinCert);
                if (!active(token)) return;
                // Cancellation suppresses subsequent connection/UI delivery. An already-started
                // authorized atomic trust write may finish; never hold the UI cancellation lock
                // across fsync, nor silently roll back a successfully paired host identity.
                store.save(record);
                post(token, () -> { dialog.dismissPairing(); dialog.setBusy(false); connect(record); });
            } catch (Exception | LinkageError e) { post(token, () -> { dialog.dismissPairing(); error(R.string.gs_pair_failed); }); }
            finally {
                if (operation != null) operation.close();
                synchronized (lock) { if (pairing == operation) pairing = null; }
            }
        });
    }
    private boolean active(int token) { return !closed && token == epoch; }
    private void post(int token, Runnable action) { main.post(() -> { if (active(token) && dialog != null) action.run(); }); }
    private void update() { if (dialog != null) dialog.updateHosts(saved, found); }
    private void error(int message) { if (dialog != null) { dialog.setBusy(false); dialog.showError(activity.getString(message)); } }
    private void cancel() {
        synchronized (lock) { ++epoch; if (pairing != null) pairing.close(); }
        if (discovery != null) { discovery.stop(); discovery = null; }
    }
    public void dismiss() {
        cancel(); GameStreamDialog old = dialog; dialog = null; if (old != null) old.dismiss();
    }
    public void close() { if (closed) return; dismiss(); closed = true; worker.shutdown(); }
}
