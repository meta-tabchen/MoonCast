package com.mooncast.host;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;
import java.nio.ByteBuffer;

/** MediaProjection -> shared MoonCast GLES framing -> hardware H.264 -> AirPlay transport. */
final class AirPlayVideoEncoder implements AutoCloseable {
    interface Output { void video(byte[] bytes, boolean keyframe); void error(String message); void log(String message); }
    private final Context context;
    private final Output output;
    private final AirPlayH264 h264 = new AirPlayH264();
    private MediaCodec codec;
    private Surface surface;
    private volatile VideoPipeline pipeline;
    private volatile boolean running;
    private Thread drain;
    AirPlayVideoEncoder(Context context, Output output) { this.context = context; this.output = output; }
    void start(MediaProjection projection, int width, int height, int fps, int bitrate, boolean legacy, int mode, boolean wholeScreen) throws Exception {
        java.util.List<String> encoders = EncoderSupport.hardware(MediaFormat.MIMETYPE_VIDEO_AVC);
        if (encoders.isEmpty()) throw new IllegalStateException(context.getString(R.string.ui_no_h_264_hardware_encoder_with_surface_input_found));
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        if (Build.VERSION.SDK_INT >= 29) format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
        if (Build.VERSION.SDK_INT >= 30) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        if (legacy) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4);
        }
        String selected = BrowserEncoderCapabilities.airPlayEncoder(format);
        if (selected == null) throw new IllegalStateException(context.getString(R.string.browser_quality_unsupported));
        codec = MediaCodec.createByCodecName(selected);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = codec.createInputSurface(); codec.start(); running = true;
        pipeline = new VideoPipeline(context, surface, width, height, mode, new VideoPipeline.Events() {
            public void message(String message) { output.log(message); }
            public void error(String message) { output.error(message); }
        });
        pipeline.setFrameLimit(fps);
        DisplayMetrics display = VideoPipeline.metrics(context.getSystemService(DisplayManager.class));
        pipeline.start(display.widthPixels, display.heightPixels, wholeScreen, (input, w, h) -> projection.createVirtualDisplay(
            "MoonCast AirPlay", w, h, display.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            input, null, new Handler(Looper.getMainLooper())));
        drain = new Thread(this::drain, "AirPlayVideo"); drain.start();
    }
    private static byte[] bytes(ByteBuffer input) {
        if (input == null) return new byte[0];
        ByteBuffer view = input.duplicate(); byte[] result = new byte[view.remaining()]; view.get(result); return result;
    }
    private void drain() {
        MediaCodec current = codec; MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (running) {
                int index = current.dequeueOutputBuffer(info, 100_000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = current.getOutputFormat();
                    h264.configuration(bytes(format.getByteBuffer("csd-0")), bytes(format.getByteBuffer("csd-1")));
                } else if (index >= 0) {
                    byte[] data = null; int flags = info.flags;
                    try {
                        ByteBuffer buffer = current.getOutputBuffer(index);
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size); data = bytes(buffer);
                        }
                    } finally { current.releaseOutputBuffer(index, false); }
                    // Do not hold a MediaCodec output while network backpressure blocks Go.
                    if (data != null) {
                        if ((flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) h264.configuration(data);
                        else { boolean key = (flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0; output.video(h264.frame(data, key), key); }
                    }
                }
            }
        } catch (Exception | LinkageError e) { if (running) output.error(context.getString(R.string.phone_h264_failed,e.getMessage())); }
    }
    void resize(int width, int height) { VideoPipeline current = pipeline; if (current != null) current.resize(width, height); }
    void scale(int mode) { VideoPipeline current = pipeline; if (current != null) current.setMode(mode); }
    void rescan() { VideoPipeline current = pipeline; if (current != null) current.rescan(); }
    FrameStatistics.Sample statistics() { VideoPipeline current = pipeline; return current == null ? new FrameStatistics.Sample(0, 0, 0) : current.statistics(); }
    @Override public void close() {
        running = false;
        VideoPipeline current = pipeline; pipeline = null; if (current != null) current.close();
        // The owner disconnects the Go pipe before close, allowing the drain thread to exit.
        if (drain != null && drain != Thread.currentThread()) try { drain.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (codec != null) { try { codec.stop(); } catch (RuntimeException ignored) {} codec.release(); codec = null; }
        if (surface != null) { surface.release(); surface = null; }
    }
}
