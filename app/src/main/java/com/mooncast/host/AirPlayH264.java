package com.mooncast.host;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Normalizes MediaCodec AVC config/access units to Annex B for the doubletake sender. */
final class AirPlayH264 {
    private byte[] config = new byte[0];
    private byte[] sps = new byte[0], pps = new byte[0];
    private int lengthSize = 4;
    private volatile boolean avccFraming;
    private volatile long annexInputs, avccInputs, ambiguousInputs, resolvedInputs;
    String diagnostics() { return "h264Input annex=" + annexInputs + ";avcc=" + avccInputs + ";ambiguous=" + ambiguousInputs +
        ";avccDeclared="+avccFraming+";resolved="+resolvedInputs; }
    private boolean validLengthSequence(byte[] data) {
        int offset=0;
        while(offset<data.length) {
            if(data.length-offset<lengthSize)return false;
            long n=0;for(int i=0;i<lengthSize;i++)n=(n<<8)|(data[offset++]&255);
            if(n<1 || n>data.length-offset)return false;
            offset+=(int)n;
        }
        return data.length>0;
    }
    void configuration(byte[] bytes) {
        if (bytes.length == 0) return;
        if (isAnnexB(bytes)) { remember(bytes); return; }
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
            remember(out.toByteArray());
            avccFraming = true;
        } else remember(normalize(bytes));
    }
    void configuration(byte[] sps, byte[] pps) {
        // A new output format supplies a new framing declaration. A standalone
        // Annex B PPS callback must not erase a preceding avcC declaration.
        avccFraming = false;
        configuration(sps);
        if (pps != null && pps.length > 0) {
            configuration(pps);
        }
    }
    private void remember(byte[] data) {
        // MediaCodec can deliver SPS and PPS together, separately, in AVCC,
        // or again as BUFFER_FLAG_CODEC_CONFIG. Merge by NAL type so one
        // callback cannot discard the other set or append stale duplicates.
        for (int start = start(data, 0); start >= 0;) {
            int offset = start + (data[start + 2] == 1 ? 3 : 4);
            int next = start(data, offset), end = next < 0 ? data.length : next;
            if (end > offset) {
                int type = data[offset] & 31;
                if (type == 7) sps = Arrays.copyOfRange(data, start, end);
                if (type == 8) pps = Arrays.copyOfRange(data, start, end);
            }
            start = next;
        }
        config = Arrays.copyOf(sps, sps.length + pps.length);
        System.arraycopy(pps, 0, config, sps.length, pps.length);
    }
    private static int start(byte[] data, int offset) {
        for (int i = offset; i + 2 < data.length; i++)
            if (data[i] == 0 && data[i+1] == 0 && (data[i+2] == 1 || i + 3 < data.length && data[i+2] == 0 && data[i+3] == 1)) return i;
        return -1;
    }
    byte[] frame(byte[] bytes, boolean keyframe) {
        boolean prefix = isAnnexB(bytes);
        if(avccFraming || !prefix)avccInputs++;else annexInputs++;
        if(prefix && validLengthSequence(bytes)) {ambiguousInputs++;if(avccFraming)resolvedInputs++;}
        // avcC explicitly declares length-prefixed video and its length width.
        // Never let a valid length 00 00 01 xx override that declaration.
        // Without a declaration retain Annex B behavior; don't guess format.
        byte[] frame = avccFraming ? lengthPrefixed(bytes) : normalize(bytes);
        if (!keyframe || config.length == 0) return frame;
        byte[] result = Arrays.copyOf(config, config.length + frame.length);
        System.arraycopy(frame, 0, result, config.length, frame.length);
        return result;
    }
    private byte[] normalize(byte[] bytes) {
        if (bytes.length == 0 || isAnnexB(bytes)) return bytes.clone();
        return lengthPrefixed(bytes);
    }
    private byte[] lengthPrefixed(byte[] bytes) {
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
