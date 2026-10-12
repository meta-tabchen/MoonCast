package com.mooncast.host;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Normalizes MediaCodec AVC config/access units to Annex B for the doubletake sender. */
final class AirPlayH264 {
    private byte[] config = new byte[0];
    private int lengthSize = 4;
    void configuration(byte[] bytes) {
        if (bytes.length == 0) return;
        if (isAnnexB(bytes)) { config = bytes.clone(); return; }
        if (bytes.length >= 7 && bytes[0] == 1) {
            lengthSize = (bytes[4] & 3) + 1;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int offset = 6;
            int count = bytes[5] & 31;
            for (int kind = 0; kind < 2; kind++) {
                for (int i = 0; i < count; i++) {
                    if (offset + 2 > bytes.length) throw new IllegalArgumentException("Truncated AVC configuration");
                    int n = ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255); offset += 2;
                    if (n == 0 || offset + n > bytes.length) throw new IllegalArgumentException("Invalid AVC parameter set");
                    append(out, bytes, offset, n); offset += n;
                }
                if (kind == 0) {
                    if (offset >= bytes.length) throw new IllegalArgumentException("Missing AVC PPS");
                    count = bytes[offset++] & 255;
                }
            }
            config = out.toByteArray();
        } else config = normalize(bytes);
    }
    void configuration(byte[] sps, byte[] pps) {
        configuration(sps);
        if (pps != null && pps.length > 0) {
            byte[] second = normalize(pps);
            byte[] both = Arrays.copyOf(config, config.length + second.length);
            System.arraycopy(second, 0, both, config.length, second.length); config = both;
        }
    }
    byte[] frame(byte[] bytes, boolean keyframe) {
        byte[] frame = normalize(bytes);
        if (!keyframe || config.length == 0) return frame;
        byte[] result = Arrays.copyOf(config, config.length + frame.length);
        System.arraycopy(frame, 0, result, config.length, frame.length);
        return result;
    }
    private byte[] normalize(byte[] bytes) {
        if (bytes.length == 0 || isAnnexB(bytes)) return bytes.clone();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int offset = 0;
        while (offset < bytes.length) {
            if (offset + lengthSize > bytes.length) throw new IllegalArgumentException("Truncated AVC length");
            long n = 0; for (int i = 0; i < lengthSize; i++) n = (n << 8) | (bytes[offset++] & 255);
            if (n == 0 || n > bytes.length - offset) throw new IllegalArgumentException("Invalid AVC NAL length");
            append(out, bytes, offset, (int)n); offset += (int)n;
        }
        return out.toByteArray();
    }
    private static void append(ByteArrayOutputStream out, byte[] data, int offset, int length) {
        out.write(0); out.write(0); out.write(0); out.write(1); out.write(data, offset, length);
    }
    private static boolean isAnnexB(byte[] b) {
        return b.length >= 3 && b[0] == 0 && b[1] == 0 && (b[2] == 1 || b.length >= 4 && b[2] == 0 && b[3] == 1);
    }
}
