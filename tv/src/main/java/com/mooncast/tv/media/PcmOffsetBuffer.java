package com.mooncast.tv.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Frame-aligned PCM16 calibration, independent of Android for generated-byte tests.
 * Positive offsets insert silence once per flush; negative offsets discard leading frames.
 * This changes actual samples, not their timestamps or the system volume.
 *
 * <p>Only setOffsetMs/getOffsetMs may be called off the playback thread. A new offset is
 * applied by flush, never halfway through a buffer. The owner must also flush/reprepare
 * its audio output and video timeline; changing this buffer alone cannot clear AudioTrack.
 */
public final class PcmOffsetBuffer {
    public static final int STEP_MS = 50;
    public static final int MAX_OFFSET_MS = 500;
    private static final ByteBuffer EMPTY = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

    private volatile int requestedOffsetMs;
    private int pendingSampleRate;
    private int pendingChannels;
    private int frameBytes;
    private long silenceFrames;
    private long dropFrames;
    private byte[] partialFrame = new byte[0];
    private int partialBytes;
    private ByteBuffer storage = EMPTY;
    private ByteBuffer output = EMPTY;
    private boolean inputEnded;

    /** Round to the closest 50 ms, with half steps rounded away from zero, then clamp. */
    public static int normalizeOffsetMs(int value) {
        int bounded = Math.max(-MAX_OFFSET_MS, Math.min(MAX_OFFSET_MS, value));
        int magnitude = ((Math.abs(bounded) + STEP_MS / 2) / STEP_MS) * STEP_MS;
        return bounded < 0 ? -magnitude : magnitude;
    }

    /** Returns whether the requested value changed. Call flush/reprepare before applying it. */
    public boolean setOffsetMs(int value) {
        int normalized = normalizeOffsetMs(value);
        boolean changed = requestedOffsetMs != normalized;
        requestedOffsetMs = normalized;
        return changed;
    }

    public int getOffsetMs() { return requestedOffsetMs; }

    /** Configuration is pending until flush. PCM frames have one signed 16-bit sample/channel. */
    public void configure(int sampleRate, int channels) {
        if (sampleRate <= 0 || sampleRate > 768000 || channels <= 0 || channels > 32) {
            throw new IllegalArgumentException("Invalid PCM sample rate/channel count");
        }
        pendingSampleRate = sampleRate;
        pendingChannels = channels;
    }

    /** Starts a fresh timeline, forgetting all queued samples and the previous correction. */
    public void flush() {
        output = EMPTY;
        partialBytes = 0;
        inputEnded = false;
        frameBytes = pendingChannels * 2;
        partialFrame = new byte[frameBytes];
        int offset = requestedOffsetMs;
        // Integer frame rounding is at most one sample; 44.1/48 kHz use exact 50 ms steps.
        long frames = (long) Math.abs(offset) * pendingSampleRate / 1000;
        silenceFrames = offset > 0 ? frames : 0;
        dropFrames = offset < 0 ? frames : 0;
    }

    /** Releases format/sample state while retaining the user's calibration preference. */
    public void reset() {
        pendingSampleRate = 0;
        pendingChannels = 0;
        flush();
        storage = EMPTY;
    }

    /**
     * Consumes PCM bytes, retaining partial interleaved frames across chunk boundaries.
     * Drain getOutput before queuing more input. If an output is pending, input is untouched.
     */
    public void queueInput(ByteBuffer input) {
        if (frameBytes == 0) throw new IllegalStateException("Configure and flush first");
        if (inputEnded) throw new IllegalStateException("Input after end of stream");
        if (output.hasRemaining() || !input.hasRemaining()) return;
        int totalBytes = Math.addExact(partialBytes, input.remaining());
        int completeBytes = totalBytes - totalBytes % frameBytes;
        if (completeBytes == 0) {
            int count = input.remaining();
            input.get(partialFrame, partialBytes, count);
            partialBytes += count;
            return;
        }
        long discardedFrames = Math.min(dropFrames, completeBytes / frameBytes);
        int discardBytes = Math.toIntExact(discardedFrames * frameBytes);
        dropFrames -= discardedFrames;
        int copiedBytes = completeBytes - discardBytes;
        int silenceBytes = Math.toIntExact(silenceFrames * frameBytes);
        int outputBytes = Math.addExact(silenceBytes, copiedBytes);
        if (outputBytes > 0) {
            if (storage.capacity() < outputBytes) {
                storage = ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder());
            }
            storage.clear();
            storage.limit(outputBytes);
            while (silenceBytes >= Long.BYTES) {
                storage.putLong(0);
                silenceBytes -= Long.BYTES;
            }
            while (silenceBytes-- > 0) storage.put((byte) 0);
        }
        silenceFrames = 0;

        int retainedPartialBytes = Math.max(0, partialBytes - discardBytes);
        if (retainedPartialBytes > 0) {
            storage.put(partialFrame, partialBytes - retainedPartialBytes, retainedPartialBytes);
        }
        int inputDiscardBytes = Math.max(0, discardBytes - partialBytes);
        input.position(input.position() + inputDiscardBytes);
        int inputCopyBytes = copiedBytes - retainedPartialBytes;
        if (inputCopyBytes > 0) {
            int originalLimit = input.limit();
            input.limit(input.position() + inputCopyBytes);
            storage.put(input);
            input.limit(originalLimit);
        }
        partialBytes = input.remaining();
        input.get(partialFrame, 0, partialBytes);
        if (outputBytes > 0) {
            storage.flip();
            output = storage;
        }
    }

    /** No invented tail: delayed content is already queued; incomplete final frames are discarded. */
    public void queueEndOfStream() {
        inputEnded = true;
        partialBytes = 0;
        silenceFrames = 0;
        dropFrames = 0;
    }

    /** Returned data remains valid until the next queueInput/flush/reset call. */
    public ByteBuffer getOutput() {
        ByteBuffer result = output;
        output = EMPTY;
        return result;
    }

    public boolean isEnded() { return inputEnded && !output.hasRemaining(); }
}
