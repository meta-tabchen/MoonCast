package com.mooncast.tv.media;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

/** Generated real PCM bytes. No device, timing, codec, HDMI, or speaker claims. */
public final class TvAudioOffsetTest {
    private static int assertions;
    public static void main(String[] args) {
        testNormalization();
        testStereoSamples();
        testAllOffsetsAndSplitFrames();
        testFlushAndRepeatedChanges();
        testPendingFormatAndOutput();
        testEndOfStream();
        testInvalidInput();
        System.out.println("TV PCM audio offset tests passed (" + assertions + " assertions)");
    }

    private static void testNormalization() {
        equal(500, PcmOffsetBuffer.normalizeOffsetMs(Integer.MAX_VALUE), "positive limit");
        equal(-500, PcmOffsetBuffer.normalizeOffsetMs(Integer.MIN_VALUE), "negative limit");
        equal(0, PcmOffsetBuffer.normalizeOffsetMs(24), "substep");
        equal(0, PcmOffsetBuffer.normalizeOffsetMs(-24), "negative substep");
        equal(50, PcmOffsetBuffer.normalizeOffsetMs(25), "positive half-step");
        equal(-50, PcmOffsetBuffer.normalizeOffsetMs(-25), "negative half-step");
        PcmOffsetBuffer pcm = new PcmOffsetBuffer();
        check(pcm.setOffsetMs(100), "first change");
        check(!pcm.setOffsetMs(101), "same normalized offset is not a change");
    }

    private static void testStereoSamples() {
        // Each frame has distinct L/R values, including nonzero high bytes and negatives.
        byte[] samples = samples(3000, 2);
        PcmOffsetBuffer delay = create(48000, 2, 50);
        byte[] delayed = process(delay, samples, 317);
        equal(samples.length + 2400 * 4, delayed.length, "50 ms stereo delay length");
        bytes(new byte[2400 * 4], Arrays.copyOf(delayed, 2400 * 4), "actual zero PCM prefix");
        bytes(samples, Arrays.copyOfRange(delayed, 2400 * 4, delayed.length), "exact samples after silence");
        PcmOffsetBuffer advance = create(48000, 2, -50);
        bytes(Arrays.copyOfRange(samples, 2400 * 4, samples.length), process(advance, samples, 241),
                "advance drops whole stereo frames, preserving L/R channels");
        PcmOffsetBuffer zero = create(48000, 2, 0);
        bytes(samples, process(zero, samples, 1), "zero offset is byte-for-byte identity");
    }

    private static void testAllOffsetsAndSplitFrames() {
        for (int rate : new int[] {44100, 48000, 96000}) {
            for (int channels : new int[] {1, 2, 6, 8}) {
                byte[] input = samples(rate / 2 + 59, channels);
                for (int offset = -500; offset <= 500; offset += 50) {
                    PcmOffsetBuffer pcm = create(rate, channels, offset);
                    byte[] actual = process(pcm, input, 1031);
                    int correctionBytes = (int) ((long) Math.abs(offset) * rate / 1000) * channels * 2;
                    byte[] expected;
                    if (offset < 0) expected = Arrays.copyOfRange(input, correctionBytes, input.length);
                    else {
                        expected = new byte[input.length + correctionBytes];
                        System.arraycopy(input, 0, expected, correctionBytes, input.length);
                    }
                    bytes(expected, actual, "rate=" + rate + " channels=" + channels + " offset=" + offset);
                    pcm.queueEndOfStream();
                    check(pcm.isEnded(), "drained EOS");
                }
            }
        }
    }

    private static void testFlushAndRepeatedChanges() {
        PcmOffsetBuffer pcm = create(48000, 2, 50);
        byte[] input = samples(2600, 2);
        bytes(expected(input, 48000, 2, 50), process(pcm, input, 133), "first delay");
        pcm.setOffsetMs(-50);
        // Offset is only adopted at a timeline flush; it must not splice an extra correction here.
        bytes(input, process(pcm, input, 137), "pending change does not mutate active samples");
        for (int round = 0; round < 60; round++) {
            int offset = round % 3 == 0 ? 50 : round % 3 == 1 ? -50 : 0;
            pcm.setOffsetMs(offset);
            pcm.flush();
            bytes(expected(input, 48000, 2, offset), process(pcm, input, 251),
                    "fresh absolute offset, no accumulated delay, round=" + round);
        }
        pcm.setOffsetMs(50);
        pcm.flush();
        pcm.queueInput(direct(new byte[] {1, 2, 3}));
        pcm.flush();
        bytes(expected(input, 48000, 2, 50), process(pcm, input, 333), "flush discards partial old frame");
        pcm.reset();
        equal(50, pcm.getOffsetMs(), "processor reset retains user setting");
        pcm.configure(44100, 1);
        pcm.flush();
        byte[] mono = samples(2300, 1);
        bytes(expected(mono, 44100, 1, 50), process(pcm, mono, 299), "reset allows new format");
        pcm.setOffsetMs(0);
        pcm.flush();
        bytes(mono, process(pcm, mono, 81), "UI calibration reset gives unmodified PCM");
    }

    private static void testPendingFormatAndOutput() {
        PcmOffsetBuffer pcm = create(48000, 2, 0);
        byte[] first = samples(1, 2);
        pcm.queueInput(direct(first));
        ByteBuffer second = direct(samples(2, 2));
        pcm.queueInput(second);
        equal(0, second.position(), "do not overwrite undrained output");
        bytes(first, drain(pcm), "preserve undrained output");
        pcm.queueInput(second.asReadOnlyBuffer());
        bytes(samples(2, 2), drain(pcm), "read-only input contents are supported");
        pcm.configure(44100, 1);
        pcm.setOffsetMs(50);
        bytes(first, process(pcm, first, 1), "configure remains pending until flush");
        pcm.flush();
        bytes(expected(first, 44100, 1, 50), process(pcm, first, 3), "new frame format adopted");
        pcm.queueInput(direct(first));
        pcm.flush();
        equal(0, pcm.getOutput().remaining(), "flush clears pending output");
    }

    private static void testEndOfStream() {
        PcmOffsetBuffer empty = create(48000, 2, 500);
        empty.queueInput(direct(new byte[0]));
        empty.queueEndOfStream();
        equal(0, empty.getOutput().remaining(), "empty stream does not invent 500 ms silence");
        check(empty.isEnded(), "empty EOS ends");
        PcmOffsetBuffer shortInput = create(48000, 2, -500);
        equal(0, process(shortInput, samples(20, 2), 7).length, "short advance drops all samples");
        shortInput.queueEndOfStream();
        check(shortInput.isEnded(), "short advance terminates without waiting for unavailable samples");
        PcmOffsetBuffer pending = create(48000, 2, 50);
        byte[] input = samples(3, 2);
        pending.queueInput(direct(input));
        pending.queueEndOfStream();
        check(!pending.isEnded(), "EOS waits for pending output");
        bytes(expected(input, 48000, 2, 50), drain(pending), "EOS retains delayed final samples");
        check(pending.isEnded(), "EOS after getOutput");
        equal(0, pending.getOutput().remaining(), "no repeated tail output");
        pending.flush();
        pending.queueInput(direct(new byte[] {1, 2, 3}));
        pending.queueEndOfStream();
        check(pending.isEnded(), "incomplete final frame does not deadlock EOS");
        equal(0, pending.getOutput().remaining(), "incomplete final frame discarded whole");
        pending.reset();
        equal(0, pending.getOutput().remaining(), "release clears output");
    }

    private static void testInvalidInput() {
        PcmOffsetBuffer pcm = new PcmOffsetBuffer();
        expectFailure(() -> pcm.configure(0, 2), "zero sample rate");
        expectFailure(() -> pcm.configure(48000, 0), "zero channels");
        expectFailure(() -> pcm.configure(Integer.MAX_VALUE, 2), "excessive rate");
        expectFailure(() -> pcm.configure(48000, 33), "excessive channel count");
        expectFailure(() -> pcm.queueInput(direct(new byte[2])), "unconfigured input");
        pcm.configure(48000, 1);
        pcm.flush();
        pcm.queueEndOfStream();
        expectFailure(() -> pcm.queueInput(direct(new byte[2])), "input after EOS");
    }

    private static PcmOffsetBuffer create(int rate, int channels, int offset) {
        PcmOffsetBuffer pcm = new PcmOffsetBuffer();
        pcm.setOffsetMs(offset);
        pcm.configure(rate, channels);
        pcm.flush();
        return pcm;
    }
    private static byte[] expected(byte[] input, int rate, int channels, int offset) {
        int shift = (int) ((long) Math.abs(offset) * rate / 1000) * channels * 2;
        if (offset < 0) return Arrays.copyOfRange(input, Math.min(shift, input.length), input.length);
        byte[] result = new byte[shift + input.length];
        System.arraycopy(input, 0, result, shift, input.length);
        return result;
    }
    private static byte[] process(PcmOffsetBuffer pcm, byte[] bytes, int maxChunk) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        Random random = new Random(19);
        for (int start = 0; start < bytes.length;) {
            int end = Math.min(bytes.length, start + 1 + random.nextInt(maxChunk));
            ByteBuffer input = direct(Arrays.copyOfRange(bytes, start, end));
            pcm.queueInput(input);
            check(!input.hasRemaining(), "input consumed");
            result.writeBytes(drain(pcm));
            start = end;
        }
        return result.toByteArray();
    }
    private static byte[] samples(int frames, int channels) {
        ByteBuffer data = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder());
        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channels; channel++) {
                data.putShort((short) (12345 + frame * 37 + channel * 7919));
            }
        }
        return data.array();
    }
    private static ByteBuffer direct(byte[] bytes) {
        ByteBuffer data = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        data.put(bytes).flip();
        return data;
    }
    private static byte[] drain(PcmOffsetBuffer pcm) {
        ByteBuffer output = pcm.getOutput();
        check(output.isDirect(), "Media3 output is direct");
        check(output.order() == ByteOrder.nativeOrder(), "Media3 output uses native byte order");
        byte[] bytes = new byte[output.remaining()];
        output.get(bytes);
        return bytes;
    }
    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }
    private static void bytes(byte[] expected, byte[] actual, String message) {
        check(Arrays.equals(expected, actual), message + " (expected " + expected.length + " bytes, got " + actual.length + ")");
    }
    private static void equal(int expected, int actual, String message) { check(expected == actual, message); }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
