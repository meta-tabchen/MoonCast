package com.mooncast.host;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Executes production MyTvDiscovery callbacks using injected Android service adapters.
 * No replicated parser, state machine, registry, or resolver implementation lives here. */
public final class MyTvDiscoveryCallbacksTest {
    private static int assertions;
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static NsdServiceInfo service(String name, String type, String host, int port) throws Exception {
        NsdServiceInfo info = new NsdServiceInfo(); info.setServiceName(name); info.setServiceType(type); info.setPort(port);
        if (host != null) info.setHost(InetAddress.getByName(host)); return info;
    }
    static NsdServiceInfo apk(String name, String type, String host) throws Exception {
        NsdServiceInfo info = service(name, type, host, 48152);
        info.setAttribute("v", "2"); info.setAttribute("apk", "1"); info.setAttribute("pairing", "none"); info.setAttribute("id", "receiver-123"); return info;
    }
    static final class Capture implements MyTvDiscovery.Listener {
        List<MyTvDevice> rows = new ArrayList<>(); int failures, changes;
        public void changed(List<MyTvDevice> devices) { rows = devices; changes++; }
        public void failed() { failures++; }
    }
    static final class Backend extends NsdManager {
        final LinkedHashMap<String, DiscoveryListener> browsers = new LinkedHashMap<>();
        ResolveListener pending; NsdServiceInfo candidate;
        int calls, stopped, canceled, peak;
        public void discoverServices(String t, int p, DiscoveryListener l) { browsers.put(t, l); l.onDiscoveryStarted(t); }
        public void stopServiceDiscovery(DiscoveryListener l) { browsers.values().remove(l); stopped++; }
        public void resolveService(NsdServiceInfo i, ResolveListener l) {
            check(pending == null, "Only one NSD resolution may be in flight"); pending = l; candidate = i; calls++; peak = Math.max(peak, 1);
        }
        public void stopServiceResolution(ResolveListener l) {
            check(pending == l, "Cancel only the active resolution"); NsdServiceInfo i = candidate; pending = null; canceled++; l.onResolutionStopped(i);
        }
        void found(String browser, NsdServiceInfo i) { browsers.get(browser).onServiceFound(i); Handler.drain(); }
        void lost(String browser, NsdServiceInfo i) { browsers.get(browser).onServiceLost(i); Handler.drain(); }
        void resolved(NsdServiceInfo i) { ResolveListener l = pending; check(l != null, "Expected pending resolve"); pending = null; l.onServiceResolved(i); Handler.drain(); }
        void failed() { ResolveListener l = pending; NsdServiceInfo i = candidate; pending = null; l.onResolveFailed(i, 3); Handler.drain(); }
    }
    static final class Env extends Context implements AutoCloseable {
        final Backend nsd = new Backend(); final WifiManager wifi = new WifiManager(); final Capture capture = new Capture();
        final MyTvDiscovery discovery = new MyTvDiscovery(this, capture);
        public <T> T getSystemService(Class<T> t) { return t.cast(nsd); }
        public Object getSystemService(String n) { return wifi; }
        Env() { discovery.start(); Handler.drain(); }
        public void close() { discovery.close(); Handler.drain(); if (nsd.pending != null) nsd.failed(); }
    }
    private static final String AIR = "_airplay._tcp.", APK = "_mooncast-apk._tcp.";
    static void legacyResolvedType() throws Exception {
        try (Env e = new Env()) {
            e.nsd.found(AIR, service("Native TV", AIR, null, 0));
            e.nsd.resolved(service("Native TV", "._airplay._tcp", "192.168.1.10", 7100));
            check(e.capture.rows.size() == 1, "REGRESSION: legacy Android leading-dot resolve type must produce AirPlay row");
            check(e.capture.rows.get(0).supportsAirplay() && !e.capture.rows.get(0).supportsApk(), "Native TV needs neither APK advertisement nor APK TXT");
            check(e.capture.rows.get(0).airplayPort == 7100, "Advertised AirPlay port retained");
        }
    }
    static void typeAndNameVariants() throws Exception {
        for (String variant : new String[]{"_airplay._tcp", AIR, "._airplay._tcp", "_airplay._tcp.local.", "._AIRPLAY._TCP.LOCAL."}) {
            try (Env e = new Env()) {
                String name = "客厅.TV (AirPlay)";
                e.nsd.found(AIR, service(name, variant, null, 0));
                e.nsd.resolved(service(name, variant, "192.168.2.10", 7000));
                check(e.capture.rows.size() == 1 && name.equals(e.capture.rows.get(0).name), "Accepted type and Unicode/dotted name: " + variant);
                e.nsd.lost(AIR, service(name, "._AirPlay._tcp.local.", null, 0));
                check(e.capture.rows.isEmpty(), "Lost callback canonical key removes variant row");
            }
        }
        try (Env e = new Env()) {
            for (String bad : new String[]{"_airplay._tcp.evil.local.", "_airplay._tcp.local.evil", "x_airplay._tcp.", ".._airplay._tcp.", "_mooncast-apk._tcp."})
                e.nsd.found(AIR, service("wrong type", bad, null, 0));
            check(e.nsd.calls == 0, "Unknown type/suffix/cross-browser callbacks rejected");
        }
    }
    static void independentCapabilitiesAndAddresses() throws Exception {
        for (String host : new String[]{"10.1.2.3", "172.16.2.3", "192.168.4.5", "169.254.20.30", "100.64.20.30"}) {
            try (Env e = new Env()) {
                e.nsd.found(AIR, service("Any vendor", AIR, null, 0));
                e.nsd.resolved(service("Any vendor", "._airplay._tcp", host, 7000));
                check(e.capture.rows.size() == 1 && e.capture.rows.get(0).host.equals(host), "Restore existing AirPlay address policy: " + host);
                check(!e.capture.rows.get(0).supportsApk(), "APK receiving off does not hide casting TV");
            }
        }
        for (String host : new String[]{"8.8.8.8", "127.0.0.1", "224.0.0.251", "::1"}) {
            try (Env e = new Env()) {
                e.nsd.found(AIR, service("Reject", AIR, null, 0)); e.nsd.resolved(service("Reject", AIR, host, 7000));
                check(e.capture.rows.isEmpty(), "Unsupported/public address rejected: " + host);
            }
        }
        try (Env e = new Env()) {
            e.nsd.found(AIR, service("Same name", AIR, null, 0));
            e.nsd.found(APK, service("APK", APK, null, 0));
            check(e.nsd.calls == 1, "Both discovery types share one resolver");
            e.nsd.resolved(service("Same name", "._airplay._tcp", "192.168.1.10", 7100));
            e.nsd.resolved(apk("APK", "._mooncast-apk._tcp.local.", "192.168.1.10"));
            check(e.capture.rows.size() == 1 && e.capture.rows.get(0).supportsApk() && e.capture.rows.get(0).supportsAirplay(), "Merge capabilities by address, not display name");
            e.nsd.lost(APK, service("APK", "._mooncast-apk._tcp", null, 0));
            check(e.capture.rows.size() == 1 && !e.capture.rows.get(0).supportsApk(), "Closing APK receiving preserves native AirPlay");
            e.nsd.found(AIR, service("Different instance", AIR, null, 0));
            e.nsd.resolved(service("Same name", AIR, "192.168.1.11", 7000));
            check(e.capture.rows.size() == 2, "Duplicate display names at different IPs remain separate");
        }
        for (String host : new String[]{"169.254.20.30", "100.64.20.30"}) {
            try (Env e = new Env()) {
                e.nsd.found(APK, service("APK", APK, null, 0)); e.nsd.resolved(apk("APK", "._mooncast-apk._tcp", host));
                check(e.capture.rows.isEmpty(), "APK policy remains RFC1918 only");
            }
        }
        try (Env e = new Env()) {
            e.nsd.found(APK, service("Old protocol", APK, null, 0));
            NsdServiceInfo bad = apk("Old protocol", APK, "192.168.1.10"); bad.setAttribute("v", "1"); e.nsd.resolved(bad);
            check(e.capture.rows.isEmpty(), "No APK capability without positive compatible TXT");
        }
    }
    static void manualAirPlayAndBrowserIsolation() throws Exception {
        for (String host : new String[]{"169.254.20.30", "100.64.20.30"}) {
            try (Env e = new Env()) {
                MyTvDevice[] result = new MyTvDevice[1];
                e.discovery.probe(host, true, value -> result[0] = value);
                check(result[0] != null && result[0].manualAirplay && result[0].airplayPort == 7000
                    && !result[0].supportsApk(), "Manual local AirPlay succeeds immediately without APK HTTP");
                e.discovery.probe(host, false, value -> result[0] = value);
                check(result[0] == null, "Manual APK probe cannot widen RFC1918 policy");
            }
        }
        try (Env e = new Env()) {
            e.nsd.browsers.get(APK).onStartDiscoveryFailed(APK, 3); Handler.drain();
            e.nsd.found(AIR, service("Native still available", AIR, null, 0));
            e.nsd.resolved(service("Native still available", "._airplay._tcp", "192.168.1.10", 7000));
            check(e.capture.rows.size() == 1 && e.capture.rows.get(0).supportsAirplay(), "APK browser failure must not stop native AirPlay discovery");
        }
    }
    static void lifecycleAndRetry() throws Exception {
        Build.VERSION.SDK_INT = 34;
        try (Env e = new Env()) {
            NsdServiceInfo found = service("Reappearing", AIR, null, 0);
            e.nsd.found(AIR, found); e.nsd.lost(AIR, found); e.nsd.found(AIR, found);
            e.nsd.resolved(service("Reappearing", AIR, "192.168.1.10", 7000));
            check(e.capture.rows.isEmpty(), "Lost/re-found service invalidates old incarnation");
            e.nsd.resolved(service("Reappearing", AIR, "192.168.1.11", 7000));
            check(e.capture.rows.get(0).host.equals("192.168.1.11"), "New incarnation resolves normally");
        }
        try (Env e = new Env()) {
            e.nsd.found(AIR, service("Slow", AIR, null, 0));
            NsdManager.DiscoveryListener stale = e.nsd.browsers.get(AIR);
            e.discovery.close(); check(!e.wifi.lock.isHeld() && e.nsd.stopped == 2, "Dismiss releases multicast and both browsers");
            e.discovery.start(); stale.onServiceFound(service("Stale", AIR, null, 0)); Handler.drain();
            e.nsd.found(AIR, service("New", AIR, null, 0));
            e.nsd.resolved(service("Slow", AIR, "192.168.1.10", 7000));
            check(e.capture.rows.isEmpty(), "Old callback cannot update reopened picker");
            e.nsd.resolved(service("New", AIR, "192.168.1.11", 7000));
            check(e.capture.rows.size() == 1 && e.wifi.lock.acquired == 2, "Reopen starts new foreground lease");
        }
        try (Env e = new Env()) {
            e.nsd.found(AIR, service("Timeout", AIR, null, 0));
            e.nsd.found(APK, service("Next", APK, null, 0)); Handler.advance(6000);
            check(e.nsd.canceled == 1 && e.nsd.calls == 2, "Cancelable timeout releases serial slot for other type");
            e.nsd.resolved(apk("Next", APK, "192.168.1.12")); Handler.advance(400);
            check(e.nsd.calls == 3, "Exactly one delayed retry"); e.nsd.failed(); Handler.advance(10000);
            check(e.nsd.calls == 3 && e.capture.failures == 1, "Retry bounded after second failure");
        }
        Build.VERSION.SDK_INT = 26;
        try (Env e = new Env()) {
            e.nsd.found(AIR, service("Legacy slow", AIR, null, 0)); Handler.advance(6400);
            check(e.nsd.calls == 1 && e.nsd.canceled == 0, "Legacy timeout does not create forbidden overlapping resolution");
            e.nsd.resolved(service("Legacy slow", AIR, "192.168.1.10", 7000));
            check(e.capture.rows.isEmpty() && e.nsd.calls == 2, "Late timed-out result ignored, queued retry starts after callback");
            e.nsd.resolved(service("Legacy slow", "._airplay._tcp", "192.168.1.10", 7000));
            check(e.capture.rows.size() == 1, "Legacy retry recovers after original callback");
        }
        Build.VERSION.SDK_INT = 34;
    }
    public static void main(String[] args) throws Exception {
        legacyResolvedType(); typeAndNameVariants(); independentCapabilitiesAndAddresses(); manualAirPlayAndBrowserIsolation(); lifecycleAndRetry();
        System.out.println("MyTvDiscoveryCallbacksTest passed: " + assertions + " callback assertions; real production discovery/registry/resolver exercised");
    }
}
