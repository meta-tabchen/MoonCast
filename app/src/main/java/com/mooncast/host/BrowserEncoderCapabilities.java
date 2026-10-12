package com.mooncast.host;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.DisplayMetrics;

/** Reported hardware capabilities are a preflight, not proof of sustained encoding. */
final class BrowserEncoderCapabilities {
    private static volatile MediaCodecInfo[] reported;
    private static synchronized MediaCodecInfo[] encoders() {
        if (reported == null) reported = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
        return reported;
    }
    private static boolean hardware(MediaCodecInfo codec) {
        if (!codec.isEncoder()) return false;
        if (Build.VERSION.SDK_INT >= 29) return codec.isHardwareAccelerated();
        String name = codec.getName().toLowerCase(java.util.Locale.ROOT);
        return !name.startsWith("omx.google.") && !name.startsWith("c2.android.");
    }
    private static boolean surface(MediaCodecInfo.CodecCapabilities caps) {
        for (int color : caps.colorFormats)
            if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) return true;
        return false;
    }
    static boolean supports(Context context, BrowserQuality quality) {
        DisplayMetrics display = VideoPipeline.metrics(context.getSystemService(DisplayManager.class));
        int[] size = quality.dimensions(display.widthPixels, display.heightPixels);
        return supportsSize(size[0], size[1], quality.fps, quality.maxBitrateBps, null);
    }
    static boolean supportsMime(Context context, BrowserQuality quality, String mime) {
        DisplayMetrics display = VideoPipeline.metrics(context.getSystemService(DisplayManager.class));
        int[] size = quality.dimensions(display.widthPixels, display.heightPixels);
        return supportsSize(size[0], size[1], quality.fps, quality.maxBitrateBps, mime);
    }
    /** AirPlay uses hardware H.264 and a requested output box, not WebRTC VP8. */
    static boolean supportsAirPlay(int width, int height, int fps, int bitrate) {
        return supportsSize(width, height, fps, bitrate, "video/avc");
    }
    private static boolean supportsSize(int width, int height, int fps, int bitrate, String onlyMime) {
        try {
            for (MediaCodecInfo codec : encoders()) {
                if (!hardware(codec)) continue;
                for (String mime : codec.getSupportedTypes()) {
                    boolean avc = mime.equalsIgnoreCase("video/avc");
                    if (!avc && !mime.equalsIgnoreCase("video/x-vnd.on2.vp8")) continue;
                    if (onlyMime != null && !mime.equalsIgnoreCase(onlyMime)) continue;
                    try {
                        MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(mime);
                        MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
                        if (surface(caps) && video != null
                                && video.getBitrateRange().contains(bitrate)
                                && video.areSizeAndRateSupported(width, height, fps)) return true;
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }
    static String airPlayEncoder(MediaFormat format) {
        try {
            for (MediaCodecInfo codec : encoders()) {
                if (!hardware(codec)) continue;
                for (String mime : codec.getSupportedTypes()) {
                    if (!mime.equalsIgnoreCase("video/avc")) continue;
                    MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(mime);
                    if (surface(caps) && caps.isFormatSupported(format)) return codec.getName();
                }
            }
        } catch (RuntimeException ignored) { }
        return null;
    }
    static BrowserQuality clearProfile(Context context) {
        // Preserve detail before temporal rate; never select 4K60 simply from a preset label.
        for (int edge : new int[] {3840, 3200, 2560, 1920, 1280, 960, 720, 640})
            for (int fps : new int[] {30, 24, 15})
                for (int budget : new int[] {24_000_000, 16_000_000, 12_000_000, 8_000_000, 4_000_000, 2_000_000}) {
                    BrowserQuality candidate = BrowserQuality.of(edge, fps, budget);
                    if (supports(context, candidate)) return candidate;
                }
        return BrowserQuality.DEFAULT; // Caller must still check supports and report unavailable.
    }
}
