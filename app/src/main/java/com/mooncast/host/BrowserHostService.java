package com.mooncast.host;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

/** Owns one Android capture grant and a temporary LAN browser receiver session.
 * Browser reconnect replaces only the peer; the consumed projection grant stays alive.
 * The existing :host ownership/exit barrier also covers this protocol. */
public final class BrowserHostService extends Service {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread commands = new HandlerThread("BrowserCommands");
    private final ExecutorService lifecycle = Executors.newSingleThreadExecutor(r -> new Thread(r, "BrowserLifecycle"));
    private Messenger binder, subscriber;
    private MediaProjection projection;
    private volatile BrowserRtcSession rtc;
    private volatile BrowserSignalingServer server;
    private PowerManager.WakeLock wake;
    private CastControls controls;
    private boolean castPaused;
    private boolean started, ownsCapture;
    private volatile boolean stopping;
    private String state = "", detail = "", error = "", urls = "";
    private boolean connected;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(com.mooncast.locale.AppLocale.wrap(base));
    }

    @Override public void onCreate() {
        super.onCreate();
        commands.start();
        binder = new Messenger(new Handler(commands.getLooper(), message -> {
            if (message.what == HostService.STOP) stopSender();
            else if (message.what == HostService.STATUS) {
                Messenger reply = message.replyTo;
                main.post(() -> { if (reply != null) subscriber = reply; publish(); });
            }
            return true;
        }));
    }

    @Override public IBinder onBind(Intent intent) { return binder.getBinder(); }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if(CastControls.isAction(intent.getAction())&&controls==null){stopSelf();return START_NOT_STICKY;}
        if ("stop".equals(intent.getAction())) { stopSender(); return START_NOT_STICKY; }
        if(controls!=null&&controls.handle(intent.getAction()))return START_NOT_STICKY;
        if (started) return START_NOT_STICKY;
        subscriber = intent.getParcelableExtra("statusReply");
        if (!SenderOwnership.acquire(this)) { stopSelf(); return START_NOT_STICKY; }
        ownsCapture = true;
        started = true;
        try {
            notification();
            int source = intent.getIntExtra("source", 0);
            if (source < 0 || source > 1 || intent.getBooleanExtra("root", false))
                throw new IllegalArgumentException(getString(R.string.browser_service_projection_only));
            Intent grant = intent.getParcelableExtra("grant");
            if (grant == null) throw new IllegalArgumentException(getString(R.string.ui_missing_screen_capture_permission_start_again));
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK, grant);
            if (projection == null) throw new IllegalStateException(getString(R.string.ui_screen_sharing_was_not_authorized));
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSender(); }
                @Override public void onCapturedContentResize(int width, int height) {
                    BrowserRtcSession current = rtc;
                    if (current != null && !stopping) current.resize(width, height);
                }
            }, main);
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MoonCast:Browser");
            wake.acquire(6 * 60 * 60 * 1000L);
            controls=new CastControls(this,"browser",12,new CastControls.Session(){
                public boolean connected(){return !stopping&&connected&&rtc!=null;}
                public boolean pause(boolean value){if(rtc==null||value&&!connected())return false;rtc.pause(value);castPaused=value;return true;}
                public void stop(){stopSender();}
            },false);
            state = getString(R.string.browser_service_preparing);
            detail = getString(R.string.browser_service_lan_hint);
            publish();
            boolean audio = intent.getBooleanExtra("audio", false);
            BrowserQuality quality = BrowserQuality.of(
                    intent.getIntExtra("browserLongEdge", BrowserQuality.DEFAULT.longEdge),
                    intent.getIntExtra("browserFps", BrowserQuality.DEFAULT.fps),
                    intent.getIntExtra("browserBitrate", BrowserQuality.DEFAULT.maxBitrateBps));
            lifecycle.execute(() -> prepare(audio, source == 0, quality));
        } catch (Exception | LinkageError failure) { fail(failureSummary(failure)); }
        return START_NOT_STICKY;
    }

    private void prepare(boolean audio, boolean wholeScreen, BrowserQuality quality) {
        try {
            if (stopping) return;
            DisplayMetrics display = VideoPipeline.metrics(getSystemService(DisplayManager.class));
            int[] size = quality.dimensions(display.widthPixels, display.heightPixels);
            BrowserRtcSession ready = new BrowserRtcSession(this, projection, audio, size[0], size[1], quality,
                    new BrowserRtcSession.Listener() {
                        @Override public void state(String value) { main.post(() -> updateState(value)); }
                        @Override public void error(String message) { main.post(() -> fail(message)); }
                        @Override public void audioUnavailable(String message) {
                            main.post(() -> {
                                if (stopping) return;
                                detail = getString(R.string.browser_service_video_only);
                                publish();
                            });
                        }
                    });
            rtc = ready;
            ready.setWholeScreen(wholeScreen);
            ready.startCapture();
            if (stopping) return;
            Inet4Address lan = lanAddress();
            if (lan == null) throw new IllegalStateException(getString(R.string.browser_service_no_lan));
            BrowserSignalingServer endpoint = new BrowserSignalingServer(lan,
                    filename -> getAssets().open("browser/" + filename), new BrowserSignalingServer.Handler() {
                @Override public String offer(String body) throws Exception {
                    if (stopping) throw new BrowserSignalingServer.RequestException(503, "Sender stopped");
                    try {
                        JSONObject request = new JSONObject(body);
                        String sdp = request.getString("sdp");
                        String viewerId = request.getString("viewerId");
                        if (!viewerId.matches("[a-f0-9]{32}"))
                            throw new BrowserSignalingServer.RequestException(400, "Invalid browser viewer ID");
                        if (sdp.length() > 60_000 || !sdp.startsWith("v=0"))
                            throw new BrowserSignalingServer.RequestException(400, "Invalid SDP offer");
                        return ready.answer(sdp, request.optString("previousSessionId", ""), viewerId);
                    } catch (JSONException invalid) {
                        throw new BrowserSignalingServer.RequestException(400, "Invalid offer JSON");
                    } catch (BrowserRtcSession.BusyException busy) {
                        throw new BrowserSignalingServer.RequestException(409, "Another browser is connected");
                    }
                }
                @Override public String status(String sessionId) throws Exception { return ready.status(sessionId); }
                @Override public void disconnect(String sessionId) { ready.disconnect(sessionId); }
            });
            server = endpoint;
            String links = "http://" + lan.getHostAddress() + ":" + endpoint.port() + endpoint.path();
            main.post(() -> {
                if (stopping) return;
                urls = links;
                updateState("waiting");
            });
        } catch (Exception | LinkageError failure) { main.post(() -> fail(failureSummary(failure))); }
    }

    private Inet4Address lanAddress() throws Exception {
        // Prefer an OS-reported Wi-Fi/Ethernet link, not a cellular or VPN listener.
        android.net.ConnectivityManager connectivity = getSystemService(android.net.ConnectivityManager.class);
        for (android.net.Network network : connectivity.getAllNetworks()) {
            android.net.NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            android.net.LinkProperties properties = connectivity.getLinkProperties(network);
            if (capabilities == null || properties == null || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) continue;
            if (!capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    && !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) continue;
            for (android.net.LinkAddress link : properties.getLinkAddresses()) {
                if (link.getAddress() instanceof Inet4Address address && address.isSiteLocalAddress()) return address;
            }
        }
        // Android hotspot interfaces need not be represented as an upstream Network.
        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            var network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback() || network.isPointToPoint()) continue;
            String name = network.getName();
            if (!(name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("ap") || name.startsWith("swlan"))) continue;
            var addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                var address = addresses.nextElement();
                if (address instanceof Inet4Address local && local.isSiteLocalAddress()) return local;
            }
        }
        return null;
    }

    private void updateState(String value) {
        if (stopping) return;
        connected = "connected".equals(value);
        if(!connected&&controls!=null)controls.disconnected();
        int label = switch (value) {
            case "connected" -> R.string.browser_service_connected;
            case "connecting" -> R.string.browser_service_connecting;
            case "disconnected", "failed" -> R.string.browser_service_reconnecting;
            default -> R.string.browser_service_waiting;
        };
        state = getString(label);
        publish();
    }

    private void fail(String message) {
        if (stopping) return;
        error = AirPlayHostService.boundedDiagnostic(message == null ? getString(R.string.browser_service_failed) : message);
        state = getString(R.string.browser_service_failed);
        publish();
        stopSender();
    }

    private String failureSummary(Throwable failure) {
        // Record the failure type without exposing a session URL, SDP or capture contents.
        android.util.Log.e("MoonCastBrowser", "Initialization failed: " + failure.getClass().getSimpleName());
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isEmpty()
                ? ": " + getString(R.string.browser_service_failed) : ": " + message);
    }

    private synchronized void stopSender() {
        if (stopping) return;
        if (!ownsCapture) { stopSelf(); return; }
        stopping = true;
        // Arm independently of the UI, GL and native WebRTC threads, as existing senders do.
        new Thread(() -> { SystemClock.sleep(4000); android.os.Process.killProcess(android.os.Process.myPid()); }, "BrowserStopDeadline").start();
        main.post(() -> {if(controls!=null){controls.close();controls=null;} urls = ""; publish(); });
        lifecycle.execute(() -> {
            try { if (server != null) server.close(); } catch (RuntimeException ignored) {} finally { server = null; }
            try { if (rtc != null) rtc.close(); } catch (RuntimeException | LinkageError ignored) {} finally { rtc = null; }
            try { if (projection != null) projection.stop(); } catch (RuntimeException ignored) {} finally { projection = null; }
            try { if (wake != null && wake.isHeld()) wake.release(); } catch (RuntimeException ignored) {}
            main.post(() -> {
                connected = false;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                commands.quitSafely();
                lifecycle.shutdown();
                // Binder death acknowledges protocol teardown; never release the ownership lease early.
                new Thread(() -> { SystemClock.sleep(250); android.os.Process.killProcess(android.os.Process.myPid()); }, "BrowserExit").start();
            });
        });
    }

    @Override public void onDestroy() {
        if (ownsCapture) stopSender();
        else { commands.quitSafely(); lifecycle.shutdown(); }
        super.onDestroy();
    }

    private void notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("browser", getString(R.string.browser_service_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 12, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 13, new Intent(this, BrowserHostService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "browser").setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.browser_service_channel)).setContentText(getString(R.string.browser_service_lan_hint))
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.ui_stop), stop).build()).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(12, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(12, notification);
    }

    private void publish() {
        if (subscriber == null) return;
        Bundle data = new Bundle();
        data.putBoolean("paused",castPaused);data.putInt("pid", android.os.Process.myPid());
        data.putString("protocol", "browser");
        data.putString("state", state);
        data.putString("detail", detail);
        data.putString("error", error);
        data.putString("urls", stopping ? "" : urls);
        data.putBoolean("stopping", stopping);
        data.putBoolean("connected", connected);
        try {
            Message response = Message.obtain(null, HostService.STATUS);
            response.setData(data);
            subscriber.send(response);
        } catch (RemoteException ignored) { subscriber = null; }
    }
}
