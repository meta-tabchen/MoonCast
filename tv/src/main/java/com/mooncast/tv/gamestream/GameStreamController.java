// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.http.ComputerDetails;
import com.mooncast.receiver.MoonlightHostClient;
import com.mooncast.receiver.PreparedDesktop;
import com.mooncast.receiver.ReceiverSession;
import com.mooncast.receiver.StreamSpec;
import com.mooncast.tv.gamestream.hosts.AndroidPairingIdentity;
import com.mooncast.tv.gamestream.hosts.HostRecord;
import com.mooncast.tv.gamestream.render.GameStreamAudioRenderer;
import com.mooncast.tv.gamestream.render.GameStreamVideoRenderer;
import com.mooncast.tv.net.LanAccess;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** View-only GameStream owner. All public mutations are main-thread, all protocol work is off-main. */
public final class GameStreamController {
    // Process-wide: Activity recreation must not forget an unreleased native/audio owner.
    private static volatile CompletableFuture<Void> globalReleased = CompletableFuture.completedFuture(null);
    public static CompletableFuture<Void> globalReleaseBarrier() { return globalReleased; }
    private CompletableFuture<Void> activeLease;
    public enum State { CONNECTING, ACTIVE, STOPPED, FAILED }
    public interface Listener {
        void onState(State state, String code);
        void onVideoSize(int width, int height);
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AudioManager audioManager;
    private final Listener listener;
    private final Object lock = new Object();
    private AudioFocusRequest focus;
    private volatile long epoch;
    private boolean closed, owned;
    private MoonlightHostClient client;
    private ReceiverSession session;
    private volatile GameStreamVideoRenderer video;
    private volatile GameStreamAudioRenderer audio;
    private CompletableFuture<Void> lastStop = CompletableFuture.completedFuture(null);

    public GameStreamController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    }
    private static void mainOnly() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Main thread required");
    }
    public GameStreamVideoRenderer video() { return video; }
    public GameStreamAudioRenderer audio() { return audio; }

    /** Caller first successfully releases all other protocol/audio owners. Never creates pairing keys. */
    public void connect(HostRecord host, LanAccess lan, StreamSpec spec, Surface surface) {
        mainOnly();
        if (!globalReleased.isDone() || globalReleased.isCompletedExceptionally()) throw new IllegalStateException("Another stream still owns output");
        if (owned) throw new IllegalStateException("A stream is already connecting or active");
        if (closed || surface == null || !surface.isValid()) throw new IllegalStateException("Video surface is unavailable");
        host.endpoint.requireOnLan(lan);
        if (!host.isPaired()) throw new IllegalArgumentException("Pair this host first");
        if (!lastStop.isDone() || lastStop.isCompletedExceptionally()) throw new IllegalStateException("Previous stream is not safely released");
        synchronized (lock) { if (session != null || client != null) throw new IllegalStateException("A stream is already active"); }
        owned = true; activeLease = new CompletableFuture<>(); globalReleased = activeLease;
        final long token = ++epoch;
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change != AudioManager.AUDIOFOCUS_GAIN && token == epoch) fail(token, "AUDIO_FOCUS_LOST");
                }, main).build();
        if (audioManager == null || audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            abandonFocus(); owned = false; activeLease.complete(null); throw new IllegalStateException("Audio focus was not granted");
        }
        video = new GameStreamVideoRenderer(surface, new GameStreamVideoRenderer.Listener() {
            @Override public void onVideoSizeChanged(int width, int height) { deliver(token, () -> listener.onVideoSize(width, height)); }
            @Override public void onError(String ignored) { fail(token, "VIDEO_OUTPUT_FAILED"); }
        });
        audio = new GameStreamAudioRenderer(new GameStreamAudioRenderer.Listener() {
            @Override public void onError(String ignored) { fail(token, "AUDIO_OUTPUT_FAILED"); }
        });
        final GameStreamVideoRenderer outputVideo = video;
        final GameStreamAudioRenderer outputAudio = audio;
        listener.onState(State.CONNECTING, "CONNECTING");
        worker.execute(() -> {
            try {
                AndroidPairingIdentity identity = AndroidPairingIdentity.loadExisting(context);
                if (identity == null) throw new IllegalStateException("Pairing identity is absent");
                MoonlightHostClient operation = new MoonlightHostClient(context, identity, identity.getClientId());
                synchronized (lock) {
                    if (token != epoch) { operation.close(); return; }
                    client = operation;
                }
                PreparedDesktop desktop = operation.prepareDesktop(new ComputerDetails.AddressTuple(host.endpoint.address, host.endpoint.httpPort),
                        host.httpsPort, host.pinnedCertificate, spec);
                if (token != epoch) return;
                ReceiverSession started = desktop.start(outputAudio, outputVideo, callbacks(token));
                // Do not hold the UI cancellation lock through even a nominally asynchronous
                // provider start. The queued stop task runs after this worker assignment.
                synchronized (lock) { session = started; }
            } catch (Exception | LinkageError failure) { fail(token, "CONNECTION_FAILED"); }
        });
    }
    public void setSurface(Surface surface) {
        mainOnly(); GameStreamVideoRenderer current = video; if (current != null) current.setSurface(surface);
    }
    private void fail(long token, String code) {
        main.post(() -> {
            if (closed || token != epoch) return;
            CompletableFuture<Void> stopped = stopAsync();
            long stoppedEpoch = epoch;
            stopped.whenComplete((ignored, failure) -> main.post(() -> {
                if (!closed && epoch == stoppedEpoch) listener.onState(State.FAILED, failure == null ? code : "RELEASE_PENDING");
            }));
        });
    }
    private void deliver(long token, Runnable action) { main.post(() -> { if (!closed && token == epoch) action.run(); }); }
    private NvConnectionListener callbacks(long token) {
        return new NvConnectionListener() {
            public void stageStarting(String stage) { }
            public void stageComplete(String stage) { }
            public void stageFailed(String stage, int ports, int code) { fail(token, "STAGE_FAILED_" + code); }
            public void connectionStarted() { deliver(token, () -> listener.onState(State.ACTIVE, "ACTIVE")); }
            public void connectionTerminated(int code) { fail(token, "SESSION_ENDED_" + code); }
            public void connectionStatusUpdate(int status) { }
            // Host text is untrusted and must not be rendered as an instruction or logged with credentials.
            public void displayMessage(String message) { }
            public void displayTransientMessage(String message) { }
            public void rumble(short n, short low, short high) { }
            public void rumbleTriggers(short n, short left, short right) { }
            public void setHdrMode(boolean enabled, byte[] metadata) { if (enabled) fail(token, "HDR_UNSUPPORTED"); }
            public void setMotionEventState(short n, byte type, short rate) { }
            public void setControllerLED(short n, byte red, byte green, byte blue) { }
        };
    }
    /** Successful completion is the ownership barrier for another audio/video protocol. */
    public CompletableFuture<Void> stopAsync() {
        mainOnly(); ++epoch;
        if (!owned) return globalReleased;
        GameStreamAudioRenderer promptlyStopAudio = audio; if (promptlyStopAudio != null) promptlyStopAudio.stop();
        MoonlightHostClient cancelling; synchronized (lock) { cancelling = client; }
        if (cancelling != null) cancelling.close();
        if (!lastStop.isDone() || lastStop.isCompletedExceptionally()) return lastStop;
        CompletableFuture<Void> completion = activeLease; lastStop = completion;
        final AudioFocusRequest oldFocus = focus; focus = null;
        worker.execute(() -> {
            ReceiverSession previous;
            synchronized (lock) { previous = session; session = null; if (client != null) client.close(); client = null; }
            CompletableFuture<Void> released;
            if (previous != null) released = previous.stopAsync();
            else {
                released = new CompletableFuture<>();
                try {
                    GameStreamVideoRenderer v = video; GameStreamAudioRenderer a = audio;
                    if (v != null) { v.stop(); v.cleanup(); }
                    if (a != null) { a.stop(); a.cleanup(); }
                    if (v != null && !v.awaitReleased(15000) || a != null && !a.awaitReleased(15000))
                        throw new IllegalStateException("Renderer release pending");
                    released.complete(null);
                } catch (Exception e) { released.completeExceptionally(e); }
            }
            released.whenComplete((ignored, failure) -> {
                if (failure == null) {
                    main.post(() -> { if (oldFocus != null && audioManager != null) audioManager.abandonAudioFocusRequest(oldFocus); owned = false; completion.complete(null); });
                } else completion.completeExceptionally(failure);
            });
        });
        return completion;
    }
    private void abandonFocus() { if (focus != null && audioManager != null) audioManager.abandonAudioFocusRequest(focus); focus = null; }
    public void close() {
        mainOnly(); if (closed) return; closed = true;
        stopAsync().whenComplete((ignored, failure) -> worker.shutdown());
    }
}
