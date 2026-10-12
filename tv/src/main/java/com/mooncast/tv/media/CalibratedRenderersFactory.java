package com.mooncast.tv.media;

import android.content.Context;
import android.annotation.TargetApi;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;

/**
 * Media3 URL/HLS/DASH playback with an unavoidable decoded-PCM calibration path.
 * Native AirPlay mirroring/audio uses a separate engine and is not modified by this factory.
 * Create a new factory for each player and whenever the buffering profile changes.
 */
@UnstableApi
public final class CalibratedRenderersFactory extends DefaultRenderersFactory {
    private final boolean lowLatency;
    private final AudioOffsetProcessor offsetProcessor;
    private final MediaPlaybackBarrier playbackBarrier = new MediaPlaybackBarrier();

    public CalibratedRenderersFactory(Context context, boolean lowLatency, int offsetMs) {
        super(context);
        this.lowLatency = lowLatency;
        offsetProcessor = new AudioOffsetProcessor(offsetMs);
        setEnableDecoderFallback(true);
    }

    /**
     * Update requested calibration. When true, recreate/reprepare the current media at its
     * current position, preserving playWhenReady. A same-position seek may be a no-op and
     * cannot guarantee clearing old AudioTrack samples. Never stack incremental silence.
     */
    public boolean setOffsetMs(int offsetMs) { return offsetProcessor.setOffsetMs(offsetMs); }
    public int getOffsetMs() { return offsetProcessor.getOffsetMs(); }
    public MediaPlaybackBarrier playbackBarrier() { return playbackBarrier; }

    /** Movie mode favors buffering margin; low-latency mode trades margin for faster starts. */
    public DefaultLoadControl createLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(lowLatency ? 1000 : 8000, lowLatency ? 5000 : 30000,
                        lowLatency ? 250 : 1000, lowLatency ? 500 : 2000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    @Override protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                                  boolean enableAudioTrackPlaybackParams) {
        DefaultAudioSink.Builder builder = new DefaultAudioSink.Builder(context)
                .setAudioProcessors(new AudioProcessor[] {offsetProcessor})
                // Float output bypasses custom processors in Media3 1.5.1.
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(false);
        if (lowLatency) {
            builder.setAudioTrackBufferSizeProvider(new DefaultAudioTrackBufferSizeProvider.Builder()
                    .setMinPcmBufferDurationUs(50000)
                    .setMaxPcmBufferDurationUs(150000)
                    .setPcmBufferMultiplicationFactor(2)
                    .build());
        }
        return playbackBarrier.wrap(new PcmOnlyAudioSink(builder.build()));
    }

    /** HDMI capabilities must not let AC3/EAC3/etc skip the processor through passthrough. */
    static final class PcmOnlyAudioSink extends ForwardingAudioSink {
        PcmOnlyAudioSink(AudioSink sink) { super(sink); }

        @Override public boolean supportsFormat(Format format) {
            return getFormatSupport(format) != SINK_FORMAT_UNSUPPORTED;
        }
        @Override public int getFormatSupport(Format format) {
            return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)
                    ? super.getFormatSupport(format) : SINK_FORMAT_UNSUPPORTED;
        }
        @Override public AudioOffloadSupport getFormatOffloadSupport(Format format) {
            return AudioOffloadSupport.DEFAULT_UNSUPPORTED;
        }
        @TargetApi(29)
        @Override public void setOffloadMode(int mode) {
            super.setOffloadMode(OFFLOAD_MODE_DISABLED);
        }
        @Override public void setSkipSilenceEnabled(boolean enabled) {
            // Inserted calibration silence must not be removed by the downstream silence skipper.
            super.setSkipSilenceEnabled(false);
        }
        @Override public void enableTunnelingV21() {
            // Tunneling also bypasses PCM processors. This app does not enable tunneling.
            super.disableTunneling();
        }
    }
}
