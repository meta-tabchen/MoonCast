/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.airplay;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.view.Surface;
import android.os.Build;
import java.nio.ByteBuffer;

/** Synchronous bounded MediaCodec path. Native supplies Annex-B access units, not a media URL. */
final class MirrorDecoder {
    interface Events {
        void size(int width, int height);
        void failure();
        void output();
    }
    private static final java.util.concurrent.ScheduledExecutorService DRAINER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "MoonCast-AirPlay-video"); thread.setDaemon(true); return thread;
            });
    private java.util.concurrent.ScheduledFuture<?> drainTask;
    private final Events events;
    private Surface surface;
    private MediaCodec codec;
    private int width = 1920, height = 1080, fps = 60;
    private boolean hevc;
    private final VideoTiming timing = new VideoTiming();
    private boolean movie = true;
    private String decoderName = "", implementation = "unknown";
    private int decodedWidth, decodedHeight;
    private long fpsWindowNs, fpsFrames, lastOutputNs;
    private float observedFps;
    private long frameCount, droppedFrames;
    private byte[] codecConfig;
    private boolean errorReported;
    MirrorDecoder(Events events) { this.events = events; }

    static boolean supports(String mime) {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!info.isEncoder()) for (String type : info.getSupportedTypes()) if (mime.equals(type)) return true;
        }
        return false;
    }
    synchronized void setSurface(Surface value) {
        if (surface == value) return;
        surface = value;
        if (codec != null && value != null && value.isValid()) {
            try { codec.setOutputSurface(value); return; } catch (RuntimeException ignored) { /* reconnect decoder */ }
        }
        stopCodec();
    }
    synchronized void setResolution(int w, int h, int frameRate) {
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return;
        fps = Math.max(24, Math.min(frameRate, 60));
        if (width == w && height == h) return;
        width = w; height = h;
        // A codec-config packet follows orientation changes; avoid stale geometry.
        stopCodec(); codecConfig = null;
    }
    synchronized void configureTiming(boolean movieMode, int cushionMs) {
        movie = movieMode; timing.configure(movieMode, cushionMs);
    }
    synchronized String decoderName() { return decoderName; }
    synchronized String implementation() { return implementation; }
    synchronized String codecMime() { return decoderName.isEmpty() ? "" : hevc ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC; }
    synchronized int decodedWidth() { return decodedWidth; }
    synchronized int decodedHeight() { return decodedHeight; }
    synchronized boolean active() { return codec != null; }
    synchronized float observedFps() {
        return lastOutputNs == 0 || System.nanoTime() - lastOutputNs > 2_000_000_000L ? 0 : observedFps;
    }
    synchronized void resetStatistics() {
        frameCount = droppedFrames = fpsFrames = fpsWindowNs = lastOutputNs = 0;
        observedFps = 0; decodedWidth = decodedHeight = 0; decoderName = ""; implementation = "unknown";
    }
    synchronized long frames() { return frameCount; }
    synchronized long drops() { return droppedFrames; }
    synchronized void stopSession() { stopCodec(); codecConfig = null; errorReported = false; }
    synchronized void feed(byte[] data, long ntpNs, boolean h265) {
        if (data == null || data.length < 5 || data.length > 16 * 1024 * 1024) return;
        boolean config = hasNal(data, h265, true), keyframe = hasNal(data, h265, false);
        if (hevc != h265) { stopCodec(); codecConfig = null; hevc = h265; }
        if (config) codecConfig = data.clone();
        if (surface == null || !surface.isValid()) return;
        try {
            if (codec == null) {
                if (!keyframe && !config) return;
                startCodec(h265);
                if (!config && codecConfig != null) queue(codecConfig, ntpNs);
            }
            if (!queue(data, ntpNs)) droppedFrames++;
            drain();
        } catch (Exception failure) {
            stopCodec();
            if (!errorReported) { errorReported = true; events.failure(); }
        }
    }
    private void startCodec(boolean h265) throws Exception {
        String mime = h265 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
        Exception last = null;
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            boolean supports = false;
            for (String type : info.getSupportedTypes()) if (mime.equals(type)) supports = true;
            if (!supports) continue;
            MediaCodec candidate = null;
            try {
                MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Math.max(1024 * 1024, width * height));
                if (!movie && Build.VERSION.SDK_INT >= 30
                        && info.getCapabilitiesForType(mime).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency))
                    format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
                candidate = MediaCodec.createByCodecName(info.getName());
                candidate.configure(format, surface, null, 0);
                candidate.start(); codec = candidate; errorReported = false;
                decoderName = info.getName();
                implementation = Build.VERSION.SDK_INT >= 29
                        ? (info.isHardwareAccelerated() ? "hardware" : info.isSoftwareOnly() ? "software" : "unknown")
                        : "unknown";
                // Drain even when the sender is showing a static screen or has just paused.
                drainTask = DRAINER.scheduleWithFixedDelay(() -> {
                    synchronized (MirrorDecoder.this) {
                        if (codec == null) return;
                        try { drain(); } catch (RuntimeException failure) {
                            stopCodec();
                            if (!errorReported) { errorReported = true; events.failure(); }
                        }
                    }
                }, 8, 8, java.util.concurrent.TimeUnit.MILLISECONDS);
                return;
            } catch (Exception failure) {
                last = failure;
                if (candidate != null) try { candidate.release(); } catch (RuntimeException ignored) {}
            }
        }
        throw new IllegalStateException("No compatible AirPlay video decoder", last);
    }
    private boolean queue(byte[] data, long ntpNs) {
        // Bounded waits preserve reference frames rather than dropping every busy input buffer.
        for (int i = 0; i < 10; i++) {
            drain();
            int index = codec.dequeueInputBuffer(10_000);
            if (index < 0) continue;
            ByteBuffer buffer = codec.getInputBuffer(index);
            if (buffer == null || buffer.capacity() < data.length) {
                codec.queueInputBuffer(index, 0, 0, ntpNs / 1000, 0);
                return false;
            }
            buffer.clear(); buffer.put(data);
            codec.queueInputBuffer(index, 0, data.length, ntpNs / 1000, 0);
            return true;
        }
        return false;
    }
    private void drain() {
        if (codec == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        for (int n = 0; n < 64; n++) {
            int index = codec.dequeueOutputBuffer(info, 0);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = codec.getOutputFormat();
                int w = format.getInteger(MediaFormat.KEY_WIDTH), h = format.getInteger(MediaFormat.KEY_HEIGHT);
                if (format.containsKey("crop-right") && format.containsKey("crop-left"))
                    w = format.getInteger("crop-right") - format.getInteger("crop-left") + 1;
                if (format.containsKey("crop-bottom") && format.containsKey("crop-top"))
                    h = format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1;
                decodedWidth = w; decodedHeight = h; events.size(w, h); continue;
            }
            if (index < 0) return;
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                codec.releaseOutputBuffer(index, false); continue;
            }
            long now = System.nanoTime();
            if (movie) codec.releaseOutputBuffer(index, timing.target(info.presentationTimeUs, now));
            else codec.releaseOutputBuffer(index, true);
            frameCount++; lastOutputNs = now;
            if (fpsWindowNs == 0) fpsWindowNs = now;
            fpsFrames++;
            if (now - fpsWindowNs >= 1_000_000_000L) {
                observedFps = fpsFrames * 1_000_000_000f / (now - fpsWindowNs);
                fpsWindowNs = now; fpsFrames = 0;
            }
            events.output();
        }
    }
    private void stopCodec() {
        if (drainTask != null) { drainTask.cancel(false); drainTask = null; }
        if (codec != null) {
            try { codec.stop(); } catch (RuntimeException ignored) {}
            try { codec.release(); } catch (RuntimeException ignored) {}
            codec = null;
        }
        timing.reset();
    }
    static boolean hasNal(byte[] data, boolean h265, boolean config) {
        for (int i = 0; i + 4 < data.length; i++) {
            int offset;
            if (data[i] != 0 || data[i + 1] != 0) continue;
            if (data[i + 2] == 1) offset = i + 3;
            else if (data[i + 2] == 0 && data[i + 3] == 1) offset = i + 4;
            else continue;
            int type = h265 ? ((data[offset] & 0xff) >> 1) & 63 : data[offset] & 31;
            if (config && (h265 ? type >= 32 && type <= 34 : type == 7 || type == 8)) return true;
            if (!config && (h265 ? type >= 16 && type <= 21 : type == 5)) return true;
        }
        return false;
    }
}
