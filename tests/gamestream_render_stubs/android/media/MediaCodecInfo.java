/* SPDX-License-Identifier: GPL-3.0-only */
package android.media;
public final class MediaCodecInfo {
    public boolean isEncoder(){return false;}
    public String[] getSupportedTypes(){return new String[]{"video/avc"};}
    public boolean isHardwareAccelerated(){return true;}
    public boolean isSoftwareOnly(){return false;}
    public String getName(){return "host-contract-fake.avc";}
    public CodecCapabilities getCapabilitiesForType(String mime){return new CodecCapabilities();}
    public static final class CodecCapabilities {
        public static final String FEATURE_LowLatency="low-latency";
        public boolean isFeatureSupported(String feature){return true;}
    }
}
