/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.airplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.MediaFormat;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.io.File;
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
    public interface Listener {
        void onStatus(String status);
        void onVideoSize(int width, int height);
        void onVideoPlay(String location, float startPositionSeconds);
        void onVideoScrub(float positionSeconds);
        void onVideoRate(float rate);
        void onVideoStop();
        default void onMirrorStarted() {}
        default void onMirrorStopped() {}
        default void onPin(String pin) {}
        default void onAudioStarted() {}
    }
    private final Context context;
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
    private volatile boolean audioDesired, audioHasFocus;
    private int audioCodecType, audioSamplesPerFrame;
    private final List<NsdManager.RegistrationListener> registrations = new ArrayList<>();
    private final ArrayDeque<Frame> startupFrames = new ArrayDeque<>();
    private WifiManager.MulticastLock multicast;
    private long handle;
    private volatile boolean running;
    private volatile int boundPort;
    private volatile int generation;
    private boolean mirroring, mirrorReady;
    private int startupBytes, requestedFps = 60;
    private volatile String lastStatus = "";
    private static final class Frame {
        final byte[] bytes; final long ntp; final boolean hevc;
        Frame(byte[] b, long n, boolean h) { bytes = b; ntp = n; hevc = h; }
    }
    public AirPlayEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        audio = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        nsd = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
        decoder = new MirrorDecoder(new MirrorDecoder.Events() {
            @Override public void size(int w, int h) { post(() -> listener.onVideoSize(w, h)); }
            @Override public void failure() { status("AirPlay decoder error; reconnect the sender"); }
        });
    }
    public void start(String name, int width, int height, int fps) throws Exception {
        start(name, width, height, fps, true);
    }
    public void start(String name, int width, int height, int fps, boolean requirePin) throws Exception {
        if (running) return;
        name = name == null || name.trim().isEmpty() ? "MoonCast TV" : name.trim();
        while (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60)
            name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Start AirPlay on a background executor");
        generation++; lastStatus = ""; audioDesired = false;
        if (!MirrorDecoder.supports(MediaFormat.MIMETYPE_VIDEO_AVC))
            throw new IllegalStateException("This device has no H.264 decoder");
        width = Math.max(640, Math.min(width, 3840)); height = Math.max(360, Math.min(height, 2160));
        requestedFps = Math.max(24, Math.min(fps, 60));
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
            bridge.nativeSetLang(created, "", "", context.getResources().getConfiguration().getLocales().toLanguageTags().replace(',', ':'));
            bridge.nativeSetPlist(created, "maxFPS", requestedFps);
            bridge.nativeSetPlist(created, "overscanned", 0);
            bridge.nativeSetDisplaySize(created, width, height, requestedFps);
            bridge.nativeServerAudioConfigure(created, 0, 95, 0, false, true, true, false);
            int port = bridge.nativeStart(created, 7000);
            boundPort = port;
            if (port < 1) throw new IllegalStateException("AirPlay could not listen on TCP 7000");
            register(bridge.nativeGetRaopServiceName(created), "_raop._tcp", port, bridge.nativeGetRaopTxtRecords(created));
            register(bridge.nativeGetServerName(created), "_airplay._tcp", port, bridge.nativeGetAirplayTxtRecords(created));
            status("AirPlay listening; discovery registering");
        } catch (Exception | LinkageError failure) {
            stop();
            throw new IllegalStateException("AirPlay startup failed", failure);
        }
    }
    public void stop() {
        long previous;
        audioDesired = false; audioHasFocus = false;
        synchronized (gate) { running = false; previous = handle; handle = 0; generation++; }
        // Never hold gate while joining native callbacks: callbacks themselves can need gate.
        for (NsdManager.RegistrationListener registration : registrations) {
            try { nsd.unregisterService(registration); } catch (RuntimeException ignored) {}
        }
        registrations.clear();
        final int stoppedGeneration = generation;
        main.post(() -> {
            if (generation == stoppedGeneration && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        });
        if (previous != 0) {
            try {
                NativeBridge.INSTANCE.nativeUpdatePlaybackInfo(previous, 0, -1, 0, true);
                NativeBridge.INSTANCE.nativeStop(previous);
            }
            finally { NativeBridge.INSTANCE.nativeDestroy(previous); }
        }
        boundPort = 0;
        endMirror(); decoder.stopSession();
        if (multicast != null) {
            if (multicast.isHeld()) multicast.release(); multicast = null;
        }
    }
    public int getPort() { return boundPort; }
    public boolean isRunning() { return running; }
    public void setSurface(Surface surface) { decoder.setSurface(surface); }
    public void clearSurface() { decoder.setSurface(null); }
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
                if (registeredGeneration == generation && running && type.equals("_airplay._tcp")) status("AirPlay ready");
            }
            @Override public void onRegistrationFailed(NsdServiceInfo service, int error) {
                if (registeredGeneration == generation && running) status("AirPlay discovery failed (" + error + ")");
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
    private void status(String message) {
        if (message.equals(lastStatus)) return;
        lastStatus = message; post(() -> listener.onStatus(message));
    }
    private void beginMirror() {
        synchronized (mirrorGate) {
            if (mirroring) return;
            mirroring = true; mirrorReady = false;
        }
        post(() -> {
            listener.onMirrorStarted();
            synchronized (mirrorGate) { if (mirroring) mirrorReady = true; }
        });
    }
    private void endMirror() {
        boolean wasMirroring;
        synchronized (mirrorGate) {
            wasMirroring = mirroring; mirroring = false; mirrorReady = false;
            startupFrames.clear(); startupBytes = 0;
        }
        decoder.stopSession();
        if (wasMirroring) post(listener::onMirrorStopped);
    }
    @Override public void onVideoData(byte[] bytes, long ntp, boolean hevc) {
        if (!running) return;
        beginMirror();
        List<Frame> buffered = new ArrayList<>();
        synchronized (mirrorGate) {
            if (!mirrorReady) {
                if (startupBytes + bytes.length <= 4 * 1024 * 1024) {
                    startupFrames.add(new Frame(bytes, ntp, hevc)); startupBytes += bytes.length;
                }
                return;
            }
            while (!startupFrames.isEmpty()) buffered.add(startupFrames.remove());
            startupBytes = 0;
        }
        for (Frame frame : buffered) decoder.feed(frame.bytes, frame.ntp, frame.hevc);
        decoder.feed(bytes, ntp, hevc);
    }
    @Override public void onAudioFormat(int ct, int spf, boolean usingScreen) {
        synchronized (gate) {
            if (handle == 0) return;
            audioCodecType = ct; audioSamplesPerFrame = spf; audioDesired = true;
        }
        if (usingScreen) beginMirror();
        post(() -> {
            // Relinquish the app's other media session before native audio takes focus.
            if (!usingScreen) listener.onAudioStarted();
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(change -> {
                        audioHasFocus = change == AudioManager.AUDIOFOCUS_GAIN;
                        if (change == AudioManager.AUDIOFOCUS_LOSS) audioDesired = false;
                        controlAudio();
                        if (!audioHasFocus && running) status("AirPlay audio paused: another app has audio focus");
                    }, main).build();
            }
            audioHasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            controlAudio();
            if (!audioHasFocus) status("AirPlay audio waiting for audio focus");
        });
    }
    private void controlAudio() {
        final int expected = generation;
        audioControl.execute(() -> {
            synchronized (gate) {
                if (handle == 0 || generation != expected) return;
                if (audioDesired && audioHasFocus) {
                    if (!NativeBridge.INSTANCE.nativeServerAudioStart(handle)) {
                        status("AirPlay audio output unavailable"); return;
                    }
                    NativeBridge.INSTANCE.nativeServerAudioFormat(handle, audioCodecType, audioSamplesPerFrame);
                } else NativeBridge.INSTANCE.nativeServerAudioStop(handle);
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
        audioDesired = false;
        controlAudio();
        post(() -> { if (focusRequest != null) audio.abandonAudioFocusRequest(focusRequest); audioHasFocus = false; });
    }
    @Override public void onConnectionInit() { /* also fires for local HLS proxy requests */ }
    @Override public void onConnectionDestroy() { /* TCP close alone is not mirror-session end */ }
    @Override public void onConnectionReset(int reason) { endMirror(); }
    @Override public void onDisplayPin(String pin) { post(() -> listener.onPin(pin)); }
    @Override public void onMetadata(byte[] data) {}
    @Override public void onCoverArt(byte[] data) {}
    @Override public void onProgress(long start, long current, long end) {}
    @Override public void onDacpId(String id, String remote) {}
    @Override public void onMirrorRunning(boolean active) { if (active) beginMirror(); else endMirror(); }
    @Override public void onVideoPlay(String url, float start) {
        endMirror(); post(() -> listener.onVideoPlay(url, start));
    }
    @Override public void onVideoScrub(float seconds) { post(() -> listener.onVideoScrub(seconds)); }
    @Override public void onVideoRate(float rate) { post(() -> listener.onVideoRate(rate)); }
    @Override public void onVideoStop() { post(listener::onVideoStop); }
    @Override public void onVideoSessionPoll() { /* host periodically supplies playback info */ }
    @Override public void onLog(String message) { /* Do not forward native URLs or pairing identities to UI. */ }
}
