package com.mooncast.host;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

/** Android-permitted playback PCM only; never opens a microphone or privileged fallback. */
final class BrowserPlaybackAudio implements AutoCloseable {
    static final int SAMPLE_RATE = 48_000;
    static final int CHANNELS = 2;
    private static final int BLOCK_BYTES = SAMPLE_RATE / 100 * CHANNELS * 2;
    private static final long BLOCK_NS = 10_000_000L;
    interface Failure { void failed(String message); }
    private final byte[][] blocks = new byte[4][BLOCK_BYTES];
    private final byte[] silence = new byte[BLOCK_BYTES];
    private final Object queue = new Object();
    private int head, count;
    private volatile boolean running;
    private AudioRecord recorder;
    private Thread reader;
    private long nextCallbackNs;

    void start(Context context, MediaProjection projection, Failure failure) {
        if (Build.VERSION.SDK_INT < 29)
            throw new IllegalStateException("Playback audio needs Android 10 or later");
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException("Playback audio permission was not granted");
        if (recorder != null) throw new IllegalStateException("Playback capture already started");
        AudioPlaybackCaptureConfiguration capture = new AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
        int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IllegalStateException("Playback PCM format is unavailable");
        AudioRecord active = new AudioRecord.Builder()
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(Math.max(minimum * 2, BLOCK_BYTES * 4))
            .setAudioPlaybackCaptureConfig(capture).build();
        try {
            if (active.getState() != AudioRecord.STATE_INITIALIZED)
                throw new IllegalStateException("Playback capture could not initialize");
            active.startRecording();
            if (active.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("Playback capture did not start");
        } catch (RuntimeException error) {
            active.release();
            throw error;
        }
        recorder = active;
        running = true;
        reader = new Thread(() -> read(active, failure), "BrowserPlaybackPCM");
        reader.start();
    }

    private void read(AudioRecord active, Failure failure) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        byte[] block = new byte[BLOCK_BYTES];
        int used = 0;
        try {
            while (running) {
                int size = active.read(block, used, block.length - used, AudioRecord.READ_BLOCKING);
                if (size <= 0) throw new IllegalStateException("Playback audio read failed: " + size);
                used += size;
                if (used == block.length) {
                    synchronized (queue) {
                        // Drop oldest PCM: a paused/reconnecting receiver must not accumulate delay.
                        if (count == blocks.length) { head = (head + 1) % blocks.length; count--; }
                        System.arraycopy(block, 0, blocks[(head + count) % blocks.length], 0, block.length);
                        count++;
                    }
                    used = 0;
                }
            }
        } catch (RuntimeException error) {
            if (running) { running = false; failure.failed(error.getMessage()); }
        } finally {
            running = false;
            synchronized (queue) { count = 0; }
            try { active.stop(); } catch (RuntimeException ignored) { }
            active.release();
        }
    }

    boolean available() { return running; }

    /** SDK 144's recordingless ADM does not sleep: the callback must pace its 10 ms blocks. */
    long fill(ByteBuffer buffer, int format, int channels, int sampleRate) {
        long now = System.nanoTime();
        if (nextCallbackNs == 0 || now - nextCallbackNs > BLOCK_NS * 3) nextCallbackNs = now;
        long wait = nextCallbackNs - now;
        while (wait > 0 && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(wait);
            wait = nextCallbackNs - System.nanoTime();
        }
        nextCallbackNs += BLOCK_NS;
        buffer.clear();
        if (running && format == AudioFormat.ENCODING_PCM_16BIT && channels == CHANNELS
                && sampleRate == SAMPLE_RATE && buffer.capacity() == BLOCK_BYTES) {
            synchronized (queue) {
                if (count > 0) {
                    buffer.put(blocks[head]); head = (head + 1) % blocks.length; count--;
                }
            }
        }
        while (buffer.hasRemaining()) buffer.put(silence, 0, Math.min(buffer.remaining(), silence.length));
        buffer.rewind();
        // No fabricated capture timestamp; libwebrtc uses its monotonic recording clock.
        return 0;
    }

    @Override public void close() {
        running = false;
        AudioRecord active = recorder; recorder = null;
        if (active != null) try { active.stop(); } catch (RuntimeException ignored) { }
        Thread current = reader; reader = null;
        if (current != null && current != Thread.currentThread()) {
            try { current.join(500); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }
        // The reader releases AudioRecord after read exits, even if the bounded join timed out.
        synchronized (queue) { count = 0; }
    }
}
