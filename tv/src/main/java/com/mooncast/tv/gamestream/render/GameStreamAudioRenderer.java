/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.gamestream.render;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;
import com.mooncast.receiver.RendererRelease;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Native Opus has already produced borrowed, interleaved stereo PCM16 at this boundary.
 * Copy into a bounded FIFO, then let one worker own every AudioTrack call and release.
 * No microphone, decoder, source-clock synchronization, or audio-focus policy lives here.
 * The session controller owns audio focus. Callbacks can occur off main and must not block.
 */
public final class GameStreamAudioRenderer implements AudioRenderer, RendererRelease {
    private static final int CHANNELS = 2;
    private static final int SOFTWARE_QUEUE_MS = 60;
    private static final long MAX_PENDING_NS = 100_000_000L;
    private static final long SETUP_WAIT_MS = 3_000;
    private static final long CLEANUP_WAIT_MS = 750;

    public interface Listener {
        default void onStats(Stats stats) { }
        default void onError(String message) { }
        default void onReleased() { }
    }
    public static final class Stats {
        public final int sampleRate, channels, queuedFrames, outputBufferFrames, underrunCount;
        /** Frames means one sample per channel; written is submitted, not measured audible. */
        /** droppedFrames counts local PCM rejection; sink flush/playback loss is not measurable here. */
        public final long receivedFrames, writtenFrames, droppedFrames, lastWriteNs;
        public final int outputResets;
        public final String lastError;
        public final boolean released;
        private Stats(Session s, String error) {
            sampleRate = s == null ? 0 : s.sampleRate;
            channels = CHANNELS;
            queuedFrames = s == null ? 0 : s.queue.queuedFrames();
            outputBufferFrames = s == null ? 0 : s.outputBufferFrames;
            underrunCount = s == null ? 0 : s.underrunCount;
            receivedFrames = s == null ? 0 : s.queue.receivedFrames();
            writtenFrames = s == null ? 0 : s.writtenFrames;
            droppedFrames = s == null ? 0 : s.queue.droppedFrames();
            lastWriteNs = s == null ? 0 : s.lastWriteNs;
            outputResets = s == null ? 0 : s.outputResets;
            lastError = error;
            released = s == null || s.released.getCount() == 0;
        }
        public float queuedMs() { return sampleRate == 0 ? 0 : queuedFrames * 1000f / sampleRate; }
        public float outputBufferMs() { return sampleRate == 0 ? 0 : outputBufferFrames * 1000f / sampleRate; }
    }

    private final Listener listener;
    private volatile Session session;
    private volatile String lastError;
    public GameStreamAudioRenderer(Listener listener) { this.listener = listener == null ? new Listener() { } : listener; }
    public GameStreamAudioRenderer() { this(null); }
    public GameStreamTiming getTiming() { return GameStreamTiming.LOW_LATENCY; }
    public Stats getStats() { return new Stats(session, lastError); }
    public String getLastError() { return lastError; }

    @Override public synchronized int setup(MoonBridge.AudioConfiguration configuration,
                                            int sampleRate, int samplesPerFrame) {
        cleanup();
        if (!isReleased()) return failSetup("Previous audio output is still releasing");
        // The pinned GameStream core negotiates 48 kHz. Do not claim surround/downmix support.
        if (configuration == null || configuration.channelCount != CHANNELS || configuration.channelMask != 0x3
                || sampleRate != 48_000 || samplesPerFrame <= 0 || samplesPerFrame > sampleRate * SOFTWARE_QUEUE_MS / 1000) {
            return failSetup("Only 48 kHz stereo PCM with bounded frame duration is supported");
        }
        lastError = null;
        Session created = new Session(sampleRate, samplesPerFrame);
        session = created;
        created.worker.start();
        try {
            if (!created.initialized.await(SETUP_WAIT_MS, TimeUnit.MILLISECONDS)) {
                created.stop();
                return failSetup("Audio output setup timed out; waiting for safe release");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); created.stop();
            return failSetup("Audio output setup interrupted");
        }
        return created.setupOkay && !created.stopping.get() ? 0 : -1;
    }
    private int failSetup(String error) { reportError(error); return -1; }
    @Override public void start() {
        Session current = session;
        if (current != null && !current.stopping.get()) { current.started = true; LockSupport.unpark(current.worker); }
    }
    @Override public void stop() { Session current = session; if (current != null) current.stop(); }
    @Override public void cleanup() {
        stop();
        if (!awaitReleased(CLEANUP_WAIT_MS)) lastError = "Audio output release is still pending";
    }
    @Override public void playDecodedAudio(short[] borrowed) {
        Session current = session;
        if (current == null || !current.started || current.stopping.get()) return;
        if (borrowed == null || borrowed.length != current.samplesPerFrame * CHANNELS) {
            reportError("Native PCM callback did not match the negotiated stereo frame size");
            current.stop();
            return;
        }
        current.queue.offer(borrowed, System.nanoTime());
        LockSupport.unpark(current.worker);
    }
    public boolean isReleased() { Session current = session; return current == null || current.released.getCount() == 0; }
    public boolean awaitReleased(long timeoutMs) {
        if (timeoutMs < 0) throw new IllegalArgumentException("Negative timeout");
        Session current = session;
        if (current == null) return true;
        if (Thread.currentThread() == current.worker) return isReleased();
        try { return current.released.await(timeoutMs, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }
    private void reportError(String error) { lastError = error; try { listener.onError(error); } catch (RuntimeException ignored) { } }
    private void publishStats(Session source) {
        if (session != source) return;
        try { listener.onStats(new Stats(source, lastError)); } catch (RuntimeException ignored) { }
    }
    private static String describe(String prefix, Exception error) {
        return prefix + ": " + error.getClass().getSimpleName() + " (" + error.getMessage() + ")";
    }

    private final class Session {
        final int sampleRate, samplesPerFrame;
        final PcmQueue queue;
        final AtomicBoolean stopping = new AtomicBoolean();
        final CountDownLatch initialized = new CountDownLatch(1), released = new CountDownLatch(1);
        final Thread worker;
        volatile boolean started, setupOkay;
        volatile int outputBufferFrames, underrunCount, outputResets;
        volatile long writtenFrames, lastWriteNs;
        AudioTrack track;
        Session(int sampleRate, int samplesPerFrame) {
            this.sampleRate = sampleRate; this.samplesPerFrame = samplesPerFrame;
            queue = new PcmQueue(CHANNELS, sampleRate * SOFTWARE_QUEUE_MS / 1000);
            worker = new Thread(this::run, "MoonCast-GameStream-audio");
            worker.setDaemon(true);
        }
        void stop() { started = false; stopping.set(true); queue.close(); LockSupport.unpark(worker); }
        void run() {
            PcmQueue.Block pending = null;
            int offset = 0;
            long lastStatsNs = 0;
            boolean playing = false;
            try {
                createTrack();
                setupOkay = true; initialized.countDown();
                while (!stopping.get()) {
                    if (started) {
                        if (!playing) { track.play(); playing = true; }
                        if (pending == null) { pending = queue.poll(); offset = 0; }
                        if (pending != null) {
                            if (System.nanoTime() - pending.receivedNs > MAX_PENDING_NS) {
                                queue.dropFrames((pending.samples.length - offset) / CHANNELS);
                                pending = null;
                                // Clear backed-up sink audio too, rather than replaying stale PCM.
                                track.pause(); track.flush(); track.play(); outputResets++;
                            } else {
                                int written = track.write(pending.samples, offset, pending.samples.length - offset,
                                        AudioTrack.WRITE_NON_BLOCKING);
                                if (written == AudioTrack.ERROR_DEAD_OBJECT && outputResets == 0) {
                                    queue.dropFrames((pending.samples.length - offset) / CHANNELS);
                                    pending = null; queue.discardQueued();
                                    track.release(); track = null;
                                    createTrack(); track.play(); outputResets++;
                                } else if (written < 0 || written % CHANNELS != 0) {
                                    throw new IllegalStateException("AudioTrack write failed: " + written);
                                } else if (written > 0) {
                                    offset += written; writtenFrames += written / CHANNELS;
                                    lastWriteNs = System.nanoTime();
                                    if (offset == pending.samples.length) pending = null;
                                }
                            }
                        }
                    }
                    long now = System.nanoTime();
                    if (now - lastStatsNs >= 1_000_000_000L) {
                        lastStatsNs = now; underrunCount = track.getUnderrunCount(); publishStats(this);
                    }
                    LockSupport.parkNanos(1_000_000L);
                    Thread.interrupted();
                }
            } catch (Exception failure) {
                reportError(describe("GameStream audio output failed", failure));
            } finally {
                started = false; stopping.set(true); queue.close(); initialized.countDown();
                boolean safe = true;
                if (track != null) {
                    // Only this thread touches the sink. Nonblocking writes avoid release/write races.
                    try { track.pause(); track.flush(); } catch (RuntimeException ignored) { }
                    try { track.release(); track = null; }
                    catch (RuntimeException failure) { safe = false; reportError(describe("Audio release failed", failure)); }
                }
                if (safe) {
                    released.countDown();
                    synchronized (GameStreamAudioRenderer.this) {
                        if (session == this) {
                            publishStats(this);
                            try { listener.onReleased(); } catch (RuntimeException ignored) { }
                        }
                    }
                }
            }
        }
        void createTrack() {
            int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IllegalStateException("No stereo PCM AudioTrack buffer available: " + min);
            int bytes = Math.max(min, Math.max(samplesPerFrame * CHANNELS * 2, sampleRate * CHANNELS * 2 / 50));
            // A vendor minimum above 500 ms is incompatible with this bounded live path.
            if (bytes > sampleRate * CHANNELS) throw new IllegalStateException("AudioTrack minimum buffer exceeds 500 ms");
            AudioTrack.Builder builder = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(bytes)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            track = builder.build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("AudioTrack is uninitialized");
            outputBufferFrames = track.getBufferSizeInFrames();
        }
    }
}
