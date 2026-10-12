/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.gamestream.render;

import java.util.ArrayDeque;

/** Bounded, copy-owning native-callback boundary, independent of Android for host tests. */
final class VideoIngress {
    static final int OK = 0;
    static final int NEED_IDR = -1;
    static final int MAX_FRAMES = 3;
    static final int MAX_BYTES = 16 * 1024 * 1024;

    static final class Frame {
        final DecodeUnitAssembler.AccessUnit unit;
        final long generation;
        final long receivedNs;
        Frame(DecodeUnitAssembler.AccessUnit unit, long generation, long receivedNs) {
            this.unit = unit;
            this.generation = generation;
            this.receivedNs = receivedNs;
        }
        int bytes() { return unit.picture.length + (unit.codecConfig == null ? 0 : unit.codecConfig.length); }
    }

    private final DecodeUnitAssembler assembler = new DecodeUnitAssembler();
    private final ArrayDeque<Frame> queue = new ArrayDeque<>();
    private int bytes;
    private long generation = 1;
    private long receivedPictures;
    private long droppedPictures;
    private boolean awaitingIdr = true;
    private boolean closed;
    private String rejection;

    synchronized int accept(byte[] data, int length, int type, int number, int frameType, long nowNs) {
        return accept(data, length, type, number, frameType, nowNs, true);
    }

    synchronized int accept(byte[] data, int length, int type, int number, int frameType, long nowNs,
                            boolean queuePictures) {
        if (closed) return NEED_IDR;
        if (type == DecodeUnitAssembler.PICTURE) receivedPictures++;
        try {
            DecodeUnitAssembler.AccessUnit unit = assembler.accept(data, length, type, number, frameType);
            if (unit == null) return OK;
            if (!queuePictures) { requireIdr(); droppedPictures++; return NEED_IDR; }
            Frame frame = new Frame(unit, generation, nowNs);
            if (queue.size() >= MAX_FRAMES || frame.bytes() > MAX_BYTES - bytes) {
                rejection = "Compressed video queue exceeded its bound; requesting IDR";
                requireIdr();
                droppedPictures++;
                return NEED_IDR;
            }
            queue.addLast(frame);
            bytes += frame.bytes();
            // Allow dependent pictures into this same bounded generation. Any codec failure
            // invalidates the whole generation, including already-popped worker frames.
            assembler.markQueued(unit);
            if (unit.keyFrame) awaitingIdr = false;
            return OK;
        } catch (IllegalArgumentException malformed) {
            rejection = malformed.getMessage();
            requireIdr();
            if (type == DecodeUnitAssembler.PICTURE) droppedPictures++;
            return NEED_IDR;
        }
    }

    synchronized Frame poll() {
        Frame frame = queue.pollFirst();
        if (frame != null) bytes -= frame.bytes();
        return frame;
    }

    synchronized void requireIdr() {
        // Do not force repeated codec flushes for every P-frame received while recovering.
        if (!awaitingIdr || !queue.isEmpty()) generation++;
        droppedPictures += queue.size();
        queue.clear();
        bytes = 0;
        awaitingIdr = true;
        assembler.requireIdr();
    }

    synchronized void dropInFlight() { droppedPictures++; requireIdr(); }
    synchronized void close() { closed = true; queue.clear(); bytes = 0; }
    synchronized long generation() { return generation; }
    synchronized int size() { return queue.size(); }
    synchronized int bytes() { return bytes; }
    synchronized long receivedPictures() { return receivedPictures; }
    synchronized long droppedPictures() { return droppedPictures; }
    synchronized boolean awaitingIdr() { return awaitingIdr; }
    synchronized String rejection() { return rejection; }
}
