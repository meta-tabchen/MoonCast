package com.mooncast.tv.media;

import android.os.Looper;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * One player's stop acknowledgement, including its asynchronous AudioTrack retirement.
 * Stop and the FIFO marker run on the player's playback looper; no UI-thread wait occurs.
 * The owner must not prepare/play this player again until it has selected that source again.
 *
 * Success means a successful sink pause/flush, no outstanding track-retirement callbacks,
 * and a processed stop command. Media3 1.5.1 notifies track retirement in finally, so this
 * is NOT proof that a throwing vendor release freed hardware. A vendor pause/flush hang,
 * a missing callback, or a blocked playback looper never becomes permission to hand off.
 */
@UnstableApi
public final class MediaPlaybackBarrier {
    private final Object lock = new Object();
    private final List<StopRequest> stops = new ArrayList<>();
    private int tracks;
    private boolean inactive = true;
    private Throwable failure;
    private ExoPlayer owner;

    private static final class StopRequest {
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        boolean marker;
    }
    interface StopCommand { void stop(Runnable marker); }

    /** Invoke on the player's application thread. Await only off that thread. */
    public CompletableFuture<Void> stopAsync(ExoPlayer player) {
        Objects.requireNonNull(player);
        if (Looper.myLooper() != player.getApplicationLooper())
            throw new IllegalStateException("Stop must run on the player application thread");
        if (owner != null && owner != player)
            throw new IllegalArgumentException("A playback barrier belongs to one player");
        owner = player;
        return stopAsync(marker -> {
            player.stop();
            player.createMessage((type, payload) -> marker.run())
                    .setLooper(player.getPlaybackLooper()).send();
        });
    }

    // Same state machine is exercised by host tests with a deterministic playback queue.
    CompletableFuture<Void> stopAsync(StopCommand command) {
        StopRequest request = new StopRequest();
        synchronized (lock) { stops.add(request); }
        try { command.stop(() -> {
            synchronized (lock) { request.marker = true; }
            completeReady();
        }); }
        catch (RuntimeException | LinkageError error) { fail(error); }
        completeReady();
        return request.completion;
    }

    /** Factory hook. Track events are observed at the sink, before UI analytics dispatch. */
    AudioSink wrap(AudioSink delegate) {
        return new ForwardingAudioSink(delegate) {
            @Override public void setListener(AudioSink.Listener listener) {
                super.setListener(new AudioSink.Listener() {
                    @Override public void onPositionDiscontinuity() { listener.onPositionDiscontinuity(); }
                    @Override public void onPositionAdvancing(long time) { listener.onPositionAdvancing(time); }
                    @Override public void onUnderrun(int size, long duration, long elapsed) { listener.onUnderrun(size, duration, elapsed); }
                    @Override public void onSkipSilenceEnabledChanged(boolean enabled) { listener.onSkipSilenceEnabledChanged(enabled); }
                    @Override public void onOffloadBufferEmptying() { listener.onOffloadBufferEmptying(); }
                    @Override public void onOffloadBufferFull() { listener.onOffloadBufferFull(); }
                    @Override public void onAudioSinkError(Exception error) { fail(error); listener.onAudioSinkError(error); }
                    @Override public void onAudioCapabilitiesChanged() { listener.onAudioCapabilitiesChanged(); }
                    @Override public void onAudioTrackInitialized(AudioSink.AudioTrackConfig config) {
                        synchronized (lock) { tracks++; inactive = false; }
                        listener.onAudioTrackInitialized(config);
                    }
                    @Override public void onAudioTrackReleased(AudioSink.AudioTrackConfig config) {
                        synchronized (lock) {
                            if (tracks == 0) failure = new IllegalStateException("Unmatched AudioTrack retirement");
                            else tracks--;
                        }
                        // A released event acknowledges retirement, not vendor-release success.
                        completeReady();
                        listener.onAudioTrackReleased(config);
                    }
                    @Override public void onSilenceSkipped() { listener.onSilenceSkipped(); }
                });
            }
            @Override public void play() {
                synchronized (lock) { inactive = false; }
                guarded(super::play, false);
            }
            @Override public void pause() { guarded(super::pause, true); }
            // DefaultAudioSink.flush() synchronously pauses the old playing AudioTrack.
            // Do not insert an extra sink.pause(): normal seeks/format changes also flush,
            // and must preserve DefaultAudioSink's play-when-ready for the replacement track.
            @Override public void flush() { guarded(super::flush, true); }
            @Override public void reset() { guarded(super::reset, true); }
            @Override public void release() { guarded(super::release, false); }
            @Override public boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
                    throws AudioSink.InitializationException, AudioSink.WriteException {
                try { return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount); }
                catch (AudioSink.InitializationException | AudioSink.WriteException | RuntimeException | LinkageError error) {
                    fail(error); throw error;
                }
            }
        };
    }

    private void guarded(Runnable operation, boolean confirmsInactive) {
        try {
            operation.run();
            if (confirmsInactive) {
                synchronized (lock) { inactive = true; }
                completeReady();
            }
        } catch (RuntimeException | LinkageError error) { fail(error); throw error; }
    }
    private void fail(Throwable error) {
        synchronized (lock) { if (failure == null) failure = error; }
        completeReady();
    }
    private void completeReady() {
        List<StopRequest> ready = new ArrayList<>();
        Throwable error;
        synchronized (lock) {
            error = failure;
            for (StopRequest stop : stops) {
                if (error != null || stop.marker && tracks == 0 && inactive) ready.add(stop);
            }
            stops.removeAll(ready);
        }
        // Never run completion callbacks while holding a sink/state lock.
        for (StopRequest stop : ready) {
            if (error == null) stop.completion.complete(null);
            else stop.completion.completeExceptionally(error);
        }
    }
}
