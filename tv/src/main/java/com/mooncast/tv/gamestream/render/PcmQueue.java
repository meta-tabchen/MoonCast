/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.gamestream.render;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Copy-owning FIFO bounded in sample frames and blocks; oldest audio loses on overflow. */
final class PcmQueue {
    static final int MAX_BLOCKS = 24;
    static final class Block {
        final short[] samples;
        final long receivedNs;
        Block(short[] samples, long receivedNs) { this.samples = samples; this.receivedNs = receivedNs; }
    }
    private final int channels;
    private final int maxFrames;
    private final ArrayDeque<Block> queue = new ArrayDeque<>();
    private int queuedFrames;
    private long receivedFrames;
    private long droppedFrames;
    private boolean closed;

    PcmQueue(int channels, int maxFrames) {
        if (channels <= 0 || maxFrames <= 0) throw new IllegalArgumentException("Invalid PCM bound");
        this.channels = channels;
        this.maxFrames = maxFrames;
    }

    synchronized void offer(short[] borrowed, long nowNs) {
        if (closed) return;
        if (borrowed == null || borrowed.length == 0 || borrowed.length % channels != 0) {
            throw new IllegalArgumentException("Malformed interleaved PCM block");
        }
        int frames = borrowed.length / channels;
        receivedFrames += frames;
        if (frames > maxFrames) { droppedFrames += frames; return; }
        while (queuedFrames + frames > maxFrames || queue.size() >= MAX_BLOCKS) {
            Block dropped = queue.removeFirst();
            int count = dropped.samples.length / channels;
            queuedFrames -= count;
            droppedFrames += count;
        }
        queue.addLast(new Block(Arrays.copyOf(borrowed, borrowed.length), nowNs));
        queuedFrames += frames;
    }

    synchronized Block poll() {
        Block block = queue.pollFirst();
        if (block != null) queuedFrames -= block.samples.length / channels;
        return block;
    }
    synchronized void dropFrames(int frames) { if (frames > 0) droppedFrames += frames; }
    synchronized void discardQueued() { droppedFrames += queuedFrames; queue.clear(); queuedFrames = 0; }
    synchronized void close() { closed = true; queue.clear(); queuedFrames = 0; }
    synchronized int queuedFrames() { return queuedFrames; }
    synchronized int size() { return queue.size(); }
    synchronized long receivedFrames() { return receivedFrames; }
    synchronized long droppedFrames() { return droppedFrames; }
}
