package com.mooncast.host;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.projection.MediaProjection;
import android.os.Build;

/** Android-permitted playback capture only. RAOP expects 44.1 kHz stereo S16LE,
 * 352 samples per ALAC frame. No microphone or privileged audio fallback. */
class AirPlayPlaybackAudio implements AutoCloseable {
    interface Output { void audio(byte[] pcm); void failed(String reason); }
    private AudioRecord recorder;
    private volatile boolean running;
    private Thread reader;
    void start(Context context, MediaProjection projection, Output output) {
        if (Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException(context.getString(R.string.ui_audio_permission_denied_continuing_with_video_only));
        AudioPlaybackCaptureConfiguration capture = new AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
        int minimum = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum < 0) throw new IllegalStateException(context.getString(R.string.phone_pcm_unavailable));
        recorder = new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setSampleRate(44100)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(Math.max(minimum, 1408 * 8)).setAudioPlaybackCaptureConfig(capture).build();
        recorder.startRecording();
        if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException(context.getString(R.string.phone_audio_not_started));
        running = true; AudioRecord active = recorder;
        reader = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            byte[] pcm = new byte[1408]; int used = 0;
            try {
                while (running) {
                    int count = active.read(pcm, used, pcm.length - used, AudioRecord.READ_BLOCKING);
                    if (count <= 0) { if (running) output.failed(context.getString(R.string.phone_audio_read_failed,count)); break; }
                    used += count;
                    if (used == pcm.length) { output.audio(pcm); used = 0; }
                }
            } catch (RuntimeException | LinkageError e) { if (running) output.failed(e.getMessage()); }
        }, "AirPlayPCM"); reader.start();
    }
    @Override public void close() {
        running = false;
        AudioRecord current = recorder; recorder = null;
        if (current != null) { try { current.stop(); } catch (RuntimeException ignored) {} try { current.release(); } catch (RuntimeException ignored) {} }
        if (reader != null && reader != Thread.currentThread()) try { reader.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        reader = null;
    }
}
