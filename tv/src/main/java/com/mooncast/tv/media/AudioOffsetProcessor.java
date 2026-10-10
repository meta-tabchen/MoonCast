package com.mooncast.tv.media;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/** Media3 adapter for the tested PCM sample transform. One instance belongs to one audio sink. */
@UnstableApi
public final class AudioOffsetProcessor implements AudioProcessor {
    private final PcmOffsetBuffer pcm = new PcmOffsetBuffer();
    private AudioFormat pendingFormat = AudioFormat.NOT_SET;

    public AudioOffsetProcessor(int offsetMs) { pcm.setOffsetMs(offsetMs); }

    public boolean setOffsetMs(int offsetMs) { return pcm.setOffsetMs(offsetMs); }
    public int getOffsetMs() { return pcm.getOffsetMs(); }

    @Override public AudioFormat configure(AudioFormat inputFormat) throws UnhandledAudioFormatException {
        if (inputFormat.encoding != C.ENCODING_PCM_16BIT
                || inputFormat.sampleRate <= 0 || inputFormat.sampleRate > 768000
                || inputFormat.channelCount <= 0 || inputFormat.channelCount > 32) {
            throw new UnhandledAudioFormatException(inputFormat);
        }
        pcm.configure(inputFormat.sampleRate, inputFormat.channelCount);
        pendingFormat = inputFormat;
        // Remain in the chain at zero so a future calibration cannot be bypassed.
        return inputFormat;
    }

    @Override public boolean isActive() { return pendingFormat != AudioFormat.NOT_SET; }
    @Override public void queueInput(ByteBuffer input) { pcm.queueInput(input); }
    @Override public void queueEndOfStream() { pcm.queueEndOfStream(); }
    @Override public ByteBuffer getOutput() { return pcm.getOutput(); }
    @Override public boolean isEnded() { return pcm.isEnded(); }
    @Override public void flush() { pcm.flush(); }
    @Override public void reset() {
        pcm.reset();
        pendingFormat = AudioFormat.NOT_SET;
    }
}
