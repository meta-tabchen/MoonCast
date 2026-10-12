// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.mooncast.tv.net.LanAccess;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Bounded LAN-only _nvstream._tcp browse for Android 8+. No polling, Internet,
 * STUN, automatic pairing, or identity logging. Own one instance per UI owner.
 * start/stop and listener callbacks run on the main thread. Stop on owner departure
 * and any selected-network change; recreate with the new selected IPv4 subnet.
 */
@SuppressWarnings("deprecation") // API 26-compatible, intentionally sequential resolver.
public final class HostDiscovery {
    public interface Listener {
        void onHostsChanged(List<DiscoveredHost> hosts);
        void onError(String message);
        default void onDiscoveryStateChanged(boolean scanning) {}
    }
    private static final String SERVICE_TYPE = "_nvstream._tcp.";
    private static final long SCAN_DURATION_MS = 30000;
    private static final long RESOLVE_TIMEOUT_MS = 10000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NsdManager nsd;
    private final LanAccess lan;
    private final Listener listener;
    private final DiscoveryLedger<NsdServiceInfo> ledger = new DiscoveryLedger<>();
    private NsdManager.DiscoveryListener browser;
    private NsdManager.ResolveListener resolver;
    private Runnable scanTimeout;
    private Runnable resolveTimeout;
    private boolean scanning;

    public HostDiscovery(Context context, LanAccess lan, Listener listener) {
        nsd = context.getApplicationContext().getSystemService(NsdManager.class);
        this.lan = Objects.requireNonNull(lan);
        this.listener = Objects.requireNonNull(listener);
    }

    public void start() {
        requireMainThread();
        if (scanning) return;
        if (nsd == null) { listener.onError("Local-network discovery is unavailable. Enter the host IPv4 address instead."); return; }
        long generation = ledger.begin();
        scanning = true;
        browser = new NsdManager.DiscoveryListener() {
            private boolean stopRetried;
            @Override public void onDiscoveryStarted(String type) {
                main.post(() -> { if (!ledger.current(generation)) stopBrowser(this); });
            }
            @Override public void onServiceFound(NsdServiceInfo service) {
                main.post(() -> {
                    if (!ledger.current(generation) || !isHostService(service)) return;
                    String key = service.getServiceName();
                    if (key != null && !key.isEmpty() && ledger.found(generation, key, service)) resolveNext();
                });
            }
            @Override public void onServiceLost(NsdServiceInfo service) {
                main.post(() -> {
                    if (ledger.lost(generation, service.getServiceName())) listener.onHostsChanged(ledger.snapshot());
                });
            }
            @Override public void onStartDiscoveryFailed(String type, int errorCode) {
                main.post(() -> {
                    if (!ledger.current(generation)) { stopBrowser(this); return; }
                    stop();
                    listener.onError("Local-network discovery could not start. Enter the host IPv4 address instead.");
                });
            }
            @Override public void onStopDiscoveryFailed(String type, int errorCode) {
                // One bounded best-effort retry; never recursively retry forever or
                // reactivate discovery after the owner has stopped.
                main.post(() -> {
                    if (!stopRetried) { stopRetried = true; stopBrowser(this); }
                    if (ledger.current(generation)) {
                        stop();
                        listener.onError("Local-network discovery stopped unexpectedly.");
                    }
                });
            }
            @Override public void onDiscoveryStopped(String type) {
                main.post(() -> { if (ledger.current(generation)) stop(); });
            }
        };
        scanTimeout = () -> { if (ledger.current(generation)) stop(); };
        main.postDelayed(scanTimeout, SCAN_DURATION_MS);
        listener.onHostsChanged(ledger.snapshot());
        if (!ledger.current(generation)) return; // Listener may synchronously close its owner.
        listener.onDiscoveryStateChanged(true);
        if (!ledger.current(generation)) return;
        try { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, browser); }
        catch (RuntimeException e) {
            stop();
            listener.onError("Local-network discovery could not start. Enter the host IPv4 address instead.");
        }
    }

    public void stop() {
        requireMainThread();
        boolean wasScanning = scanning;
        scanning = false;
        ledger.stop();
        if (scanTimeout != null) { main.removeCallbacks(scanTimeout); scanTimeout = null; }
        if (resolveTimeout != null) { main.removeCallbacks(resolveTimeout); resolveTimeout = null; }
        NsdManager.DiscoveryListener old = browser;
        browser = null;
        if (old != null) stopBrowser(old);
        if (resolver != null && Build.VERSION.SDK_INT >= 34) {
            try { nsd.stopServiceResolution(resolver); }
            catch (RuntimeException ignored) { /* Its result still has a stale generation. */ }
        }
        if (wasScanning) listener.onDiscoveryStateChanged(false);
    }

    private void resolveNext() {
        DiscoveryLedger.Entry<NsdServiceInfo> entry = ledger.next();
        if (entry == null) return;
        resolver = new NsdManager.ResolveListener() {
            @Override public void onResolveFailed(NsdServiceInfo service, int errorCode) { main.post(() -> finish(entry, null, this)); }
            @Override public void onServiceResolved(NsdServiceInfo service) { main.post(() -> finish(entry, service, this)); }
            @Override public void onResolutionStopped(NsdServiceInfo service) { main.post(() -> finish(entry, null, this)); }
        };
        NsdManager.ResolveListener active = resolver;
        resolveTimeout = () -> {
            if (resolver != active || !ledger.current(entry.generation)) return;
            stop();
            listener.onError("Host discovery timed out. Enter the host IPv4 address instead.");
        };
        main.postDelayed(resolveTimeout, RESOLVE_TIMEOUT_MS);
        try { nsd.resolveService(entry.service, active); }
        catch (RuntimeException e) { finish(entry, null, active); }
    }

    private void finish(DiscoveryLedger.Entry<NsdServiceInfo> entry, NsdServiceInfo service,
            NsdManager.ResolveListener completed) {
        if (resolver != completed) return;
        resolver = null;
        if (resolveTimeout != null) { main.removeCallbacks(resolveTimeout); resolveTimeout = null; }
        DiscoveredHost host = null;
        if (service != null && ledger.current(entry.generation)) {
            // Newer NSD can return both families; do not lose a usable IPv4 address
            // merely because an IPv6 address happens to be listed first.
            if (Build.VERSION.SDK_INT >= 34) {
                List<InetAddress> addresses = service.getHostAddresses();
                for (int i = 0; i < addresses.size() && i < DiscoveryLedger.MAX_SERVICES && host == null; i++) {
                    host = resolvedHost(service, addresses.get(i));
                }
            } else {
                host = resolvedHost(service, service.getHost());
            }
        }
        if (ledger.complete(entry, host)) listener.onHostsChanged(ledger.snapshot());
        resolveNext();
    }

    private DiscoveredHost resolvedHost(NsdServiceInfo service, InetAddress address) {
        if (!(address instanceof Inet4Address)) return null;
        try {
            HostEndpoint endpoint = HostEndpoint.of(address.getHostAddress(), service.getPort()).requireOnLan(lan);
            return new DiscoveredHost(endpoint, service.getServiceName());
        } catch (IllegalArgumentException ignored) {
            return null; // Off-subnet/malformed advertisements are never probed.
        }
    }

    private void stopBrowser(NsdManager.DiscoveryListener listener) {
        try { nsd.stopServiceDiscovery(listener); }
        catch (RuntimeException ignored) { /* An already stopped/failed registration is harmless. */ }
    }
    private static boolean isHostService(NsdServiceInfo service) {
        String type = service.getServiceType();
        if (type == null) return false;
        type = type.toLowerCase(Locale.ROOT);
        return type.equals(SERVICE_TYPE) || type.equals("_nvstream._tcp");
    }
    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Host discovery must be owned by the main thread");
    }
}
