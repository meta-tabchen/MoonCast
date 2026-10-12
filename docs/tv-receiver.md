# MoonCast TV receiver (0.3.0 preview)

This is a **separate Android TV receiver application**, package `com.mooncast.tv`. The existing `app/` MoonCast phone sender is unchanged. Install the TV APK from this branch's GitHub prerelease; do not replace the phone sender APK.

**Version scope:** this guide records the published TV v0.3 AirPlay/DLNA preview. The
current source tree also contains an **unreleased GameStream integration under test**
for the MoonCast phone sender, sharing the TV framing controls. It is not Android
system-picker support and has no physical phone-to-TV acceptance claim. See the
[GameStream guide](tv-gamestream.md). New TV publication requires the
[external signing gate](tv-signing.md); that does not alter historical v0.3 assets or
their debug-signing facts below.

## Start receiving / 开始接收

1. Install the preview APK on Android 8.0+ TV. Both ARM64 and ARMv7 are included (x86_64 is available for testing). This is debug-key-signed preview software, not production signing. CI runs use ephemeral keys; a later preview may require uninstalling the previous TV app, losing its preferences and pairing identity. A stable production upgrade/signing path is not configured.
2. Put your TV and phone on the same **trusted** Wi-Fi/Ethernet LAN. Disable AP/client isolation. Open MoonCast TV: reception starts automatically, without a Start click. Reopening the foreground app starts reception again; manually pausing reception lasts until the next foreground launch.
3. For an iPhone, open **Screen Mirroring / 屏幕镜像**, select the displayed MoonCast TV name. This version does not request a PIN; any device on the same reachable LAN can connect. Compatibility is experimental and requires device validation.
4. In a DLNA-capable application, select MoonCast TV as the renderer and play an unprotected supported URL. DLNA is media URL playback, not Android screen mirroring.
5. Press OK or Menu to show TV-remote controls. The redesigned home offers **Framing**, **Advanced settings**, and returning to playback. Picture/sound, connection recovery and technical diagnostics live under Advanced settings. During playback, Back opens the home controls with Return to playback focused; Back again returns to the picture without stopping. Nested settings close first, and Back in the live editor cancels its preview. Only idle home offers stop-and-exit. Leaving the app stops listeners, discovery and audio; it does not continue as a hidden background receiver.

## Android system casting

Android’s built-in wireless-display menu is different from a DLNA video-app target. This release fixes burst/MX compatibility in actual DLNA discovery, but does not implement Miracast or generic Google Cast mirroring. See the [Android system casting assessment and verified OEM paths](tv-android-system-casting.md). The home guide makes this distinction explicit.

## Implemented compatibility matrix

| Protocol / feature | Implementation | Validation / limitations |
| --- | --- | --- |
| AirPlay screen mirroring | Actual UxPlay protocol core, JNI bridge, Android MediaCodec AVC/HEVC decoder, mDNS discovery, PIN-free connections | No physical iPhone/TV interoperability claim; depends on iOS, network and device decoder |
| AirPlay audio | Native receiver audio engine, Oboe output | TV remote controls physical volume; sender volume interoperability unverified |
| AirPlay HLS media | Native local proxy with Media3 HLS playback and transport feedback | Clear content only; individual sender applications may restrict output |
| DLNA / UPnP AV | SSDP renderer discovery, AVTransport, RenderingControl, ConnectionManager, GENA notifications | HTTP(S) media supplied by controllers; codecs/container depend on TV; not screen mirroring |
| MP4 / HLS / DASH / audio | AndroidX Media3, device codec support, audio focus | No DRM keys, encrypted-service bypass or universal codec guarantee |
| Google Cast / Chromecast | Not implemented or advertised | Requires a different receiver ecosystem; DLNA cannot substitute for Cast |
| Miracast / Wi-Fi Direct display | Not implemented or advertised | Requires suitable platform support; not a normal LAN socket protocol |
| AirPlay 2 multiroom / DRM | Unsupported | No claim of certified Apple compatibility or DRM playback |
| Moonlight / GameStream client | Not in the published TV v0.3 APK; integrated in the current unreleased tree | [MoonCast-phone companion route](tv-gamestream.md), H.264 SDR/stereo, under test; the published sender workflow still supports official Moonlight |

Discovery is not proof of playback. Compilation, native-library verification and generated protocol tests are not evidence of physical iPhone/TV interoperability, latency, 4K60, HDR, thermal stability or a security audit.

## Live framing with the TV remote

- **Fit** preserves the whole source aspect ratio with letterboxing.
- **Fill** uses uniform scaling to fill the screen, intentionally cropping edges.
- **Live crop / zoom / position** leaves the video visible behind a transparent editor with a small translucent control strip. Left, top, right and bottom are independent, each 0–45% of the source. At least 20% of each source dimension is retained, so opposite edges together cannot exceed 80%. Zoom is proportional, 100–300%; horizontal and vertical position range from −100% to +100% of available travel. The picture is never stretched.
  - Up/down selects zoom, horizontal position, vertical position or an edge.
  - Left/right changes the selected value by 1%; holding the key accelerates to 5% steps.
  - OK saves; Back cancels the preview and restores the prior framing. Menu previews a full-image reset; OK saves that reset, while Back still cancels it. **Restore full image** in the Framing menu saves a global Fit/full-image choice directly.
  - **One app-wide choice:** first use defaults to Auto/remove black bars. Mode and manual edges/zoom/pan are remembered across AirPlay/DLNA and portrait/landscape. Auto-detected coordinates are transient and recalculated for each new video/source or size; they are never copied from another video. A source/size change cancels obsolete edits. v0.2 profiles have no last-choice timestamp, so upgrading without a v0.3 global value starts Auto rather than guessing an old profile. No sender identities, media names or URLs are saved.
- **Auto / remove black bars** samples 160×90 frames twice a second and conservatively checks paired black borders, interior brightness and visible edge content. Ordinary borders require eight stable samples; wide borders, including a portrait picture inside a landscape stream, require twelve and stronger evidence. Mostly-dark or ambiguous frames do not acquire a crop.
  - After locking, the crop does not automatically tighten. Two consecutive samples with newly visible content in a removed border expand that axis to help reveal captions or overlays. This is a safety heuristic, not guaranteed subtitle detection.
  - Use **Rescan black bars** after content changes to acquire a new crop. Burned-in subtitles move with the picture and manual crop/zoom can hide them; use Fit or Restore full image if needed.

## Picture and sound

**Media resolution limit** chooses existing Media3 tracks up to 1080p or 720p, or leaves selection adaptive/native. **AirPlay mirror settings** separately request up to 1080p60, 1080p30 or 720p60. These are negotiation preferences, not a guarantee of received resolution or frame rate. Neither restores missing source detail or changes a fixed file's encoding. Runtime information distinguishes requested mirror settings from decoder-reported dimensions, decoder name and observed output counters; it is not a measurement of screen presentation or end-to-end latency.

There are two separate audio paths:

- **Media playback / audio calibration** applies to Media3 URL playback, including DLNA and AirPlay HLS. Set −500…+500 ms in 50 ms steps, saved separately for DLNA and AirPlay media. Positive values insert silent PCM frames to delay sound; negative values discard leading PCM frames to advance it. This is real sample processing and loses the discarded start of the audio. It is applied once after a fresh stream/seek flush. Changing the setting recreates playback at the current position and preserves the play/pause choice; live or unseekable sources may resume at an available live position instead.
- Media3 **Movie** mode uses 8–30 seconds of media buffering, 1 second to start and 2 seconds after rebuffer. **Low latency** uses 1–5 seconds, 250 ms to start and 500 ms after rebuffer, plus a smaller 50–150 ms PCM buffer target subject to the device minimum. These are actual buffering settings, not measured end-to-end delay. Low latency can increase stutter on weak networks.
- Calibration uses decoded PCM16. HDMI encoded passthrough, offload, float output, tunneling and silence skipping cannot bypass the processor, even at 0 ms. A format supported only by encoded passthrough may therefore fail to play; high-resolution sources can lose extra precision. See [Media3 audio calibration](tv-audio-calibration.md) for the processing, lifecycle and test details.
- **AirPlay audio jitter buffer** controls the separate native mirror/audio-only engine. Movie defaults to a real 150 ms audio prebuffer and source-PTS-relative video pacing with a matching initial holdback. Low latency defaults to adaptive audio buffering and immediate video output; hardware may decline low-latency requests. An explicit buffer choice can override the mode default: Adaptive, or 50–500 ms in 50 ms steps.
- Native jitter buffering is not a signed A/V correction. The pinned native interface has no signed audio-offset control or shared audio playback clock. Matching initial cushions is approximate pacing, not calibrated lip sync. Applying mirror settings while receiving asks to restart, stops playback and requires reconnecting from the phone.

AirPlay and DLNA share one active playback experience. Starting DLNA temporarily stops AirPlay's native listener/audio to avoid two audio sources; stopping media re-enables AirPlay discovery. Network changes stop existing playback and rediscover the new LAN. Audio/video continue only while this Activity is foreground.

## Recovery and receiver identity

- AirPlay PIN is removed from the application UI and disabled for every native start/restart, including upgrades with an old PIN preference. Use only on a trusted home LAN. This is convenience, not authenticated access control.
- **Disconnected-session recovery** defaults to 15 seconds; the UI also offers Off, 30 and 60 seconds. Silence alone does not imply disconnection. The mirror watchdog waits for a witnessed close of the final control TCP connection and no further video for the full grace period. A rendered static or paused screen with an open connection can remain indefinitely. Separately, a presentable video packet with no decoder output for the grace period can trigger decoder-stall recovery.
- A Wi-Fi failure whose TCP connection has not yet been reported closed cannot safely be distinguished from a static screen. There is no promise of recovery within 15 seconds of every network failure. The mirror watchdog does not time out URL playback or audio-only sessions. Recovery clears the old picture/audio and restarts the receiver while foreground reception is still requested; it cannot force the phone to reconnect.
- **Reset current session (keep identity)** is a manual recovery option. Ordinary stop/start and automatic recovery never delete receiver keys or rotate receiver identity. No PIN challenge is required after restarting.
- **Reset receiver identity / pair again** is a separate, explicitly confirmed destructive action. It stops playback, deletes this app's AirPlay receiver key and stored AirPlay network identity, and creates a new identity on restart. Senders may need to discover/select the TV again; a stale phone entry may need manual removal. This is a global identity reset. The native interface exposes no sender-name/paired-device list or per-device revocation.

## Security and privacy

The app uses no cloud account, analytics or microphone/camera capture. Pairing identity stays in private app storage. AirPlay has no PIN challenge. DLNA control is not authenticated: any allowed local subnet device may request media or change in-app volume. Never port-forward the receiver. AirPlay's native listeners bind available interfaces, so trusted-LAN use matters; this preview is not a hardened Internet service. DLNA callbacks and incoming controls are restricted to the selected IPv4 subnet and bounded in size/time/concurrency. Media URLs may access public CDN hosts. Servers must provide ordinary valid HTTP framing. Seeking more than 8 MiB into a server that ignores byte ranges is rejected rather than downloading unbounded skipped data. The HTTP transport validates each manifest/segment/key request and redirect, rejects resolved loopback/link-local/infrastructure addresses, and allows the native HLS proxy only on its current explicit port. DNS is re-resolved by the platform at connect time, so this is not a complete DNS-rebinding defense. Protected media and credentials are not extracted or bypassed. Do not enter sensitive media URLs into untrusted controller apps.

## Rebuild and release

The following is the v0.3 build/release reference. For the current integration tree,
also run the [GameStream checks](tv-gamestream.md#source-build-and-validation) and
follow [TV signing and opt-in publication](tv-signing.md): without external signing
inputs, `:tv:assembleRelease` now emits `tv-release-unsigned.apk`, not the signed
`tv-release.apk` in the historical commands below. Do not publish an unsigned or
development debug APK as the next TV release.

Use JDK 17 and the pinned Android SDK/build tools from `.github/workflows/tv-receiver.yml`. The real-Media3 JVM check requires dependencies resolved by the Gradle build and Android SDK 35; set `JAVA_HOME` and `ANDROID_HOME` as appropriate.

```sh
python3 scripts/prepare_airplay_native.py
python3 scripts/prepare_airplay_source.py
python3 scripts/run_unit_tests.py
python3 scripts/run_tv_unit_tests.py
python3 scripts/run_airplay_unit_tests.py
python3 scripts/run_tv_audio_tests.py
./gradlew :tv:assembleDebug :tv:assembleRelease :tv:lintDebug :tv:lintRelease :tv:testDebugUnitTest
python3 scripts/run_tv_media3_audio_tests.py
python3 scripts/verify_tv_apk.py tv/build/outputs/apk/release/tv-release.apk
```

These are verification steps, not a statement that the final v0.3 CI run has passed. The release gate must include Java 17 builds/lint, portable framing/session/protocol/PCM tests, the actual Media3 pipeline/sink check, native/APK payload verification, `apksigner` and `zipalign`. The Gradle JVM unit-test task has no src/test sources; the portable runners provide host coverage. The separate androidTest UI suite is compiled by CI and must be executed on an emulator/device separately. Generated PCM tests cover all 21 signed offsets, sample rates, channel layouts, chunk boundaries and lifecycle changes. The Media3 integration runner uses the pinned 1.5.1 classes to exercise pipeline flush/reset/end-of-stream and prevent audio-processing bypass. Neither runner opens physical audio output or measures A/V sync.

See `native/airplay/` for exact upstream commits, runtime/source SHA-256 and retained licenses. The pinned native source and binaries are unchanged in v0.3; the receiver uses the existing JNI interface, not a newly rebuilt native engine. AirPlay is based on jqssun/android-airplay-server / UxPlay under GPLv3; Media3 is Apache-2.0. The generated corresponding-source archive accompanies APK and SHA256SUMS in each prerelease. Preview releases are immutable and identified by CI run and commit. Build steps and source fetching must finish before release publication. `scripts/package_tv_release.py` packages a clean, committed checkout only after verification; it does not build, run the checks or publish by itself. The workflow verifies published asset hashes again after download.

## Device acceptance still needed

No physical-device, latency or A/V-synchronization validation is claimed. Install on a TV and check:

- Cold launch, remote focus and live crop preview/save/cancel/reset; all four edges, zoom/pan bounds, portrait/landscape rotation and app-wide choice persistence across sources.
- Cinema acquisition on ordinary/wide bars, dark scenes, and new subtitles/overlays after lock; Rescan and full-image recovery.
- PIN-free startup and reconnect; session reset, identity-reset cancel/confirm, iPhone portrait/landscape video with audible audio, pause/resume/reconnect.
- Static/paused mirroring longer than the watchdog timeout, interrupted Wi-Fi, decoder recovery and sender-initiated disconnect; verify old video/audio is cleared.
- DLNA MP4/HLS seek/pause/volume, AirPlay HLS, AirPlay↔DLNA takeover, network changes, and no residual media after Stop, no audio/listener after leaving the Activity/Home, and clean re-entry. Back during playback must keep playback running.
- Audible clap/visible flash tests at −500/0/+500 ms on Media3; seek, completion, HDMI output, mode changes and native buffer changes. Measure actual lip sync/latency separately from software buffer values.
- Sustained playback, frame drops and thermal behavior.

Record TV model, Android version, sender model/OS, app, codec and content type. No personal capture is required.

## Reproduce APK UI validation

The separate `androidTest` APK contains framework instrumentation; it never enters the
receiver release APK. Build `:tv:assembleDebug :tv:assembleDebugAndroidTest`, then run
`scripts/run_tv_ui_validation.py --serial emulator-5554 --output <directory>
--allow-clear-emulator-app-data --fixture <generated-H264.mp4>` with the Android SDK's
`adb` on PATH. Use a disposable Android emulator (preferably Android TV): the script refuses a
non-emulator and clears this test app's data between English and Chinese runs.
It captures actual Android screenshots and a hashed evidence manifest, checks
D-pad focus, automatic startup, PIN-disabled configuration, app-wide framing,
Back/cancel/reopen behavior, and optional locally generated DLNA video decoding.
This does not test a physical sender, Wi-Fi discovery across devices, HDMI output,
AirPlay interoperability, or end-to-end latency. CI compiles the instrumentation APK;
its device suite must be run separately and reported with its actual outcome.

With `--offline`, the harness skips LAN/decode requirements and explicitly marks
playback navigation as test-only injected session state. It still exercises the
real Activity, D-pad, dialogs, global preferences and screenshots. This mode must
not be counted as successful AirPlay, DLNA or video decoding. See
[tv-ui-validation-v03.md](tv-ui-validation-v03.md) for the recorded run and images.
