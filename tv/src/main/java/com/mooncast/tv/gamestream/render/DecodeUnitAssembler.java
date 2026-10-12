/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.gamestream.render;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Pure-Java boundary for the pinned Moonlight JNI callback contract. Parameter sets are
 * separate callbacks; PICDATA is already a complete, concatenated Annex-B access unit.
 * All accepted data is copied because JNI reuses its byte array after each callback.
 * Callers serialize access, and call markQueued() only after MediaCodec accepted the picture.
 */
final class DecodeUnitAssembler {
    static final int PICTURE = 0;
    static final int SPS = 1;
    static final int PPS = 2;
    static final int P_FRAME = 0;
    static final int IDR_FRAME = 1;
    static final int MAX_PICTURE_BYTES = 8 * 1024 * 1024;
    static final int MAX_CONFIG_BYTES = 64 * 1024;

    static final class AccessUnit {
        final byte[] codecConfig;
        final byte[] picture;
        final boolean keyFrame;

        AccessUnit(byte[] codecConfig, byte[] picture, boolean keyFrame) {
            this.codecConfig = codecConfig;
            this.picture = picture;
            this.keyFrame = keyFrame;
        }
    }

    private final ByteArrayOutputStream parameterSets = new ByteArrayOutputStream();
    private byte[] cachedConfig;
    private boolean sawSps;
    private boolean sawPps;
    private boolean haveFrame;
    private int frameNumber;
    private int currentFrameType;
    private boolean pictureSubmitted;
    private boolean awaitingIdr = true;

    /** Returns null for a parameter-set callback, or a complete picture for PICDATA. */
    AccessUnit accept(byte[] data, int length, int type, int number, int frameType) {
        if (data == null || length <= 0 || length > data.length) {
            throw new IllegalArgumentException("Invalid decode unit length");
        }
        if (frameType != P_FRAME && frameType != IDR_FRAME) {
            throw new IllegalArgumentException("Unknown frame type");
        }
        if (type != PICTURE && type != SPS && type != PPS) {
            throw new IllegalArgumentException("Only H.264 picture/SPS/PPS units are supported");
        }
        if (length > (type == PICTURE ? MAX_PICTURE_BYTES : MAX_CONFIG_BYTES)) {
            throw new IllegalArgumentException("Decode unit exceeds receiver size bound");
        }
        if (!haveFrame || number != frameNumber) {
            resetPendingFrame();
            haveFrame = true;
            frameNumber = number;
            currentFrameType = frameType;
        } else if (currentFrameType != frameType || pictureSubmitted) {
            throw new IllegalArgumentException("Inconsistent or duplicate frame callbacks");
        }

        int nalTypes = validateAnnexB(data, length);
        if (type == SPS || type == PPS) {
            int requiredNal = type == SPS ? 7 : 8;
            if (frameType != IDR_FRAME || nalTypes != (1 << requiredNal)) {
                throw new IllegalArgumentException("Parameter-set type does not match IDR callback");
            }
            if (type == SPS) {
                validateSpsFormats(data, length);
            }
            if (parameterSets.size() + length > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException("Too many parameter sets");
            }
            parameterSets.write(data, 0, length);
            sawSps |= type == SPS;
            sawPps |= type == PPS;
            return null;
        }

        boolean idr = frameType == IDR_FRAME;
        int pictureType = idr ? 5 : 1;
        int otherPictureType = idr ? 1 : 5;
        if ((nalTypes & (1 << pictureType)) == 0
                || (nalTypes & (1 << otherPictureType)) != 0
                || (nalTypes & ((1 << 7) | (1 << 8))) != 0) {
            throw new IllegalArgumentException("Picture NAL types do not match callback frame type");
        }
        if (!idr && awaitingIdr) {
            throw new IllegalArgumentException("Waiting for a complete IDR frame");
        }

        byte[] config = null;
        if (idr) {
            if (parameterSets.size() != 0) {
                if (!sawSps || !sawPps) {
                    throw new IllegalArgumentException("IDR has an incomplete SPS/PPS pair");
                }
                cachedConfig = parameterSets.toByteArray();
            }
            if (cachedConfig == null) {
                throw new IllegalArgumentException("IDR arrived before SPS and PPS");
            }
            config = Arrays.copyOf(cachedConfig, cachedConfig.length);
        }
        pictureSubmitted = true;
        return new AccessUnit(config, Arrays.copyOf(data, length), idr);
    }

    void markQueued(AccessUnit unit) {
        if (unit.keyFrame) {
            awaitingIdr = false;
        }
    }

    /** Keep known parameter sets, but do not feed dependent frames after a dropped picture. */
    void requireIdr() {
        awaitingIdr = true;
        resetPendingFrame();
        haveFrame = false;
    }

    private void resetPendingFrame() {
        parameterSets.reset();
        sawSps = false;
        sawPps = false;
        pictureSubmitted = false;
    }

    private static int startCodeLength(byte[] data, int position, int length) {
        if (position + 3 <= length && data[position] == 0 && data[position + 1] == 0) {
            if (data[position + 2] == 1) return 3;
            if (position + 4 <= length && data[position + 2] == 0 && data[position + 3] == 1) {
                return 4;
            }
        }
        return 0;
    }

    private static int findNextStart(byte[] data, int position, int length) {
        for (int i = position; i < length - 2; i++) {
            if (startCodeLength(data, i, length) != 0) return i;
        }
        return length;
    }

    private static int validateAnnexB(byte[] data, int length) {
        int position = 0;
        int nalTypes = 0;
        while (position < length) {
            int prefix = startCodeLength(data, position, length);
            int header = position + prefix;
            if (prefix == 0 || header >= length || (data[header] & 0x80) != 0) {
                throw new IllegalArgumentException("Malformed H.264 Annex-B NAL unit");
            }
            int nalType = data[header] & 0x1f;
            if (nalType != 1 && nalType != 5 && (nalType < 6 || nalType > 12)) {
                throw new IllegalArgumentException("Unsupported H.264 NAL type: " + nalType);
            }
            int next = findNextStart(data, header + 1, length);
            if (next <= header + 1) {
                throw new IllegalArgumentException("Empty H.264 NAL payload");
            }
            nalTypes |= 1 << nalType;
            position = next;
        }
        return nalTypes;
    }

    /** Reject High 10/4:2:2/4:4:4 and non-8-bit SPS, even if negotiation was wrong. */
    private static void validateSpsFormats(byte[] data, int length) {
        int position = 0;
        while (position < length) {
            int header = position + startCodeLength(data, position, length);
            int next = findNextStart(data, header + 1, length);
            Bits bits = new Bits(data, header + 1, next);
            int profile = bits.read(8);
            bits.read(8); // constraint flags and reserved bits
            bits.read(8); // level_idc
            bits.unsignedExpGolomb(); // seq_parameter_set_id
            if (profile == 100) {
                if (bits.unsignedExpGolomb() != 1
                        || bits.unsignedExpGolomb() != 0
                        || bits.unsignedExpGolomb() != 0) {
                    throw new IllegalArgumentException("Only 8-bit 4:2:0 H.264 is supported");
                }
            } else if (profile != 66 && profile != 77 && profile != 88) {
                throw new IllegalArgumentException("Unsupported H.264 profile: " + profile);
            }
            position = next;
        }
    }

    /** Minimal escaped RBSP reader for the SPS format fields above; not an SPS rewriter. */
    private static final class Bits {
        private final byte[] rbsp;
        private int bit;

        Bits(byte[] data, int begin, int end) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(end - begin);
            int zeros = 0;
            for (int i = begin; i < end; i++) {
                int value = data[i] & 0xff;
                if (zeros >= 2 && value == 3) {
                    if (i + 1 >= end || (data[i + 1] & 0xff) > 3) {
                        throw new IllegalArgumentException("Malformed H.264 escape byte");
                    }
                    zeros = 0;
                    continue;
                }
                bytes.write(value);
                zeros = value == 0 ? zeros + 1 : 0;
            }
            rbsp = bytes.toByteArray();
        }

        int read(int count) {
            if (bit + count > rbsp.length * 8) {
                throw new IllegalArgumentException("Truncated SPS format fields");
            }
            int result = 0;
            for (int i = 0; i < count; i++, bit++) {
                result = (result << 1) | ((rbsp[bit / 8] >> (7 - bit % 8)) & 1);
            }
            return result;
        }

        int unsignedExpGolomb() {
            int zeros = 0;
            while (read(1) == 0) {
                if (++zeros > 20) throw new IllegalArgumentException("Oversized SPS value");
            }
            return (1 << zeros) - 1 + read(zeros);
        }
    }
}
