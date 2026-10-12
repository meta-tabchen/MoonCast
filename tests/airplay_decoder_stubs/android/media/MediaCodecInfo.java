/* SPDX-License-Identifier: GPL-3.0-only */
package android.media;

public final class MediaCodecInfo {
    private final String name;
    MediaCodecInfo(String name) { this.name = name; }
    public String getName() { return name; }
    public boolean isEncoder() { return false; }
    public String[] getSupportedTypes() {
        return new String[]{MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC};
    }
    public boolean isHardwareAccelerated() { return true; }
    public boolean isSoftwareOnly() { return false; }
    public CodecCapabilities getCapabilitiesForType(String mime) { return new CodecCapabilities(); }
    public static final class CodecCapabilities {
        public static final String FEATURE_LowLatency = "low-latency";
        public boolean isFeatureSupported(String feature) { return true; }
    }
}
