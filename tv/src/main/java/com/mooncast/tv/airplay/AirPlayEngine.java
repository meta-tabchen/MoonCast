/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.airplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import com.mooncast.tv.R;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.MediaFormat;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.github.jqssun.airplay.bridge.NativeBridge;
import io.github.jqssun.airplay.bridge.RaopCallbackHandler;

/** Real UxPlay receiver. Call start/stop on one serial background executor; listener calls run on main.
 * The supplied Surface belongs to the UI and is never released here. This is a LAN-only receiver.
 */
public final class AirPlayEngine implements RaopCallbackHandler {
    public enum PlaybackMode { MOVIE, LOW_LATENCY }
    /** Readiness and error handling must never depend on translated display text. */
    public enum Status { LISTENING, READY, RECOVERING, INFORMATION, PROBLEM }
    public enum SessionState { STOPPED, READY, MIRRORING, AUDIO_ONLY, MEDIA_URL, RECOVERING }
    /** Requested values and actual decoder/audio observations are intentionally separate. */
    public static final class RuntimeStats {
        public final SessionState state;
        public final PlaybackMode playbackMode;
        public final int requestedWidth, requestedHeight, requestedFps, decodedWidth, decodedHeight;
        public final String codec, decoderName, decoderImplementation, peerDescription;
        public final float observedFps;
        public final long decodedFrames, droppedFrames;
        public final int controlConnections, watchdogSeconds, configuredAudioBufferMs;
        public final boolean pinRequired, audioActive;
        public final NativeAudioStats audio;
        private RuntimeStats(AirPlayEngine engine) {
            state = engine.sessionState; playbackMode = engine.playbackMode;
            requestedWidth = engine.requestedWidth; requestedHeight = engine.requestedHeight;
            requestedFps = engine.requestedFps;
            synchronized (engine.decoder) {
                decodedWidth = engine.decoder.decodedWidth(); decodedHeight = engine.decoder.decodedHeight();
                codec = engine.decoder.codecMime(); decoderName = engine.decoder.decoderName();
                decoderImplementation = engine.decoder.implementation(); observedFps = engine.decoder.observedFps();
                decodedFrames = engine.decoder.frames(); droppedFrames = engine.decoder.drops();
            }
            controlConnections = engine.watchdog.connections(); watchdogSeconds = engine.watchdog.timeoutSeconds();
            configuredAudioBufferMs = engine.effectiveAudioBufferMs();
            pinRequired = engine.running ? engine.activePinRequired : engine.pinRequired;
            audioActive = engine.audioOutputActive; audio = engine.audioStats;
            // The ABI discards names/IDs. HTTP counts also include HLS proxy sockets.
            peerDescription = state == SessionState.MIRRORING || state == SessionState.AUDIO_ONLY
                    || state == SessionState.MEDIA_URL ? engine.resources.getString(R.string.tv_airplay_sender_unknown) : "";
        }
    }
    public interface Listener {
        void onStatus(Status status, String message);
        void onVideoSize(int width, int height);
        void onVideoPlay(String location, float startPositionSeconds);
        void onVideoScrub(float positionSeconds);
        void onVideoRate(float rate);
        void onVideoStop();
        default void onMirrorStarted() {}
        default void onMirrorStopped() {}
        default void onPin(String pin) {}
        default void onAudioStarted() {}
        /** Hide/clear the UI-owned mirror Surface; stopping MediaCodec retains its last pixels. */
        default void onSessionCleared(String reason) {}
        /** Main-thread request: queue stop/start on the owner's serial lifecycle worker. */
        default void onRecoveryRequired(String reason) {}
    }
    private final Context context;
    private final Resources resources;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object gate = new Object(), mirrorGate = new Object();
    private final AudioManager audio;
    private final NsdManager nsd;
    private final MirrorDecoder decoder;
    private static final java.util.concurrent.ExecutorService audioControl = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MoonCast-AirPlay-audio"); thread.setDaemon(true); return thread;
    });
    private AudioFocusRequest focusRequest;
    private int focusGeneration = -1;
    private volatile boolean audioDesired, audioHasFocus, audioOutputActive;
    private int audioCodecType, audioSamplesPerFrame;
    private final SessionWatchdog watchdog = new SessionWatchdog();
    private final ByteBuffer audioDebug = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
    private volatile NativeAudioStats audioStats;
    private volatile PlaybackMode playbackMode = PlaybackMode.MOVIE;
    private volatile SessionState sessionState = SessionState.STOPPED;
    private volatile int audioBufferMs = -1; // -1 = mode default, 0 = native adaptive tuner
    private volatile boolean pinRequired = true, activePinRequired = true, recoveryPending;
    private volatile int requestedWidth = 1920, requestedHeight = 1080;
    private final Runnable healthTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            pollAudioStats();
            String reason = watchdog.check(SystemClock.elapsedRealtime());
            if (reason != null) requestRecovery(resources.getString(SessionWatchdog.SENDER_DISCONNECTED.equals(reason)
                    ? R.string.tv_sender_disconnected : R.string.tv_video_decoder_stalled), false);
            if (running) main.postDelayed(this, 1000);
        }
    };
    private final List<NsdManager.RegistrationListener> registrations = new ArrayList<>();
    private final ArrayDeque<Frame> startupFrames = new ArrayDeque<>();
    private WifiManager.MulticastLock multicast;
    private long handle;
    private volatile boolean running;
    private long diagnosticFrames,diagnosticPackets;
    private volatile int boundPort;
    private volatile int generation;
    private boolean mirroring, mirrorReady, mirrorUiReady, surfaceAvailable, startupScheduled;
    private volatile int mirrorEpoch;
    private static final java.util.concurrent.ExecutorService videoBootstrap = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MoonCast-AirPlay-startup"); thread.setDaemon(true); return thread;
    });
    private volatile boolean audioUsingScreen;
    private int startupBytes, requestedFps = 60;
    private volatile String lastStatus = "";
    private static final class Frame {
        final byte[] bytes; final long ntp; final boolean hevc;
        Frame(byte[] b, long n, boolean h) { bytes = b; ntp = n; hevc = h; }
    }
    public AirPlayEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        // Keep the app-selected locale from the Activity instead of the process default.
        resources = context.getResources();
        audio = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        nsd = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
        decoder = new MirrorDecoder(new MirrorDecoder.Events() {
            @Override public void diagnostic(String code,long value){ReceiverDiagnostics.event(code,value);}
            @Override public void size(int w, int h) { post(() -> listener.onVideoSize(w, h)); }
            @Override public void failure() { requestRecovery(resources.getString(R.string.tv_video_decoder_failed), true); }
            @Override public void output() { watchdog.videoOutput();long count=++diagnosticFrames;if(count==1||count%600==0)ReceiverDiagnostics.event("decoded-frame",count); }
        });
    }
    public void start(String name, int width, int height, int fps) throws Exception {
        start(name, width, height, fps, pinRequired);
    }
    public void start(String name, int width, int height, int fps, boolean requirePin) throws Exception {
        if (running) return;
        diagnosticFrames=0;diagnosticPackets=0;
        name = name == null || name.trim().isEmpty() ? "MoonCast TV" : name.trim();
        while (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60)
            name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Start AirPlay on a background executor");
        generation++; ReceiverDiagnostics.event("native-start",generation); lastStatus = ""; audioDesired = false; audioHasFocus = false;
        recoveryPending = false; audioStats = null; audioUsingScreen = false;
        pinRequired = requirePin; activePinRequired = requirePin;
        sessionState = SessionState.STOPPED; watchdog.reset(); decoder.resetStatistics();
        if (!MirrorDecoder.supports(MediaFormat.MIMETYPE_VIDEO_AVC))
            throw new IllegalStateException("This device has no H.264 decoder");
        width = Math.max(640, Math.min(width, 3840)); height = Math.max(360, Math.min(height, 2160));
        requestedWidth = width; requestedHeight = height;
        requestedFps = Math.max(24, Math.min(fps, 60));
        decoder.configureTiming(playbackMode == PlaybackMode.MOVIE, effectiveVideoCushionMs());
        decoder.setResolution(width, height, requestedFps);
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicast = wifi.createMulticastLock("mooncast-tv-airplay");
                multicast.setReferenceCounted(false); multicast.acquire();
            }
            NativeBridge bridge = NativeBridge.INSTANCE;
            bridge.nativeSetDefaultStreamValues(audioProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE),
                    audioProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
            long created = bridge.nativeInit(this, installationAddress(), name,
                    new File(context.getFilesDir(), "airplay-identity.pem").getAbsolutePath(), false, requirePin);
            if (created == 0) throw new IllegalStateException("AirPlay native initialization failed");
            synchronized (gate) { handle = created; running = true; }
            bridge.nativeSetH265Enabled(created, MirrorDecoder.supports(MediaFormat.MIMETYPE_VIDEO_HEVC));
            bridge.nativeSetCodecs(created, true, true);
            bridge.nativeSetHlsEnabled(created, true);
            bridge.nativeSetAudioEnabled(created, true);
            bridge.nativeSetLang(created, "", "", resources.getConfiguration().getLocales().toLanguageTags().replace(',', ':'));
            bridge.nativeSetPlist(created, "maxFPS", requestedFps);
            bridge.nativeSetPlist(created, "overscanned", 0);
            bridge.nativeSetDisplaySize(created, width, height, requestedFps);
            if (!configureNativeAudio(created)) throw new IllegalStateException("Audio configuration failed");
            int port = bridge.nativeStart(created, 7000);
            boundPort = port;
            if (port < 1) throw new IllegalStateException("AirPlay could not listen on TCP 7000");
            register(bridge.nativeGetRaopServiceName(created), "_raop._tcp", port, bridge.nativeGetRaopTxtRecords(created));
            register(bridge.nativeGetServerName(created), "_airplay._tcp", port, bridge.nativeGetAirplayTxtRecords(created));
            sessionState = SessionState.READY;
            main.removeCallbacks(healthTick); main.post(healthTick);
            status(Status.LISTENING, R.string.tv_airplay_listening);
        } catch (Exception | LinkageError failure) {
            stop();
            throw new IllegalStateException("AirPlay startup failed", failure);
        }
    }
    public void stop() {
        ReceiverDiagnostics.event("native-stop",generation);
        long previous;
        audioDesired = false; audioHasFocus = false; audioOutputActive = false;
        synchronized (gate) { running = false; previous = handle; handle = 0; generation++; }
        main.removeCallbacks(healthTick); watchdog.reset(); audioStats = null;
        recoveryPending = false; sessionState = SessionState.STOPPED;
        endMirror(); decoder.stopSession();
        final int clearingGeneration = generation;
        main.post(() -> {
            if (generation == clearingGeneration) listener.onSessionCleared(resources.getString(R.string.tv_receiver_stopped));
        });
        // Never hold gate while joining native callbacks: callbacks themselves can need gate.
        for (NsdManager.RegistrationListener registration : registrations) {
            try { nsd.unregisterService(registration); } catch (RuntimeException ignored) {}
        }
        registrations.clear();
        final int stoppedGeneration = generation;
        main.post(() -> {
            if (focusRequest != null && focusGeneration < stoppedGeneration) {
                audio.abandonAudioFocusRequest(focusRequest); focusRequest = null; focusGeneration = -1;
            }
        });
        if (previous != 0) {
            try {
                NativeBridge.INSTANCE.nativeUpdatePlaybackInfo(previous, 0, -1, 0, true);
                NativeBridge.INSTANCE.nativeStop(previous);
            }
            finally { NativeBridge.INSTANCE.nativeDestroy(previous); }
        }
        boundPort = 0;
        if (multicast != null) {
            if (multicast.isHeld()) multicast.release(); multicast = null;
        }
    }
    public int getPort() { return boundPort; }
    /** Sticky until a subsequent codec release succeeds; safe to query without the codec lock. */
    public boolean hasVideoReleaseFailure() { return decoder.releaseFailed(); }
    public RuntimeStats getRuntimeStats() { return new RuntimeStats(this); }
    public void setWatchdogTimeoutSeconds(int seconds) { watchdog.setTimeoutSeconds(seconds); }
    public void setPlaybackMode(PlaybackMode mode) {
        if (mode == null) throw new IllegalArgumentException("Playback mode required");
        playbackMode = mode;
        decoder.configureTiming(mode == PlaybackMode.MOVIE, effectiveVideoCushionMs());
        applyAudioConfiguration();
    }
    /** Real native jitter prebuffer, not a signed audio-sync offset. 0 selects adaptive buffering. */
    public void setAudioBufferMs(int milliseconds) {
        if (milliseconds < 0 || milliseconds > 500 || milliseconds % 50 != 0)
            throw new IllegalArgumentException("Audio buffer must be 0–500 ms in 50 ms steps");
        audioBufferMs = milliseconds;
        decoder.configureTiming(playbackMode == PlaybackMode.MOVIE, effectiveVideoCushionMs());
        applyAudioConfiguration();
    }
    public void useModeDefaultAudioBuffer() {
        audioBufferMs = -1;
        decoder.configureTiming(playbackMode == PlaybackMode.MOVIE, effectiveVideoCushionMs());
        applyAudioConfiguration();
    }
    /** PIN mode is fixed at nativeInit. The owner must restart on its serial executor if true. */
    public boolean requestPinMode(boolean required) {
        boolean changed = pinRequired != required; pinRequired = required;
        return changed && running;
    }
    /** Explicit destructive UI action only. Automatic recovery must never call this method.
     * Rotates receiver identity, not a per-device revoke (the ABI has no peer database).
     */
    public void resetIdentityAfterConfirmation(boolean confirmed) throws java.io.IOException {
        if (!confirmed) throw new SecurityException("Explicit identity-reset confirmation required");
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Reset identity on the serial lifecycle worker");
        synchronized (gate) {
            if (running || handle != 0) throw new IllegalStateException("Stop AirPlay before resetting identity");
            File identity = new File(context.getFilesDir(), "airplay-identity.pem");
            if (identity.exists() && !identity.delete()) throw new java.io.IOException("Receiver identity could not be removed");
            if (!context.getSharedPreferences("airplay", Context.MODE_PRIVATE).edit().remove("identity").commit())
                throw new java.io.IOException("Receiver address could not be reset");
        }
    }
    private int effectiveAudioBufferMs() {
        return audioBufferMs >= 0 ? audioBufferMs : playbackMode == PlaybackMode.MOVIE ? 150 : 0;
    }
    private int effectiveVideoCushionMs() {
        return playbackMode == PlaybackMode.MOVIE ? effectiveAudioBufferMs() : 0;
    }
    private boolean configureNativeAudio(long nativeHandle) {
        boolean lowLatency = playbackMode == PlaybackMode.LOW_LATENCY;
        return NativeBridge.INSTANCE.nativeServerAudioConfigure(nativeHandle, effectiveAudioBufferMs(),
                95, 0, false, true, lowLatency, false);
    }
    private void applyAudioConfiguration() {
        final int expected = generation;
        audioControl.execute(() -> {
            synchronized (gate) {
                if (handle != 0 && generation == expected && !configureNativeAudio(handle))
                    status(Status.PROBLEM, R.string.tv_airplay_audio_configuration_unavailable);
            }
        });
    }
    private void pollAudioStats() {
        final int expected = generation;
        audioControl.execute(() -> {
            synchronized (gate) {
                if (handle == 0 || generation != expected) return;
                audioDebug.clear();
                if (NativeBridge.INSTANCE.nativeServerAudioDebug(handle, audioDebug))
                    audioStats = NativeAudioStats.read(audioDebug);
            }
        });
    }
    private void requestRecovery(String reason, boolean problem) {
        ReceiverDiagnostics.event(problem?"decoder-recovery":"session-recovery",generation);
        final boolean wasMirroring;
        synchronized (mirrorGate) {
            if (!running || recoveryPending) return;
            recoveryPending = true; sessionState = SessionState.RECOVERING;
            wasMirroring = mirroring; mirroring = mirrorReady = mirrorUiReady = false;
            mirrorEpoch++; ReceiverDiagnostics.event("mirror-epoch",mirrorEpoch); startupFrames.clear(); startupBytes = 0;
        }
        // The owner's lifecycle worker releases MediaCodec/native sockets, never this callback.
        audioDesired = false; controlAudio(); watchdog.endMirror();
        post(() -> {
            if (wasMirroring) listener.onMirrorStopped();
            listener.onSessionCleared(reason); listener.onRecoveryRequired(reason);
        });
        status(problem ? Status.PROBLEM : Status.RECOVERING, R.string.tv_airplay_reconnecting, reason);
    }
    public boolean isRunning() { return running; }
    public void setSurface(Surface surface) {
        decoder.setSurface(surface);
        synchronized (mirrorGate) { surfaceAvailable = surface != null && surface.isValid(); }
        drainStartup();
    }
    public void clearSurface() {
        synchronized (mirrorGate) { surfaceAvailable = false; mirrorReady = false; }
        decoder.setSurface(null);
    }
    public long getDecodedFrames() { return decoder.frames(); }
    public long getDroppedFrames() { return decoder.drops(); }
    public void updatePlaybackInfo(float position, float duration, float rate, boolean ready) {
        synchronized (gate) {
            if (handle != 0) NativeBridge.INSTANCE.nativeUpdatePlaybackInfo(handle, position, duration, rate, ready);
        }
    }
    private int audioProperty(String key) {
        try { return Integer.parseInt(audio.getProperty(key)); } catch (RuntimeException ignored) { return 0; }
    }
    private byte[] installationAddress() {
        SharedPreferences prefs = context.getSharedPreferences("airplay", Context.MODE_PRIVATE);
        String value = prefs.getString("identity", null);
        byte[] address = new byte[6];
        if (value != null && value.matches("[0-9a-f]{12}")) {
            for (int i = 0; i < 6; i++) address[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        } else {
            new SecureRandom().nextBytes(address); address[0] = (byte) ((address[0] & 0xfc) | 2);
            StringBuilder encoded = new StringBuilder();
            for (byte item : address) encoded.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
            prefs.edit().putString("identity", encoded.toString()).apply();
        }
        return address;
    }
    private void register(String name, String type, int port, Map<String, String> records) {
        NsdServiceInfo info = new NsdServiceInfo(); info.setServiceName(name); info.setServiceType(type); info.setPort(port);
        if (records != null) for (Map.Entry<String, String> record : records.entrySet()) info.setAttribute(record.getKey(), record.getValue());
        final int registeredGeneration = generation;
        NsdManager.RegistrationListener registration = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo service) {
                if (registeredGeneration == generation && running && type.equals("_airplay._tcp")) status(Status.READY, R.string.tv_airplay_ready);
            }
            @Override public void onRegistrationFailed(NsdServiceInfo service, int error) {
                if (registeredGeneration == generation && running) status(Status.PROBLEM, R.string.tv_airplay_discovery_failed, error);
            }
            @Override public void onServiceUnregistered(NsdServiceInfo service) {}
            @Override public void onUnregistrationFailed(NsdServiceInfo service, int error) {}
        };
        registrations.add(registration); nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration);
    }
    private void post(Runnable action) {
        int expected = generation;
        main.post(() -> { if (generation == expected && running) action.run(); });
    }
    private void status(Status status, int resource, Object... arguments) {
        String message = resources.getString(resource, arguments);
        if (message.equals(lastStatus)) return;
        lastStatus = message; post(() -> listener.onStatus(status, message));
    }
    private void beginMirror() {
        final int expectedMirror;
        synchronized (mirrorGate) {
            if (mirroring || !running || recoveryPending) return;
            mirroring = true; mirrorReady = mirrorUiReady = false; expectedMirror = ++mirrorEpoch;
            sessionState = SessionState.MIRRORING;
            watchdog.beginMirror(SystemClock.elapsedRealtime());
        }
        post(() -> {
            synchronized (mirrorGate) { if (!mirroring || mirrorEpoch != expectedMirror || recoveryPending) return; }
            listener.onMirrorStarted();
            synchronized (mirrorGate) { if (mirroring && mirrorEpoch == expectedMirror) mirrorUiReady = true; }
            drainStartup();
        });
    }
    /** Drain startup immediately after UI/surface readiness, even if sender then stays static.
     * Keep mirrorReady false until every older packet has been fed, preserving codec order.
     */
    private void drainStartup() {
        final int expectedGeneration, expectedMirror;
        synchronized (mirrorGate) {
            if (!running || recoveryPending || !mirroring || !mirrorUiReady || !surfaceAvailable || startupScheduled) return;
            startupScheduled = true; expectedGeneration = generation; expectedMirror = mirrorEpoch;
        }
        videoBootstrap.execute(() -> {
            try {
                while (true) {
                    Frame frame;
                    synchronized (mirrorGate) {
                        if (generation != expectedGeneration || mirrorEpoch != expectedMirror || !running
                                || recoveryPending || !mirroring || !surfaceAvailable) return;
                        frame = startupFrames.poll();
                        if (frame == null) { mirrorReady = true; return; }
                        startupBytes -= frame.bytes.length;
                    }
                    synchronized (decoder) {
                        if (generation != expectedGeneration || mirrorEpoch != expectedMirror || !running || recoveryPending) return;
                        decoder.feed(frame.bytes, frame.ntp, frame.hevc);
                    }
                }
            } finally {
                boolean queued;
                synchronized (mirrorGate) { startupScheduled = false; queued = !startupFrames.isEmpty(); }
                // A newer session may have reached readiness while the old task was finishing.
                if (queued) drainStartup();
            }
        });
    }
    private void endMirror() {
        final boolean wasMirroring;
        final int endedMirror;
        synchronized (mirrorGate) {
            wasMirroring = mirroring; mirroring = mirrorReady = mirrorUiReady = false; endedMirror = ++mirrorEpoch;
            startupFrames.clear(); startupBytes = 0;
        }
        watchdog.endMirror(); decoder.stopSession();
        if (wasMirroring) {
            if (running && !recoveryPending) sessionState = SessionState.READY;
            post(() -> {
                synchronized (mirrorGate) { if (mirrorEpoch != endedMirror || mirroring) return; }
                listener.onMirrorStopped(); listener.onSessionCleared(resources.getString(R.string.tv_mirror_ended));
            });
        }
    }
    @Override public void onVideoData(byte[] bytes, long ntp, boolean hevc) {
        if (!running || recoveryPending || bytes == null || bytes.length < 5 || bytes.length > 16 * 1024 * 1024) return;
        beginMirror();
        boolean vcl=MirrorDecoder.hasVcl(bytes,hevc);
        watchdog.videoPacket(SystemClock.elapsedRealtime(),vcl);
        if(vcl){long count=++diagnosticPackets;if(count==1||count%600==0)ReceiverDiagnostics.event("received-vcl",count);}
        final int expectedGeneration = generation, expectedMirror;
        synchronized (mirrorGate) {
            expectedMirror = mirrorEpoch;
            if (!mirrorReady) {
                if (startupBytes + bytes.length <= 4 * 1024 * 1024) {
                    startupFrames.add(new Frame(bytes, ntp, hevc)); startupBytes += bytes.length;
                }
                return;
            }
        }
        synchronized (decoder) {
            if (generation == expectedGeneration && mirrorEpoch == expectedMirror && running && !recoveryPending)
                decoder.feed(bytes, ntp, hevc);
        }
    }

    @Override public void onAudioFormat(int ct, int spf, boolean usingScreen) {
        synchronized (gate) {
            if (handle == 0 || recoveryPending) return;
            audioCodecType = ct; audioSamplesPerFrame = spf; audioDesired = true; audioUsingScreen = usingScreen;
        }
        if (usingScreen) beginMirror(); else sessionState = SessionState.AUDIO_ONLY;
        post(() -> {
            if (!audioDesired || recoveryPending) return;
            // Relinquish the app's other media session before native audio takes focus.
            if (!usingScreen) listener.onAudioStarted();
            if (focusRequest == null || focusGeneration != generation) {
                if (focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
                final int requestGeneration = generation; focusGeneration = requestGeneration;
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(change -> {
                        if (!running || requestGeneration != generation) return;
                        audioHasFocus = change == AudioManager.AUDIOFOCUS_GAIN;
                        if (change == AudioManager.AUDIOFOCUS_LOSS) audioDesired = false;
                        controlAudio();
                        if (!audioHasFocus && running) status(Status.INFORMATION, R.string.tv_airplay_audio_paused);
                    }, main).build();
            }
            audioHasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            controlAudio();
            if (!audioHasFocus) status(Status.INFORMATION, R.string.tv_airplay_audio_waiting);
        });
    }
    private void controlAudio() {
        final int expected = generation;
        audioControl.execute(() -> {
            synchronized (gate) {
                if (handle == 0 || generation != expected) return;
                if (audioDesired && audioHasFocus) {
                    audioOutputActive = NativeBridge.INSTANCE.nativeServerAudioStart(handle);
                    if (!audioOutputActive) {
                        status(Status.PROBLEM, R.string.tv_airplay_audio_output_unavailable); return;
                    }
                    NativeBridge.INSTANCE.nativeServerAudioFormat(handle, audioCodecType, audioSamplesPerFrame);
                } else {
                    NativeBridge.INSTANCE.nativeServerAudioStop(handle); audioOutputActive = false;
                }
            }
        });
    }
    @Override public void onVideoSize(float sw, float sh, float w, float h) {
        if (!running || w <= 0 || h <= 0) return;
        decoder.setResolution(Math.round(w), Math.round(h), requestedFps);
        post(() -> listener.onVideoSize(Math.round(w), Math.round(h)));
    }
    @Override public void onVolumeChange(float db) {
        // Keep the TV's physical volume under its remote. Native audio playback remains available.
        // Changing global TV volume from a sender without explicit UI permission is intentionally omitted.
    }
    @Override public float onClientVolume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        return current == 0 || max == 0 ? -144f : -30f + 30f * current / max;
    }
    @Override public void onAudioTeardown() {
        if (!running) return;
        audioDesired = false;
        if (sessionState == SessionState.AUDIO_ONLY) {
            sessionState = SessionState.READY; post(() -> listener.onSessionCleared(resources.getString(R.string.tv_audio_ended)));
        }
        controlAudio();
        post(() -> { if (focusRequest != null) audio.abandonAudioFocusRequest(focusRequest); audioHasFocus = false; });
    }
    @Override public void onConnectionInit() {ReceiverDiagnostics.event("connection-open",generation); if (running) watchdog.connectionOpened(); }
    @Override public void onConnectionDestroy() {
        ReceiverDiagnostics.event("connection-close",generation);
        if (running) watchdog.connectionClosed(SystemClock.elapsedRealtime());
    }
    @Override public void onConnectionReset(int reason) {ReceiverDiagnostics.event("native-reset",reason); requestRecovery(resources.getString(R.string.tv_native_session_reset, reason), false); }
    @Override public void onDisplayPin(String pin) { post(() -> listener.onPin(pin)); }
    @Override public void onMetadata(byte[] data) {}
    @Override public void onCoverArt(byte[] data) {}
    @Override public void onProgress(long start, long current, long end) {}
    @Override public void onDacpId(String id, String remote) {}
    @Override public void onMirrorRunning(boolean active) {
        ReceiverDiagnostics.event("native-mirror",active?1:0);
        if (!running || recoveryPending) return;
        if (active) beginMirror(); else { endMirror(); if (audioUsingScreen) onAudioTeardown(); }
    }
    @Override public void onVideoPlay(String url, float start) {
        if (!running || recoveryPending) return;
        endMirror(); sessionState = SessionState.MEDIA_URL; post(() -> listener.onVideoPlay(url, start));
    }
    @Override public void onVideoScrub(float seconds) { post(() -> listener.onVideoScrub(seconds)); }
    @Override public void onVideoRate(float rate) { post(() -> listener.onVideoRate(rate)); }
    @Override public void onVideoStop() {
        if (running && !recoveryPending) sessionState = SessionState.READY;
        post(listener::onVideoStop);
    }
    @Override public void onVideoSessionPoll() { /* host periodically supplies playback info */ }
    @Override public void onLog(String message) { /* Do not forward native URLs or pairing identities to UI. */ }
}
