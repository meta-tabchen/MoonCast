/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.airplay;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Packed 38-byte AudioDebugData from android-airplay-server c8defdd, not a guessed ABI. */
public final class NativeAudioStats {
    public final int backlogMs, cushionMs, decodeMeanUs, decodeMaxUs, heldBuffers;
    public final long trims, droppedPackets, insertedSilences, underruns, xruns, decodeErrors;
    private NativeAudioStats(ByteBuffer data) {
        ByteBuffer b = data.duplicate().order(ByteOrder.LITTLE_ENDIAN); b.position(0);
        backlogMs = b.getShort() & 65535; cushionMs = b.getShort() & 65535;
        trims = unsigned(b.getInt()); droppedPackets = unsigned(b.getInt());
        insertedSilences = unsigned(b.getInt()); underruns = unsigned(b.getInt());
        decodeMeanUs = b.getInt(); decodeMaxUs = b.getInt(); heldBuffers = b.getShort() & 65535;
        xruns = unsigned(b.getInt()); decodeErrors = unsigned(b.getInt());
    }
    static NativeAudioStats read(ByteBuffer data) {
        if (data == null || data.limit() < 38) throw new IllegalArgumentException("Incomplete native audio stats");
        return new NativeAudioStats(data);
    }
    private static long unsigned(int value) { return value & 0xffffffffL; }
}
