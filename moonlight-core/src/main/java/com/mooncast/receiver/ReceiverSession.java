// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.receiver;

import com.limelight.LimeLog;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns native lifecycle. Stop is always on its own worker and is exactly once. */
public final class ReceiverSession implements AutoCloseable {
    public enum State { CONNECTING, ACTIVE, STOPPING, STOPPED, FAILED }
    private static final java.util.IdentityHashMap<Object, ReceiverSession> RENDERER_OWNERS = new java.util.IdentityHashMap<>();
    interface Driver {
        void start(AudioRenderer audio, VideoDecoderRenderer video, NvConnectionListener listener);
        void stop();
        boolean awaitStartupComplete(long timeoutMillis) throws InterruptedException;
    }
    private final Driver connection;
    private final AudioRenderer audio;
    private final VideoDecoderRenderer video;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final java.util.concurrent.atomic.AtomicReference<State> state =
            new java.util.concurrent.atomic.AtomicReference<>(State.CONNECTING);

    ReceiverSession(NvConnection connection, AudioRenderer audio, VideoDecoderRenderer video,
            NvConnectionListener listener) {
        this(new Driver() {
            public void start(AudioRenderer a, VideoDecoderRenderer v, NvConnectionListener l) { connection.start(a, v, l); }
            public void stop() { connection.stop(); }
            public boolean awaitStartupComplete(long ms) throws InterruptedException { return connection.awaitStartupComplete(ms); }
        }, audio, video, listener);
    }

    ReceiverSession(Driver connection, AudioRenderer audio, VideoDecoderRenderer video,
            NvConnectionListener listener) {
        this.connection = connection;
        this.audio = audio;
        this.video = video;
        synchronized (RENDERER_OWNERS) {
            if (RENDERER_OWNERS.containsKey(audio) || RENDERER_OWNERS.containsKey(video)) {
                throw new IllegalStateException("Renderers are still owned by another session");
            }
            RENDERER_OWNERS.put(audio, this);
            RENDERER_OWNERS.put(video, this);
        }
        try {
        connection.start(audio, video, new NvConnectionListener() {
            public void stageStarting(String stage) { deliver(() -> listener.stageStarting(stage)); }
            public void stageComplete(String stage) { deliver(() -> listener.stageComplete(stage)); }
            public void stageFailed(String stage, int ports, int code) {
                state.compareAndSet(State.CONNECTING, State.FAILED);
                state.compareAndSet(State.ACTIVE, State.FAILED);
                deliver(() -> listener.stageFailed(stage, ports, code));
                stopAsync();
            }
            public void connectionStarted() {
                if (stopping.get()) return;
                if (!state.compareAndSet(State.CONNECTING, State.ACTIVE)) return;
                deliver(listener::connectionStarted);
            }
            public void connectionTerminated(int code) {
                deliver(() -> listener.connectionTerminated(code));
                stopAsync();
            }
            public void connectionStatusUpdate(int status) { deliver(() -> listener.connectionStatusUpdate(status)); }
            public void displayMessage(String message) { deliver(() -> listener.displayMessage(message)); }
            public void displayTransientMessage(String message) { deliver(() -> listener.displayTransientMessage(message)); }
            // View-only: no gamepad, motion, keyboard or pointer control is exposed.
            public void rumble(short n, short lo, short hi) {}
            public void rumbleTriggers(short n, short left, short right) {}
            public void setHdrMode(boolean enabled, byte[] metadata) {}
            public void setMotionEventState(short n, byte type, short rate) {}
            public void setControllerLED(short n, byte r, byte g, byte b) {}
        });
        } catch (RuntimeException | LinkageError e) {
            stopAsync();
            throw e;
        }
    }

    public State getState() { return state.get(); }

    /**
     * Keep the caller's Surface valid until this future completes successfully.
     * Failure/timeout is not permission to dispose resources still owned by a vendor codec.
     */
    public CompletableFuture<Void> stopAsync() {
        if (stopping.compareAndSet(false, true)) {
            state.set(State.STOPPING);
            new Thread(() -> {
                try {
                    connection.stop();
                    if (!connection.awaitStartupComplete(30000)) {
                        throw new IllegalStateException("Native startup cancellation is still pending");
                    }
                    // Safe if native cleanup already called these: adapter contract is idempotent.
                    RuntimeException cleanupFailure = null;
                    for (Runnable cleanup : new Runnable[] {video::stop, audio::stop, video::cleanup, audio::cleanup}) {
                        try { cleanup.run(); }
                        catch (RuntimeException e) {
                            if (cleanupFailure == null) cleanupFailure = e;
                            else cleanupFailure.addSuppressed(e);
                        }
                    }
                    boolean videoReleased = ((RendererRelease)video).awaitReleased(15000);
                    boolean audioReleased = ((RendererRelease)audio).awaitReleased(15000);
                    if (!videoReleased || !audioReleased) {
                        throw new IllegalStateException("Vendor renderer release is still pending");
                    }
                    if (cleanupFailure != null) throw cleanupFailure;
                    synchronized (RENDERER_OWNERS) {
                        RENDERER_OWNERS.remove(audio);
                        RENDERER_OWNERS.remove(video);
                    }
                    state.set(State.STOPPED);
                    stopped.complete(null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    state.set(State.FAILED);
                    stopped.completeExceptionally(e);
                } catch (RuntimeException | LinkageError e) {
                    state.set(State.FAILED);
                    stopped.completeExceptionally(e);
                }
            }, "MoonCast-GameStream-stop").start();
        }
        return stopped;
    }

    @Override public void close() { stopAsync(); }

    private void deliver(Runnable callback) {
        if (stopping.get()) return;
        try { callback.run(); }
        catch (RuntimeException e) {
            // Throwing through a JNI callback can abort the whole receiver process.
            LimeLog.warning("Receiver UI callback failed");
        }
    }
}
