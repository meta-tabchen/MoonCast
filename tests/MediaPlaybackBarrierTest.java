package com.mooncast.tv.media;

import androidx.media3.exoplayer.audio.AudioSink;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Deterministic sink/playback-queue fakes against real Media3 interfaces; no device claims. */
public final class MediaPlaybackBarrierTest {
    private static int assertions;
    private static final AudioSink.AudioTrackConfig CONFIG =
            new AudioSink.AudioTrackConfig(2, 48000, 12, false, false, 4096);
    private static final class Fixture {
        final MediaPlaybackBarrier barrier = new MediaPlaybackBarrier();
        final ArrayDeque<Runnable> commands = new ArrayDeque<>();
        AudioSink.Listener events;
        int pauses, flushes, forwardedReleases;
        boolean throwPause, throwFlush;
        CountDownLatch pauseEntered, allowPause;
        final AudioSink sink = barrier.wrap((AudioSink) Proxy.newProxyInstance(
                AudioSink.class.getClassLoader(), new Class<?>[]{AudioSink.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setListener": events = (AudioSink.Listener) args[0]; return null;
                        case "play": return null;
                        case "pause":
                            pauses++;
                            if (pauseEntered != null) pauseEntered.countDown();
                            if (allowPause != null && !allowPause.await(5, TimeUnit.SECONDS))
                                throw new IllegalStateException("Test pause gate timed out");
                            if (throwPause) throw new IllegalStateException("Vendor pause failure");
                            return null;
                        case "flush":
                            flushes++;
                            if (throwFlush) throw new IllegalStateException("Vendor flush failure");
                            return null;
                        case "reset": case "release": return null;
                        default: throw new AssertionError("Unexpected sink call " + method.getName());
                    }
                }));
        Fixture() {
            sink.setListener(new AudioSink.Listener() {
                public void onPositionDiscontinuity() { }
                public void onUnderrun(int size, long duration, long elapsed) { }
                public void onSkipSilenceEnabledChanged(boolean enabled) { }
                public void onAudioTrackReleased(AudioSink.AudioTrackConfig config) { forwardedReleases++; }
            });
        }
        void initialize() { events.onAudioTrackInitialized(CONFIG); sink.play(); }
        void retire() { events.onAudioTrackReleased(CONFIG); }
        CompletableFuture<Void> stop() {
            return barrier.stopAsync(marker -> {
                commands.add(() -> { sink.pause(); sink.flush(); });
                commands.add(marker);
            });
        }
        void next() { commands.remove().run(); }
    }
    public static void main(String[] args) throws Exception {
        emptyPlayerWaitsForMarker(); trackRetirementWaitsForMarker(); markerWaitsForRetirement();
        multipleTracksAndRepeatedStop(); retirementAloneDoesNotProveInactivity();
        pauseAndFlushFailuresFailClosed(); hungPauseDoesNotHoldStateLock(); replayInvalidatesPause();
        ordinaryFlushPreservesPlaybackIntent();
        System.out.println("Media playback barrier synthetic tests passed (" + assertions + " assertions). "
                + "No AudioTrack, codec, or vendor-release success is claimed.");
    }
    private static void emptyPlayerWaitsForMarker() {
        Fixture f = new Fixture(); CompletableFuture<Void> stopped = f.stop();
        check(!stopped.isDone(), "stop must not synchronously acknowledge playback thread");
        f.next(); check(!stopped.isDone(), "sink flush alone is insufficient");
        f.next(); check(success(stopped), "unused player stops after marker");
    }
    private static void trackRetirementWaitsForMarker() {
        Fixture f = new Fixture(); f.initialize(); CompletableFuture<Void> stopped = f.stop();
        f.next(); f.retire(); check(!stopped.isDone(), "retirement before marker cannot complete");
        f.next(); check(success(stopped), "all three acknowledgements complete");
        check(f.forwardedReleases == 1, "sink listener events are forwarded");
    }
    private static void markerWaitsForRetirement() {
        Fixture f = new Fixture(); f.initialize(); CompletableFuture<Void> stopped = f.stop();
        f.next(); f.next(); check(!stopped.isDone(), "FIFO marker cannot skip asynchronous release");
        f.retire(); check(success(stopped), "retirement completes paused marker");
    }
    private static void multipleTracksAndRepeatedStop() {
        Fixture f = new Fixture(); f.initialize(); f.initialize();
        CompletableFuture<Void> first = f.stop(), second = f.stop();
        f.next(); f.next(); f.retire(); check(!first.isDone(), "every old track must retire");
        f.retire(); check(success(first), "first marker acknowledged");
        check(!second.isDone(), "each stop has its own FIFO marker");
        f.next(); f.next(); check(success(second), "repeated stop completes independently");
    }
    private static void retirementAloneDoesNotProveInactivity() {
        Fixture f = new Fixture(); f.initialize();
        CompletableFuture<Void> stopped = f.barrier.stopAsync(marker -> f.commands.add(marker));
        f.retire(); f.next();
        check(!stopped.isDone(), "finally/release callback is not a successful pause acknowledgement");
    }
    private static void pauseAndFlushFailuresFailClosed() {
        for (boolean pauseFailure : new boolean[]{true, false}) {
            Fixture f = new Fixture(); f.initialize();
            f.throwPause = pauseFailure; f.throwFlush = !pauseFailure;
            CompletableFuture<Void> stopped = f.stop();
            try { f.next(); throw new AssertionError("Expected vendor exception"); }
            catch (IllegalStateException expected) { }
            f.retire(); f.next();
            check(stopped.isCompletedExceptionally(), "failure plus retirement cannot authorize handoff");
            CompletableFuture<Void> again = f.barrier.stopAsync(Runnable::run);
            check(again.isCompletedExceptionally(), "failed owner is not silently rehabilitated");
        }
    }
    private static void hungPauseDoesNotHoldStateLock() throws Exception {
        Fixture f = new Fixture(); f.initialize();
        f.pauseEntered = new CountDownLatch(1); f.allowPause = new CountDownLatch(1);
        CompletableFuture<Void> stopped = f.stop();
        Runnable stopCommand = f.commands.remove();
        Thread playback = new Thread(stopCommand, "synthetic-media-playback"); playback.start();
        check(f.pauseEntered.await(1, TimeUnit.SECONDS), "vendor pause entered");
        f.retire(); // Must remain responsive even while the vendor operation is blocked.
        check(!stopped.isDone(), "hung pause stays pending");
        f.allowPause.countDown(); playback.join(1000);
        check(!playback.isAlive(), "test playback worker terminates");
        check(!stopped.isDone(), "unprocessed marker remains required");
        f.next(); check(success(stopped), "confirmed inactivity and marker complete");
    }
    private static void replayInvalidatesPause() {
        Fixture f = new Fixture(); f.initialize(); CompletableFuture<Void> stopped = f.stop();
        f.next(); f.next(); f.sink.play(); f.retire();
        check(!stopped.isDone(), "a newer play invalidates the earlier inactive acknowledgement");
        f.sink.pause(); check(success(stopped), "a later successful pause restores inactivity");
    }
    private static void ordinaryFlushPreservesPlaybackIntent() {
        Fixture f = new Fixture(); f.initialize(); f.sink.flush();
        check(f.pauses == 0, "ordinary seek/format flush must not inject sink.pause");
        check(f.flushes == 1, "ordinary flush is forwarded exactly once");
    }
    private static boolean success(CompletableFuture<Void> future) {
        return future.isDone() && !future.isCompletedExceptionally();
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
