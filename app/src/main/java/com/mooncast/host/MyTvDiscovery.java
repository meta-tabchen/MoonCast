package com.mooncast.host;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Shared, foreground-scoped discovery. Every NSD resolve uses the process-wide serial broker. */
final class MyTvDiscovery implements AutoCloseable {
    interface Listener { void changed(List<MyTvDevice> devices); void failed(); }
    interface ProbeListener { void complete(MyTvDevice device); }
    private static final String AIR = "_airplay._tcp.", APK = "_mooncast-apk._tcp.";
    private final Context context;
    private final NsdManager nsd;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MyTvRegistry registry = new MyTvRegistry();
    private final HashSet<String> found = new HashSet<>();
    private final java.util.HashMap<String, Integer> epochs = new java.util.HashMap<>();
    private int nextEpoch;
    private final List<NsdManager.DiscoveryListener> browsers = new ArrayList<>();
    private ExecutorService probes;
    private volatile HttpURLConnection connection;
    private WifiManager.MulticastLock multicast;
    private int generation;
    private volatile boolean active;
    MyTvDiscovery(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        nsd = this.context.getSystemService(NsdManager.class);
    }
    void start() {
        if (active) return;
        active = true; int run = ++generation;
        probes = Executors.newSingleThreadExecutor();
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        try { if (wifi != null) { multicast = wifi.createMulticastLock("mooncast-my-tv"); multicast.setReferenceCounted(false); multicast.acquire(); } }
        catch (RuntimeException ignored) { listener.failed(); }
        if (nsd == null) { listener.failed(); return; }
        browse(AIR, run); browse(APK, run);
    }
    /** DNS-SD callbacks vary by Android resolver: legacy resolution adds a leading
     * dot; some stacks include the local domain. DNS labels are case insensitive. */
    private static String type(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith(".")) normalized = normalized.substring(1);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith(".local")) normalized = normalized.substring(0, normalized.length() - 6);
        return normalized + ".";
    }
    private static String key(NsdServiceInfo info) { return type(info.getServiceType()) + "/" + info.getServiceName(); }
    private void browse(String type, int run) {
        NsdManager.DiscoveryListener browser = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onStopDiscoveryFailed(String t, int error) {}
            public void onStartDiscoveryFailed(String t, int error) { main.post(() -> { if (run == generation) listener.failed(); }); }
            public void onServiceFound(NsdServiceInfo info) { main.post(() -> {
                if (run != generation || !type.equals(type(info.getServiceType())) || info.getServiceName() == null
                    || info.getServiceName().length() > 255 || found.size() >= 64) return;
                String id = key(info);
                if (found.add(id)) { int epoch = ++nextEpoch; epochs.put(id, epoch); resolve(info, id, run, epoch, 0); }
            }); }
            public void onServiceLost(NsdServiceInfo info) { main.post(() -> {
                if (run != generation) return;
                String id = key(info); found.remove(id); epochs.remove(id); registry.remove(id); changed();
            }); }
        };
        browsers.add(browser);
        try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, browser); }
        catch (RuntimeException ignored) { listener.failed(); }
    }
    private void resolve(NsdServiceInfo info, String id, int run, int epoch, int attempt) {
        Resolver.submit(nsd, info, () -> run == generation && java.util.Objects.equals(epochs.get(id), epoch), result -> {
            if (run != generation || !java.util.Objects.equals(epochs.get(id), epoch)) return;
            if (result == null) {
                if (attempt == 0) main.postDelayed(() -> { if (run == generation && java.util.Objects.equals(epochs.get(id), epoch)) resolve(info, id, run, epoch, 1); }, 400);
                else listener.failed();
                return;
            }
            MyTvDevice device = fromService(result);
            if (device != null) { registry.put(id, device); changed(); }
        });
    }
    private static String txt(Map<String, byte[]> map, String key) {
        byte[] bytes = map.get(key); return bytes == null || bytes.length > 128 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }
    static MyTvDevice fromService(NsdServiceInfo info) {
        if (info.getHost() == null) return null;
        String host = info.getHost().getHostAddress();
        if (!AirPlayEndpoint.localNumericHost(host)) return null;
        Map<String, byte[]> attributes = info.getAttributes();
        if (APK.equals(type(info.getServiceType()))) {
            if (ApkEndpoint.parse(host + ":" + MyTvDevice.APK_PORT) == null
                || info.getPort() != MyTvDevice.APK_PORT || !"2".equals(txt(attributes, "v"))
                || !"1".equals(txt(attributes, "apk")) || !"none".equals(txt(attributes, "pairing"))) return null;
            return new MyTvDevice(host, info.getServiceName(), 0, MyTvDevice.APK_PORT, txt(attributes, "id"), "", false);
        }
        if (AIR.equals(type(info.getServiceType())) && info.getPort() > 0 && info.getPort() <= 65535)
            return new MyTvDevice(host, info.getServiceName(), info.getPort(), 0, txt(attributes, "id"), txt(attributes, "deviceid"), false);
        return null;
    }
    /** Probe only the fixed endpoint. No redirects, credentials, URI or file state. */
    void probe(String host, boolean allowManualAirplay, ProbeListener callback) {
        if (!active || probes == null || !AirPlayEndpoint.localNumericHost(host)) { callback.complete(null); return; }
        if (ApkEndpoint.parse(host + ":" + MyTvDevice.APK_PORT) == null) {
            // Preserve existing AirPlay local/link-local support without widening APK's network policy.
            MyTvDevice manual = allowManualAirplay ? new MyTvDevice(host, host, 7000, 0, "", "", true) : null;
            if (manual != null) { registry.put("manual/" + host, manual); changed(); }
            callback.complete(manual); return;
        }
        final int run = generation;
        probes.execute(() -> {
            if (!active) return;
            MyTvDevice result = null;
            HttpURLConnection c = null;
            Runnable watchdog = null;
            final long deadline = android.os.SystemClock.elapsedRealtime() + 4500;
            try {
                c = (HttpURLConnection) new URL("http", host, MyTvDevice.APK_PORT, "/info").openConnection();
                connection = c; if (!active) { c.disconnect(); return; } c.setConnectTimeout(1800); c.setReadTimeout(1800); c.setInstanceFollowRedirects(false);
                final HttpURLConnection boundedConnection = c;
                watchdog = boundedConnection::disconnect; main.postDelayed(watchdog, 4500);
                c.setRequestProperty("Accept", "application/json");
                if (c.getResponseCode() == 200) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream in = c.getInputStream()) {
                        byte[] buffer = new byte[512]; int count;
                        while ((count = in.read(buffer)) != -1) { if (android.os.SystemClock.elapsedRealtime() >= deadline) throw new java.io.IOException("Info deadline"); if (bytes.size() + count > 4096) throw new java.io.IOException("Oversized info"); bytes.write(buffer, 0, count); }
                    }
                    JSONObject json = new JSONObject(bytes.toString("UTF-8"));
                    String identity = json.optString("receiverId", "");
                    if (json.optInt("protocol") == 2 && json.optBoolean("apk") && json.has("pairing") && !json.getBoolean("pairing")
                        && json.optInt("port") == MyTvDevice.APK_PORT && "ready".equals(json.optString("state"))
                        && identity.matches("[A-Za-z0-9-]{8,128}"))
                        result = new MyTvDevice(host, json.optString("name", host), 0, MyTvDevice.APK_PORT, identity, "", false);
                }
            } catch (Exception ignored) { /* An unreachable/native TV never gains APK capability. */ }
            finally { if (watchdog != null) main.removeCallbacks(watchdog); if (c != null) c.disconnect(); connection = null; }
            if (result == null && allowManualAirplay) result = new MyTvDevice(host, host, 7000, 0, "", "", true);
            if (result != null && allowManualAirplay && !result.supportsAirplay())
                result = result.merge(new MyTvDevice(host, host, 7000, 0, "", "", true));
            final MyTvDevice value = result;
            main.post(() -> {
                if (run != generation) return;
                if (value != null) {
                    String id = "manual/" + host; int epoch = ++nextEpoch; epochs.put(id, epoch);
                    registry.put(id, value); changed();
                    main.postDelayed(() -> {
                        if (run == generation && java.util.Objects.equals(epochs.get(id), epoch)) { registry.remove(id); epochs.remove(id); changed(); }
                    }, 30000);
                }
                callback.complete(value);
            });
        });
    }
    private void changed() { listener.changed(registry.snapshot()); }
    @Override public void close() {
        active = false; ++generation; main.removeCallbacksAndMessages(null);
        for (NsdManager.DiscoveryListener browser : browsers) try { nsd.stopServiceDiscovery(browser); } catch (RuntimeException ignored) {}
        browsers.clear(); found.clear(); epochs.clear(); registry.clear();
        if (probes != null) { probes.shutdownNow(); probes = null; }
        HttpURLConnection c = connection; if (c != null) c.disconnect();
        if (multicast != null) { try { if (multicast.isHeld()) multicast.release(); } catch (RuntimeException ignored) {} multicast = null; }
        Resolver.prune();
    }

    /** Old Android cannot cancel resolveService: retain its slot after timeout until its callback.
     * This intentionally avoids concurrent resolvers across dismiss/reopen on API 26–33. */
    private static final class Resolver {
        interface Valid { boolean get(); }
        interface Result { void accept(NsdServiceInfo info); }
        private static final Handler MAIN = new Handler(Looper.getMainLooper());
        private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
        private static Job current;
        static void submit(NsdManager nsd, NsdServiceInfo info, Valid valid, Result result) {
            prune(); if (QUEUE.size() >= 64) { result.accept(null); return; }
            QUEUE.add(new Job(nsd, info, valid, result)); next();
        }
        static void prune() { QUEUE.removeIf(job -> !job.valid.get()); }
        private static void next() {
            prune(); if (current != null || QUEUE.isEmpty()) return;
            current = QUEUE.removeFirst(); current.start();
        }
        private static final class Job implements NsdManager.ResolveListener {
            final NsdManager nsd; final NsdServiceInfo info; final Valid valid; final Result result;
            boolean delivered;
            final Runnable timeout = this::timedOut;
            private void timedOut() {
                deliver(null);
                if (Build.VERSION.SDK_INT >= 34) try { nsd.stopServiceResolution(this); } catch (RuntimeException ignored) { /* Keep slot until callback. */ }
            }
            Job(NsdManager nsd, NsdServiceInfo info, Valid valid, Result result) { this.nsd = nsd; this.info = info; this.valid = valid; this.result = result; }
            @SuppressWarnings("deprecation") void start() {
                MAIN.postDelayed(timeout, 6000);
                try { nsd.resolveService(info, this); } catch (RuntimeException ignored) { finish(null); }
            }
            void deliver(NsdServiceInfo value) { if (!delivered) { delivered = true; if (valid.get()) result.accept(value); } }
            void finish(NsdServiceInfo value) {
                if (current != this) return;
                MAIN.removeCallbacks(timeout); deliver(value); current = null; next();
            }
            public void onResolveFailed(NsdServiceInfo service, int error) { MAIN.post(() -> finish(null)); }
            public void onServiceResolved(NsdServiceInfo service) { MAIN.post(() -> finish(service)); }
            public void onResolutionStopped(NsdServiceInfo service) { MAIN.post(() -> finish(null)); }
            public void onStopResolutionFailed(NsdServiceInfo service, int error) { /* Original callback owns slot. */ }
        }
    }
}
