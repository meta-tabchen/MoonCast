/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.gamestream.render;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.jni.MoonBridge;
import com.mooncast.receiver.RendererRelease;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Conservative H.264 SDR GameStream receiver. A single worker owns all MediaCodec calls;
 * native callbacks only validate/copy into a bounded queue. This is not Moonlight's full
 * device-quirk renderer. The supplied Surface (including TextureView surfaces) is caller-owned.
 *
 * Listener callbacks normally run on the codec worker; setup argument errors run on its
 * caller. Listeners must be quick/nonblocking, post UI work, and ignore stale session events.
 * Never release a detached Surface until onSurfaceDetached/awaitSurfaceDetached confirms it.
 * cleanup is bounded; a hung vendor codec must retain its Surface until release is confirmed.
 */
public final class GameStreamVideoRenderer extends VideoDecoderRenderer implements RendererRelease {
    public static final int SUPPORTED_VIDEO_FORMATS = MoonBridge.VIDEO_FORMAT_H264;
    private static final long SETUP_WAIT_MS = 5_000;
    private static final long CLEANUP_WAIT_MS = 750;
    private static final long MAX_PENDING_NS = 100_000_000L;
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;

    public interface Listener {
        default void onVideoSizeChanged(int width, int height) { }
        default void onStats(Stats stats) { }
        default void onError(String message) { }
        default void onSurfaceDetached(Surface surface) { }
        default void onReleased() { }
    }

    public static final class Stats {
        public final int decodedWidth, decodedHeight, requestedFps, queuedFrames, queuedBytes;
        public final String decoderName, implementation, lastError, lastDropReason;
        /** Local compressed-picture drops only; excludes network loss and unknowable display drops. */
        public final long receivedFrames, frames, droppedFrames, decoderResets, lastOutputNs;
        /** Receiver codec-output rate; not measured display refresh or end-to-end latency. */
        public final float observedFps;
        public final boolean awaitingIdr, surfaceAttached, released;
        private Stats(Session s, String error) {
            decodedWidth = s == null ? 0 : s.decodedWidth;
            decodedHeight = s == null ? 0 : s.decodedHeight;
            requestedFps = s == null ? 0 : s.fps;
            decoderName = s == null ? "" : s.decoderName;
            implementation = s == null ? "unknown" : s.implementation;
            lastError = error;
            lastDropReason = s == null ? null : s.ingress.rejection();
            receivedFrames = s == null ? 0 : s.ingress.receivedPictures();
            frames = s == null ? 0 : s.frames;
            droppedFrames = s == null ? 0 : s.ingress.droppedPictures();
            decoderResets = s == null ? 0 : s.decoderResets;
            lastOutputNs = s == null ? 0 : s.lastOutputNs;
            observedFps = s == null || lastOutputNs == 0 || System.nanoTime() - lastOutputNs > 2_000_000_000L
                    ? 0 : s.observedFps;
            queuedFrames = s == null ? 0 : s.ingress.size();
            queuedBytes = s == null ? 0 : s.ingress.bytes();
            awaitingIdr = s == null || s.ingress.awaitingIdr();
            surfaceAttached = s != null && s.codecSurface != null;
            released = s == null || s.released.getCount() == 0;
        }
    }

    private final Listener listener;
    private final Object surfaceLock = new Object();
    private volatile Surface desiredSurface;
    private volatile Session session;
    private volatile String lastError;

    public GameStreamVideoRenderer(Surface surface, Listener listener) {
        desiredSurface = surface;
        this.listener = listener == null ? new Listener() { } : listener;
    }
    public GameStreamVideoRenderer(Listener listener) { this(null, listener); }
    public GameStreamTiming getTiming() { return GameStreamTiming.LOW_LATENCY; }
    public Stats getStats() { return new Stats(session, lastError); }
    public String getLastError() { return lastError; }

    /** Asynchronous replacement/detach. This method never releases either Surface. */
    public void setSurface(Surface surface) {
        synchronized (surfaceLock) {
            if (desiredSurface == surface) return;
            desiredSurface = surface;
            surfaceLock.notifyAll();
        }
        Session current = session;
        if (current != null) { current.ingress.requireIdr(); LockSupport.unpark(current.worker); }
    }

    /** Call off the UI thread after replacing/detaching oldSurface. False means retain it. */
    public boolean awaitSurfaceDetached(Surface oldSurface, long timeoutMs) {
        if (timeoutMs < 0) throw new IllegalArgumentException("Negative timeout");
        if (oldSurface == null) return true;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.min(timeoutMs, 60_000));
        synchronized (surfaceLock) {
            while (desiredSurface == oldSurface || (session != null && session.codecSurface == oldSurface)) {
                long left = deadline - System.nanoTime();
                if (left <= 0 || (session != null && Thread.currentThread() == session.worker)) return false;
                try { TimeUnit.NANOSECONDS.timedWait(surfaceLock, left); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
            }
            return true;
        }
    }

    @Override public synchronized int setup(int format, int width, int height, int redrawRate) {
        cleanup();
        if (!isReleased()) return failSetup("Previous video decoder is still releasing");
        if (format != SUPPORTED_VIDEO_FORMATS || width <= 0 || height <= 0
                || width > 8192 || height > 8192 || (long) width * height > 8192L * 4320
                || redrawRate <= 0 || redrawRate > 240) {
            return failSetup("Unsupported stream: H.264 8-bit SDR and valid dimensions/FPS are required");
        }
        lastError = null;
        Session created = new Session(width, height, redrawRate);
        session = created;
        created.worker.start();
        try {
            if (!created.initialized.await(SETUP_WAIT_MS, TimeUnit.MILLISECONDS)) {
                created.stop();
                return failSetup("Video decoder setup timed out; waiting for safe release");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); created.stop();
            return failSetup("Video decoder setup interrupted");
        }
        return created.setupOkay && !created.stopping.get() ? 0 : -1;
    }
    private int failSetup(String message) { reportError(message); return -1; }
    @Override public void start() {
        Session current = session;
        if (current != null && !current.stopping.get()) { current.started = true; LockSupport.unpark(current.worker); }
    }
    @Override public void stop() { Session current = session; if (current != null) current.stop(); }
    @Override public void cleanup() {
        stop();
        if (!awaitReleased(CLEANUP_WAIT_MS)) lastError = "Video release pending; retain the caller-owned Surface";
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
    @Override public int getCapabilities() {
        // Java validation/copying/locks are not a nonblocking direct-submit contract.
        // No reference-frame invalidation, HEVC, AV1, HDR, or slice capability is advertised.
        return 0;
    }
    @Override public void setHdrMode(boolean enabled, byte[] metadata) {
        if (enabled) { reportError("HDR is unsupported; negotiate H.264 SDR"); stop(); }
    }
    @Override public int submitDecodeUnit(byte[] data, int length, int type, int frameNumber,
            int frameType, char hostLatency, long receiveTimeUs, long enqueueTimeUs) {
        Session current = session;
        if (current == null || current.stopping.get() || !current.started) return MoonBridge.DR_NEED_IDR;
        Surface target = desiredSurface;
        int result = current.ingress.accept(data, length, type, frameNumber, frameType, System.nanoTime(),
                target != null && target.isValid());
        LockSupport.unpark(current.worker);
        return result;
    }
    private void reportError(String message) {
        lastError = message;
        try { listener.onError(message); } catch (RuntimeException ignored) { }
    }
    private void publishStats(Session source) {
        if (session != source) return;
        try { listener.onStats(new Stats(source, lastError)); } catch (RuntimeException ignored) { }
    }
    private static String describe(String prefix, Exception error) {
        return prefix + ": " + error.getClass().getSimpleName() + " (" + error.getMessage() + ")";
    }

    private final class Session {
        final int width, height, fps;
        final VideoIngress ingress = new VideoIngress();
        final AtomicBoolean stopping = new AtomicBoolean();
        final CountDownLatch initialized = new CountDownLatch(1), released = new CountDownLatch(1);
        final Thread worker;
        volatile boolean started, setupOkay;
        volatile Surface codecSurface;
        volatile int decodedWidth, decodedHeight;
        volatile String decoderName = "", implementation = "unknown";
        volatile long frames, decoderResets, lastOutputNs;
        volatile float observedFps;
        MediaCodec codec;
        VideoIngress.Frame pending;
        boolean configQueued;
        long codecGeneration, ptsUs, fpsWindowNs, fpsWindowFrames, lastStatsNs;

        Session(int width, int height, int fps) {
            this.width = width; this.height = height; this.fps = fps;
            worker = new Thread(this::run, "MoonCast-GameStream-video");
            worker.setDaemon(true);
        }
        void stop() { started = false; stopping.set(true); ingress.close(); LockSupport.unpark(worker); }
        void run() {
            try {
                reconcileSurface();
                setupOkay = true;
                initialized.countDown();
                while (!stopping.get()) {
                    reconcileSurface();
                    if (codec != null && started) {
                        long generation = ingress.generation();
                        if (generation != codecGeneration) {
                            pending = null; configQueued = false;
                            codec.flush(); decoderResets++; codecGeneration = generation;
                        }
                        drainOutput();
                        if (pending == null) { pending = ingress.poll(); configQueued = false; }
                        if (pending != null) {
                            if (pending.generation != ingress.generation()) {
                                pending = null;
                            } else if (System.nanoTime() - pending.receivedNs > MAX_PENDING_NS) {
                                ingress.dropInFlight(); pending = null;
                            } else if (pending.unit.codecConfig != null && !configQueued) {
                                configQueued = queue(pending.unit.codecConfig, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                            } else {
                                long stamp = Math.max(ptsUs + 1, System.nanoTime() / 1000);
                                if (queue(pending.unit.picture, stamp,
                                        pending.unit.keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0)) {
                                    ptsUs = stamp; pending = null;
                                }
                            }
                        }
                        drainOutput();
                    }
                    long now = System.nanoTime();
                    if (now - lastStatsNs >= 1_000_000_000L) { lastStatsNs = now; publishStats(this); }
                    LockSupport.parkNanos(2_000_000L);
                    Thread.interrupted();
                }
            } catch (Exception failure) {
                reportError(describe("GameStream video decoder failed", failure));
            } finally {
                started = false; stopping.set(true); ingress.close(); pending = null;
                initialized.countDown();
                boolean detached = releaseCodec();
                if (detached) {
                    released.countDown();
                    synchronized (GameStreamVideoRenderer.this) {
                        if (session == this) {
                            publishStats(this);
                            try { listener.onReleased(); } catch (RuntimeException ignored) { }
                        }
                    }
                }
            }
        }

        void reconcileSurface() throws Exception {
            Surface target = desiredSurface;
            if (target != null && !target.isValid()) target = null;
            if (codecSurface == target && (codec != null || target == null)) return;
            if (!releaseCodec()) throw new IllegalStateException("Previous Surface could not be safely detached");
            ingress.requireIdr(); pending = null; configQueued = false;
            if (target == null || stopping.get()) return;
            synchronized (surfaceLock) {
                if (desiredSurface != target || !target.isValid() || stopping.get()) return;
                // Claim before configure, under the same lock used by detach waiters.
                codecSurface = target;
            }
            createDecoder(target);
            codecGeneration = ingress.generation();
        }

        void createDecoder(Surface surface) throws Exception {
            List<MediaCodecInfo> candidates = new ArrayList<>();
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (MIME.equalsIgnoreCase(type)) { candidates.add(info); break; }
                }
            }
            if (Build.VERSION.SDK_INT >= 29) candidates.sort(Comparator.comparing(info -> !info.isHardwareAccelerated()));
            Exception last = null;
            for (MediaCodecInfo info : candidates) {
                if (stopping.get() || desiredSurface != surface) return;
                boolean lowLatency = false;
                try {
                    lowLatency = Build.VERSION.SDK_INT >= 30 && info.getCapabilitiesForType(MIME)
                            .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency);
                } catch (RuntimeException ignored) { }
                for (int attempt = 0; attempt < (lowLatency ? 2 : 1); attempt++) {
                    try {
                        MediaFormat format = MediaFormat.createVideoFormat(MIME, width, height);
                        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, DecodeUnitAssembler.MAX_PICTURE_BYTES);
                        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
                        format.setInteger(MediaFormat.KEY_PRIORITY, 0);
                        if (lowLatency && attempt == 0) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
                        codec = MediaCodec.createByCodecName(info.getName());
                        // Runtime SPS/PPS are queued through BUFFER_FLAG_CODEC_CONFIG before IDR.
                        codec.configure(format, surface, null, 0);
                        codec.start();
                        decoderName = info.getName();
                        implementation = Build.VERSION.SDK_INT >= 29
                                ? info.isHardwareAccelerated() ? "hardware" : info.isSoftwareOnly() ? "software" : "unknown"
                                : "unknown";
                        return;
                    } catch (Exception failure) {
                        last = failure;
                        if (codec != null) {
                            // A failed release is not evidence the caller may destroy this Surface.
                            codec.release(); codec = null;
                        }
                    }
                }
            }
            throw new IllegalStateException("No compatible H.264 decoder", last);
        }

        boolean queue(byte[] bytes, long timestampUs, int flags) {
            int index = codec.dequeueInputBuffer(0);
            if (index < 0) return false;
            ByteBuffer buffer = codec.getInputBuffer(index);
            if (buffer == null || buffer.capacity() < bytes.length) {
                codec.queueInputBuffer(index, 0, 0, timestampUs, 0);
                ingress.dropInFlight(); pending = null;
                return false;
            }
            buffer.clear(); buffer.put(bytes);
            codec.queueInputBuffer(index, 0, bytes.length, timestampUs, flags);
            return true;
        }

        void drainOutput() {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (int n = 0; n < 8 && !stopping.get(); n++) {
                int index = codec.dequeueOutputBuffer(info, 0);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat output = codec.getOutputFormat();
                    int w = output.getInteger(MediaFormat.KEY_WIDTH), h = output.getInteger(MediaFormat.KEY_HEIGHT);
                    if (output.containsKey("crop-left") && output.containsKey("crop-right"))
                        w = output.getInteger("crop-right") - output.getInteger("crop-left") + 1;
                    if (output.containsKey("crop-top") && output.containsKey("crop-bottom"))
                        h = output.getInteger("crop-bottom") - output.getInteger("crop-top") + 1;
                    if (w > 0 && h > 0 && w <= 8192 && h <= 8192) {
                        decodedWidth = w; decodedHeight = h;
                        try { listener.onVideoSizeChanged(w, h); } catch (RuntimeException ignored) { }
                    }
                    continue;
                }
                if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) continue;
                if (index < 0) return;
                boolean render = (info.flags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG | MediaCodec.BUFFER_FLAG_END_OF_STREAM)) == 0
                        && started && desiredSurface == codecSurface && codecSurface != null && codecSurface.isValid();
                codec.releaseOutputBuffer(index, render);
                if (render) {
                    frames++; lastOutputNs = System.nanoTime();
                    if (fpsWindowNs == 0) fpsWindowNs = lastOutputNs;
                    fpsWindowFrames++;
                    if (lastOutputNs - fpsWindowNs >= 1_000_000_000L) {
                        observedFps = fpsWindowFrames * 1_000_000_000f / (lastOutputNs - fpsWindowNs);
                        fpsWindowFrames = 0; fpsWindowNs = lastOutputNs;
                    }
                }
            }
        }

        boolean releaseCodec() {
            if (codec != null) {
                try { codec.release(); codec = null; }
                catch (RuntimeException failure) {
                    reportError(describe("Video decoder release failed; retain Surface", failure));
                    return false;
                }
            }
            Surface old;
            synchronized (surfaceLock) { old = codecSurface; codecSurface = null; surfaceLock.notifyAll(); }
            if (old != null) try { listener.onSurfaceDetached(old); } catch (RuntimeException ignored) { }
            return true;
        }
    }
}
