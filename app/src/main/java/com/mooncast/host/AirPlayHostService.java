package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import airplaylib.Airplaylib;
import airplaylib.EventHandler;
import airplaylib.Session;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Exclusive outbound AirPlay screen + eligible playback-audio sender. This service owns
 * one MediaProjection grant. Protocol switching waits for the :host process to exit. */
public final class AirPlayHostService extends Service {
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(com.mooncast.locale.AppLocale.wrap(base));
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread commands = new HandlerThread("AirPlayCommands");
    private Messenger binder, subscriber;
    private final AtomicInteger generation = new AtomicInteger();
    private final ArrayList<String> logs = new ArrayList<>();
    private MediaProjection projection;
    private volatile Session session;
    private AirPlayVideoEncoder video;
    private AirPlayPlaybackAudio audio;
    private LocalAudioMute localMute;
    private PowerManager.WakeLock wake;
    private CastControls controls;
    private volatile boolean castPaused;
    private android.net.wifi.WifiManager.MulticastLock audioDiscoveryLock;
    private volatile boolean stopping;
    private boolean started, ownsCapture, cleanupStarted, connected, pinPending, wantsAudio, mutePhone, legacy, wholeScreen;
    private int width, height, fps, bitrate, scaleMode, port;
    private String host = "", state = "", detail = "", error = "";
    private volatile long connectedFrames;
    private long statusToken;
    private String protocolStage = "CONNECT";
    private boolean legacyHttpTransport;
    private final CapturedContentSize capturedSize = new CapturedContentSize();
    private String versionLabel = "unknown", stopOrigin = "running", nativeDiagnostic = "{}", lastVideoDiagnostic = "pending";
    private String failureCode = "none";
    private long lastDiagnosticSave;
    private volatile long pcmFrames;
    private volatile int pcmPeak;

    @Override public void onCreate() {
        super.onCreate(); commands.start();
        try { android.content.pm.PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0); versionLabel=p.versionName+"/"+(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode); }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        binder = new Messenger(new Handler(commands.getLooper(), msg -> {
            if (msg.what == HostService.STOP) { stopSender(); return true; }
            Message copy = Message.obtain(msg);
            main.post(() -> { try { handle(copy); } finally { copy.recycle(); } }); return true;
        }));
    }
    @Override public IBinder onBind(Intent intent) { return binder.getBinder(); }
    private void handle(Message msg) {
        if (msg.what == HostService.STATUS) { if (msg.replyTo != null) subscriber = msg.replyTo; publish(); return; }
        if (stopping) return;
        if (msg.what == HostService.PIN && pinPending) {
            String pin = msg.getData().getString("pin", "");
            if (pin.length() > 0 && pin.length() <= 128) { pinPending = false; connect(pin); }
        } else if (msg.what == HostService.SCALE) {
            scaleMode = Math.max(0, Math.min(CropGeometry.CINEMA, msg.arg1)); if (video != null) video.scale(scaleMode);
        } else if (msg.what == HostService.CROP_RESET && video != null) video.rescan();
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if(CastControls.isAction(intent.getAction())&&controls==null){stopSelf();return START_NOT_STICKY;}
        if ("stop".equals(intent.getAction())) { stopSender(); return START_NOT_STICKY; }
        if(controls!=null&&controls.handle(intent.getAction()))return START_NOT_STICKY;
        if (started) return START_NOT_STICKY;
        Messenger initialReply = intent.getParcelableExtra("statusReply");
        if (initialReply != null) subscriber = initialReply;
        if (!SenderOwnership.acquire(this)) { stopSelf(); return START_NOT_STICKY; }
        ownsCapture = true; started = true;
        statusToken = intent.getLongExtra("statusToken", 0);
        try {
            notification();
            localMute = new LocalAudioMute(this); localMute.restore();
            int source = intent.getIntExtra("source", 0);
            if (source < 0 || source > 1 || intent.getBooleanExtra("root", false)) throw new IllegalArgumentException(getString(R.string.ap_service_projection_only));
            wholeScreen = source == 0;
            host = intent.getStringExtra("airplayHost"); port = intent.getIntExtra("airplayPort", 7000);
            if (!validLanHost(host) || port < 1 || port > 65535) throw new IllegalArgumentException(getString(R.string.ap_service_bad_receiver));
            legacy = intent.getBooleanExtra("airplayLegacy", false);
            width = boundedEven(intent.getIntExtra("airplayWidth", 1920), 320, legacy ? 1280 : 3840);
            height = boundedEven(intent.getIntExtra("airplayHeight", 1080), 240, legacy ? 720 : 2160);
            fps = Math.max(15, Math.min(legacy ? 30 : 60, intent.getIntExtra("airplayFps", 30)));
            bitrate = Math.max(1_000_000, Math.min(40_000_000, intent.getIntExtra("airplayBitrate", 8_000_000)));
            scaleMode = Math.max(0, Math.min(CropGeometry.CINEMA, intent.getIntExtra("scaleMode", CropGeometry.VIDEO_REGION)));
            wantsAudio = intent.getBooleanExtra("audio", false);
            mutePhone = wantsAudio && intent.getBooleanExtra("muteLocal", false);
            Intent grant = intent.getParcelableExtra("grant");
            if (grant == null) throw new IllegalArgumentException(getString(R.string.ui_missing_screen_capture_permission_start_again));
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK, grant);
            if (projection == null) throw new IllegalStateException(getString(R.string.ui_screen_sharing_was_not_authorized));
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { if (!stopping) { stopOrigin="projection-stop"; stopSender(); } }
                @Override public void onCapturedContentResize(int w, int h) {
                    if (stopping) return;
                    if (capturedSize.update(w,h) && video != null) video.resize(w,h);
                }
            }, main);
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MoonCast:AirPlay");
            wake.acquire(6 * 60 * 60 * 1000L);
            controls=new CastControls(this,"airplay",8,new CastControls.Session(){
                public boolean connected(){return !stopping&&connected&&video!=null;}
                public boolean pause(boolean value){if(!connected())return false;video.pause(value);castPaused=value;return true;}
                public void stop(){stopSender();}
            },null);
            // The upstream bare legacy path sends empty SETUP /video and skips
            // /info, pairing and FairPlay. Real AirPlay mirrors require negotiated
            // session/stream plists. The saved compatibility option now limits
            // encoding only; receiver features still choose legacy/HAP pairing.
            Airplaylib.setAirPlay1Mode(false);
            // Private, backup-excluded store. Separate from Sunshine certificates/PINs.
            Airplaylib.setCredentialsPath(new File(getFilesDir(), "airplay-credentials.json").getAbsolutePath());
            connect(intent.getStringExtra("airplayPin"));
        } catch (Exception | LinkageError failure) { fail(PhoneMessages.message(R.string.phone_capture_failed,failure.getMessage())); }
        return START_NOT_STICKY;
    }
    private static int boundedEven(int value, int min, int max) { return Math.max(min, Math.min(max, value)) & ~1; }
    static boolean validLanHost(String host) {
        if (host == null || !host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        String[] parts = host.split("\\."); int[] octets = new int[4];
        try { for (int i = 0; i < 4; i++) { octets[i] = Integer.parseInt(parts[i]); if (octets[i] > 255) return false; } }
        catch (NumberFormatException failure) { return false; }
        return octets[0] == 10 || octets[0] == 192 && octets[1] == 168 || octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31
            || octets[0] == 169 && octets[1] == 254 || octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127;
    }
    private void connect(String pin) {
        if (wantsAudio) try {
            android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null && audioDiscoveryLock == null) {
                audioDiscoveryLock = wifi.createMulticastLock("MoonCast:RAOP");
                audioDiscoveryLock.setReferenceCounted(false); audioDiscoveryLock.acquire();
            }
        } catch (RuntimeException ignored) { releaseAudioDiscovery(); }
        int token = generation.incrementAndGet(); Session old = session; session = null;
        connected = false; connectedFrames = 0;
        protocolStage = "GET/stream.xml";
        legacyHttpTransport = false;
        state = getString(R.string.ap_service_connecting); detail = host + ":" + port; publish();
        new Thread(() -> {
            try {
            if (old != null) old.disconnect();
            if (stopping || token != generation.get()) return;
            Session created = Airplaylib.newSession(new EventHandler() {
                private void deliver(Runnable action) { main.post(() -> { if (!stopping && token == generation.get()) action.run(); }); }
                public void onDeviceFound(String json) {}
                public void onConnected() { deliver(() -> startCapture(token)); }
                public void onDisconnected(String reason) { deliver(() -> failProtocol(reason)); }
                public void onPinRequired() { deliver(() -> { pinPending = true; state = getString(R.string.ap_service_pin); detail = getString(R.string.ap_service_pin_hint); publish(); }); }
                public void onError(String message) { deliver(() -> failProtocol(message)); }
                public void onLog(String message) {
                    String stage = AirPlayDiagnostics.progress(message);
                    if (stage != null) deliver(() -> { if (message.equals("[LEGACYHTTP] STREAM")) legacyHttpTransport = true; protocolStage = stage; log("AirPlay stage=" + stage); publish(); });
                }
            });
            // Claim the session on the command owner before starting an asynchronous Go handshake.
            main.post(() -> {
                if (stopping || token != generation.get()) { discard(created); return; }
                session = created;
                try { created.setAudioEnabled(wantsAudio); created.setClockReference(System.nanoTime()/1000); created.connect(host, port, pin == null ? "" : pin, width, height, fps); }
                catch (RuntimeException | LinkageError failure) { failProtocol(failure.getMessage()); }
            });
            } catch (RuntimeException | LinkageError failure) {
                main.post(() -> { if (!stopping && token == generation.get()) failProtocol(failure.getMessage()); });
            }
        }, "AirPlayConnect").start();
        main.postDelayed(() -> { if (!stopping && token == generation.get() && !connected && !pinPending) fail(getString(R.string.ap_service_timeout, AirPlayDiagnostics.safeStage(protocolStage))); }, 25000);
    }
    private void failProtocol(String reason) {
        stopOrigin=AirPlayDiagnostics.origin(reason);
        failureCode=AirPlayDiagnostics.failure(reason, protocolStage);
        fail(getString(R.string.ap_service_protocol_failed, AirPlayDiagnostics.failure(reason, protocolStage)));
    }
    private static void discard(Session obsolete) {
        new Thread(() -> { try { obsolete.disconnect(); } catch (RuntimeException | LinkageError ignored) {} }, "AirPlayDiscard").start();
    }
    private void startCapture(int token) {
        if (stopping || connected || token != generation.get() || session == null) return;
        releaseAudioDiscovery();
        Session active = session;
        try {
            int rw = (int)active.streamWidth(), rh = (int)active.streamHeight();
            if (rw > 0 && rh > 0) {
                // Native already applied the requested quality ceiling. Use its
                // exact canvas rather than scaling a second time after rotation.
                width = rw & ~1; height = rh & ~1;
            }
            active.setAirPlay1FrameSize(width, height);
            video = new AirPlayVideoEncoder(this, new AirPlayVideoEncoder.Output() {
                public void video(byte[] bytes, boolean keyframe, long ptsMicros) { if (!stopping && token == generation.get()) { active.sendFrameAt(bytes, keyframe, ptsMicros); connectedFrames++; } }
                public void error(String message) { main.post(() -> { if (token == generation.get()) fail(message); }); }
                public void log(String message) { main.post(() -> AirPlayHostService.this.log(message)); }
            });
            boolean compatibility = legacy || legacyHttpTransport;
            int captureFps = legacyHttpTransport ? Math.min(fps, 30) : fps;
            video.start(projection, width, height, captureFps, compatibility ? Math.min(bitrate, 4_000_000) : bitrate, compatibility, scaleMode, wholeScreen, capturedSize);
            connected = true; pinPending = false;
            state = getString(R.string.ap_service_casting); detail = getString(R.string.phone_airplay_stream,host,width,height,fps);
            if (wantsAudio && active.hasAudio()) startAudio(active, token);
            else log(getString(wantsAudio ? R.string.ap_service_receiver_no_audio : R.string.ui_video_only_sound_stays_on_the_phone));
            publish();
            watchTransport(active, token);
        } catch (Exception | LinkageError failure) { fail(PhoneMessages.message(R.string.phone_capture_failed,failure.getMessage())); }
    }
    private void watchTransport(Session active, int token) {
        main.postDelayed(() -> {
            if (stopping || token != generation.get() || session != active) return;
            try {
                if (!active.isConnected()) { failProtocol("legacy transport connection closed"); return; }
                publish();
                watchTransport(active, token);
            } catch (RuntimeException | LinkageError failure) { failProtocol(failure.getMessage()); }
        }, 500);
    }
    private void startAudio(Session active, int token) {
        try {
            audio = new AirPlayPlaybackAudio(); audio.start(this, projection, new AirPlayPlaybackAudio.Output() {
                public void audio(byte[] bytes) { if (!stopping && token == generation.get()) {
                    int peak=0; for(int i=0;i+1<bytes.length;i+=2) peak=Math.max(peak,Math.abs((int)(short)((bytes[i]&255)|(bytes[i+1]<<8))));
                    pcmPeak=peak; pcmFrames++; if(castPaused)java.util.Arrays.fill(bytes,(byte)0);active.sendAudio(bytes);
                } }
                public void failed(String reason) { main.post(() -> { if (!stopping && token == generation.get()) { closeAudio(); log(getString(R.string.ui_audio_capture_unavailable_video_continues) + reason); publish(); } }); }
            });
            // Local volume control is optional. A fixed-volume route or denied
            // mute must not tear down otherwise valid receiver playback audio.
            if (mutePhone) try { localMute.silence(); }
            catch (RuntimeException failure) { log(getString(R.string.ui_phone_muting_unavailable) + failure.getMessage()); }
            log(getString(R.string.ap_service_audio));
            watchAudio(active, token);
        } catch (RuntimeException failure) { closeAudio(); log(getString(R.string.ui_audio_capture_unavailable_video_continues) + failure.getMessage()); }
    }
    private void watchAudio(Session active, int token) {
        main.postDelayed(() -> {
            if (stopping || token != generation.get() || session != active || audio == null) return;
            // A native RAOP audio forwarder can end while video stays connected. Never
            // leave the handset muted when the transport no longer has an audio pipe.
            try {
                if (!active.hasAudio()) { closeAudio(); log(getString(R.string.ap_service_audio_ended)); publish(); }
                else watchAudio(active, token);
            } catch (RuntimeException | LinkageError failure) { closeAudio(); failProtocol(failure.getMessage()); }
        }, 500);
    }
    private void restoreVolume() { if (localMute != null) try { localMute.restore(); } catch (RuntimeException failure) { android.util.Log.w("MoonCast", "Volume recovery pending", failure); } }
    private void closeAudio() { restoreVolume(); AirPlayPlaybackAudio old = audio; audio = null; if (old != null) old.close(); }
    private void fail(String message) {
        if (stopping) return;
        if (stopOrigin.equals("running")) stopOrigin="capture-or-startup";
        releaseAudioDiscovery();
        connected = false; pinPending = false;
        error = boundedDiagnostic(PhoneMessages.render(this, message == null || message.isEmpty() ? getString(R.string.ap_service_failed) : message));
        state = getString(R.string.ap_service_failed); detail = error; log(error);
        HostFailureStore.failure(getFilesDir(), statusToken, AirPlayDiagnostics.safeStage(protocolStage), error, this::publish, this::stopSender);
    }
    private synchronized void stopSender() {
        if (stopping) return;
        if (!ownsCapture) { stopSelf(); return; }
        stopping = true; generation.incrementAndGet();
        if (stopOrigin.equals("running")) stopOrigin="user-or-service-stop";
        connected = false; pinPending = false;
        // Same bounded process isolation as GameStream; no consumed Android grant is reused.
        new Thread(() -> { SystemClock.sleep(4000); android.os.Process.killProcess(android.os.Process.myPid()); }, "AirPlayStopDeadline").start();
        main.post(this::finishStop);
    }
    private void finishStop() {
        if (cleanupStarted) return; cleanupStarted = true;if(controls!=null){controls.close();controls=null;} publish(); restoreVolume(); releaseAudioDiscovery();
        // Close Go pipes before waiting for audio/video workers blocked by network backpressure.
        Session old = session; session = null;
        new Thread(() -> {
            try { if (old != null) old.disconnect(); }
            catch (RuntimeException | LinkageError failure) { android.util.Log.w("MoonCast", "AirPlay disconnect failed; capture cleanup continues"); }
            finally { main.post(this::releaseCapture); }
        }, "AirPlayDisconnect").start();
    }
    private void releaseAudioDiscovery() {
        android.net.wifi.WifiManager.MulticastLock lock = audioDiscoveryLock; audioDiscoveryLock = null;
        if (lock != null) try { if (lock.isHeld()) lock.release(); } catch (RuntimeException ignored) {}
    }
    private void releaseCapture() {
        closeAudio();
        try { if (video != null) video.close(); } catch (RuntimeException failure) { android.util.Log.w("MoonCast", "Video cleanup", failure); } finally { video = null; }
        try { if (projection != null) projection.stop(); } catch (RuntimeException ignored) {} finally { projection = null; }
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (RuntimeException ignored) {}
        connected = false; pinPending = false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        // Binder death is the only switch acknowledgement. Ownership stays held until exit.
        new Thread(() -> { SystemClock.sleep(250); android.os.Process.killProcess(android.os.Process.myPid()); }, "AirPlayExit").start();
    }
    @Override public void onDestroy() { if (ownsCapture) stopSender(); else commands.quitSafely(); super.onDestroy(); }
    private void notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("airplay", getString(R.string.phone_airplay_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 3, new Intent(this, AirPlayHostService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "airplay").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.ap_service_notification)).setContentText(getString(R.string.ui_lan_casting_tap_to_open_stop_at_any_time))
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, getString(R.string.ui_stop), stop).build()).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(8, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION); else startForeground(8, notification);
    }
    static String boundedDiagnostic(String message) {
        if (message == null) return "";
        StringBuilder safe = new StringBuilder(Math.min(320, message.length()));
        for (int i = 0; i < message.length() && i < 320; i++) {
            char c = message.charAt(i); safe.append(c >= 32 || c == '\n' || c == '\t' ? c : ' ');
        }
        return safe.toString();
    }
    private void log(String message) { logs.add(boundedDiagnostic(PhoneMessages.render(this,message))); if (logs.size() > 60) logs.remove(0); }
    private void publish() {
        String report=diagnosticReport();
        long now=SystemClock.elapsedRealtime();
        if(stopping || !error.isEmpty() || now-lastDiagnosticSave>5000) {AirPlayDiagnosticStore.save(getFilesDir(),report);lastDiagnosticSave=now;}
        if (subscriber == null) return;
        Bundle b = new Bundle(); b.putString("state", state); b.putString("detail", detail); b.putString("error", error);
        b.putBoolean("paused",castPaused);b.putBoolean("audioRunning", audio != null); b.putBoolean("stopping", stopping); b.putBoolean("connected", connected); b.putBoolean("pinPending", pinPending);
        b.putInt("scaleMode", scaleMode); b.putString("protocol", "airplay"); b.putInt("pid", android.os.Process.myPid());
        b.putString("diagnosticReport",report);
        if(wantsAudio && connected && audio==null) {
            try {
                org.json.JSONObject n=new org.json.JSONObject(nativeDiagnostic);if(n.has("last"))n=n.getJSONObject("last");
                b.putString("audioHint",getString(R.string.ap_service_audio_reason,n.optString("audioStage","pending"),n.optString("audioError","none")));
            } catch(org.json.JSONException ignored) {}
        }
        b.putString("logs", String.join("\n", logs));
        FrameStatistics.Sample stats = video == null ? new FrameStatistics.Sample(0, 0, 0) : video.statistics();
        b.putString("metrics", getString(R.string.phone_airplay_metrics, connectedFrames, stats.fps(), audio == null ? getString(R.string.phone_video_only) : "44.1 kHz ALAC"));
        try { Message response = Message.obtain(null, HostService.STATUS); response.setData(b); subscriber.send(response); } catch (RemoteException ignored) { subscriber = null; }
    }
    private String diagnosticReport() {
        Session current=session;
        if(current!=null) try {nativeDiagnostic=current.diagnostics();}catch(RuntimeException | LinkageError ignored){}
        if(video!=null)lastVideoDiagnostic=video.diagnostics();
        return "MoonCast "+versionLabel+";sdk="+Build.VERSION.SDK_INT+
            "\nsnapshotElapsedMs="+SystemClock.elapsedRealtime()+"\nsnapshotWallMs="+System.currentTimeMillis()+
            "\nconnected="+connected+";source="+(wholeScreen?"screen":"app")+";mode="+scaleMode+
            ";captured="+capturedSize.width(0)+"x"+capturedSize.height(0)+"\n"+lastVideoDiagnostic+
            "\naudioRequested="+wantsAudio+";audioCapture="+(audio!=null)+";pcmFrames="+pcmFrames+";pcmPeak="+pcmPeak+
            "\nstopOrigin="+stopOrigin+";failure="+failureCode+"\nnative="+nativeDiagnostic;
    }
}
