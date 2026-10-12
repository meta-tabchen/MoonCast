# TV Media3 audio calibration

This implementation applies only to Media3 URL playback (DLNA and AirPlay HLS),
not the native AirPlay mirroring/RAOP audio engine.

## Actual sample processing

`AudioOffsetProcessor` delegates to `PcmOffsetBuffer`:

- 0 ms copies the original PCM bytes unchanged.
- Positive offsets add that many silent PCM frames before the stream. Sound is delayed.
- Negative offsets discard that many leading PCM frames. Sound is advanced.
- Steps are 50 ms and values are limited to −500…+500 ms.
- Interleaved channel frames stay intact, even across partial input-buffer boundaries.
- The offset is applied once after each fresh stream/seek flush. It is absolute, not
  an incremental delay. A UI calibration reset means requesting 0 ms and repreparing.
- A delayed stream retains all complete input samples and has a longer audio tail.
  An advanced stream loses its first offset-sized portion and ends earlier. A short
  stream may be completely discarded for a large negative correction. Empty input
  does not produce invented audio. An incomplete final PCM frame is discarded.
- PCM format conversions happen in Media3 before the custom processor. The processing
  path is PCM16; high-resolution/float sources can therefore lose their extra precision.

This is an actual sample transform, without rewriting sample timestamps or changing
physical TV volume. It uses the regular audio output clock. There is no additional
clock correction intended to cancel the inserted/discarded frames. Tests establish
sample-level behavior; physical display/speaker synchronization still needs measurement.

## Player integration and lifecycle

Construct one `CalibratedRenderersFactory(context, lowLatency, offsetMs)` per ExoPlayer.
Pass it to `ExoPlayer.Builder(context, factory)` and pass `factory.createLoadControl()`
to the builder's `setLoadControl`.

`factory.setOffsetMs(value)` updates the requested setting, returns whether it changed,
and is safe from the application thread. When it changes, recreate/reprepare playback
at the current position, preserving whether playback was paused. This clears pending
AudioTrack samples and resets video/audio together. A same-position `seekTo` can be
optimized away and is insufficient. Live/unseekable sources may reconnect at their
available live position rather than preserving an exact old position.

Flush discards pending output/partial frames, takes the latest setting, and starts the
correction once on the new stream. Processor reset releases format/buffer state while
retaining the user's requested calibration. A released player must not share its
factory/processor with another active player. Recreate the factory/player to change
buffering profile. This release exposes normal-speed playback; calibration at other
playback speeds and gapless playlists is not covered by the acceptance tests.

## Preventing silent bypass

The forwarding audio sink only advertises raw PCM input, so an available platform
codec must decode encoded formats. It reports audio offload unsupported, disables
float output and tunneling, and prevents silence-skipping from deleting calibration
silence. These restrictions remain in place at 0 ms to make future adjustments reliable.
A format available only through encoded HDMI passthrough may no longer play; this is
preferable to presenting a calibration control that has no effect. DRM remains outside
the receiver's scope.

## Buffering profiles

- Movie/default: 8–30 seconds of Media3 media buffering, 1 second to start, 2 seconds
  after rebuffer, and Media3's default PCM AudioTrack buffer sizing.
- Low latency: 1–5 seconds of media buffering, 250 ms to start, 500 ms after rebuffer;
  PCM buffer target 50–150 ms, platform minimum multiplied by 2. Media3 still honors
  the actual device minimum when that exceeds the target.

These settings affect the real Media3 load control and AudioTrack construction.
Low latency trades buffering protection for earlier starts and may underrun on a busy
network or slow TV. They do not promise a particular end-to-end delay, alter server HLS
segment boundaries, force a decoder's hardware low-latency mode, or adjust native AirPlay.

## Reproducible checks

`python3 scripts/run_tv_audio_tests.py` (JDK 17) checks actual PCM bytes at 44.1/48/96 kHz,
all 21 offsets, 1/2/6/8 channels, random chunk boundaries, reset/flush, repeated live-setting
changes, output draining, and empty/short/truncated/end-of-stream input. It needs no device
and does not measure A/V synchronization. Android compilation checks the adapter/factory
against the pinned Media3 1.5.1 dependency. Device acceptance should include audio+video
claps/flashes, changes through −500/0/+500 ms, seeking, pause/resume, completion, codec
changes, HDMI output, profile switches, and stop/reconnect.

## Upstream API references (pinned 1.5.1)

- [Audio processor contract](https://github.com/androidx/media/blob/1.5.1/libraries/common/src/main/java/androidx/media3/common/audio/AudioProcessor.java)
- [PCM-only processing, position and output handling](https://github.com/androidx/media/blob/1.5.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java)
- [AudioTrack buffer-size provider](https://github.com/androidx/media/blob/1.5.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioTrackBufferSizeProvider.java)
- [Forwarding sink](https://github.com/androidx/media/blob/1.5.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/ForwardingAudioSink.java)

After a Gradle build resolves dependencies, `python3 scripts/run_tv_media3_audio_tests.py`
also exercises the actual Media3 1.5.1 `AudioProcessingPipeline` with this adapter,
all 21 offsets and mono/stereo/six-channel PCM at 44.1/48 kHz. It verifies flush/reset/EOS
and uses a recording delegate to check that the real forwarding sink rejects encoded
passthrough and offload and prevents silence-skipping/tunneling bypass. Media3 classes
are extracted from the resolved official AARs; the Android SDK jar supplies platform
class definitions. No platform audio methods are invoked, so this remains a JVM
integration check rather than an AudioTrack, codec, HDMI or physical A/V test.
