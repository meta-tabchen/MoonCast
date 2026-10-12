# GameStream renderer device validation

This instrumentation suite tests the **Android renderer boundary** using small,
fully generated local media. It does not start `TvActivity`, discovery, pairing,
or a GameStream network connection, and does not create pairing/signing keys.

## Actual assertions

- A generated Annex-B H.264 stream supplies separate SPS, PPS, and IDR callbacks
  to the production `GameStreamVideoRenderer`. The callback arrays are overwritten
  immediately afterward, exercising the copied-data ownership boundary.
- The production renderer creates a real Android `MediaCodec`, configures its
  `Surface`, and queues the compressed data. A `SurfaceTexture` consumes actual
  output. GLES reads back pixels and checks red/blue patches at known positions.
  Each decoded image is saved as a PNG. Decoder output counters alone cannot pass.
- Surface replacement, null detach, IDR recovery, re-attachment, stop/cleanup,
  late callback rejection, fresh-session restart, and the production
  `GameStreamSurfaces.retire` release path are exercised. Caller-owned surfaces
  must remain valid until the caller retires them after confirmed detachment.
- Generated 48 kHz stereo PCM16 is supplied at the boundary where native Opus
  would already have decoded audio. The production renderer creates a real
  `AudioTrack`; successful writes, bounded buffers, stop/release, repeated
  cleanup, restart, and malformed-callback shutdown are asserted.

## Run on a dedicated Android test device

Requirements: configured Android build tools, a connected authorized emulator or
test device, and the existing project debug signing identity. Do not generate a
production signing identity or pairing identity for this suite.

```sh
./gradlew :tv:assembleDebug :tv:assembleDebugAndroidTest
python3 scripts/run_gamestream_renderer_device_test.py \
  --adb /path/to/adb --serial emulator-5580 --install \
  --output /private/local/path/gamestream-renderer-evidence
```

The explicit `--install` option installs/replaces only the already-built debug
application and its test APK on the selected device. Omit it if those exact APKs
are already installed. The script checks the instrumentation pass marker, the
JSON result, every individual check, and all four decoded-pixel PNGs. The old
TV UI suite remains the default; this suite is selected with
`-e suite gamestream-renderer`.

For software-emulated AOSP in a container, keep the emulator, `adb` server and
runner in the same process/network namespace. Isolate emulator `TMPDIR` and
`ANDROID_TMP` from other emulator instances: a shared netsim discovery file can
point at a server unreachable from another namespace. Reuse official SDK tools
and an existing debug identity; do not weaken security settings. Wait for
`sys.boot_completed=1` before installation. Software emulation can take several
minutes to boot and is unsuitable for performance measurements.

The evidence directory contains `report.json`, the instrumentation transcript,
and four PNGs from actual decoded output. Keep local emulator logs and device
identifiers private and out of publication archives. The synthesized H.264
fixture and its reproducible generation command live under
`tv/src/androidTest/assets/gamestream-renderer/` and are excluded from app APKs.

## Evidence limits

Passing means actual Android codec-to-Surface pixels, PCM AudioTrack writes,
and the tested lifecycle worked on the reported Android build. It does **not**
establish native Opus decoding, audible output, GameStream RTP/FEC/network
transport, mDNS, pairing, input, a real Sunshine/MoonCast sender, physical-TV
hardware compatibility, A/V synchronization, bitrate/resolution limits,
latency, frame pacing, visual losslessness, or long-session stability.

The fixture is only 160x96, two pictures, baseline SDR H.264. Software-emulated
AOSP results must be reported as emulator results, never as Android TV hardware
or sender interoperability acceptance. Device execution results are recorded
separately below after running the suite.

## Execution record

**2026-10-11 UTC: passed all 56 assertions on the frozen debug APK.**

- Android API 33, AOSP x86_64 revision 2, model `Android SDK built for x86_64`.
  Official emulator 37.2.12.0, CPU software emulation without KVM, SwiftShader,
  1024 MB guest RAM. Host audio output was disabled; no audibility claim.
- Real decoder: `c2.goldfish.h264.decoder`. Android reports its implementation as
  `hardware`, but this is an emulated codec, not physical-TV hardware evidence.
- All four captured Surface stages produced the expected left red `(255,0,0)`
  and right blue `(1,0,255)` pixels: initial, replacement, re-attached, and fresh
  session. Four detach callbacks and two video-release callbacks were observed.
- The first audio session reported 480 frames successfully written to an actual
  stereo 48 kHz AudioTrack and a 2116-frame output buffer. Two audio-release
  callbacks were observed, including malformed-callback shutdown after restart.
  These numbers describe this generated-media check, not a latency benchmark.
- The known GameStream AndroidKeyStore pairing alias was absent before and after
  the suite. The test read only alias existence and created no pairing identity.
- `GAMESTREAM_RENDERER_VALIDATION_PASSED`, runner exit 0, all JSON assertions
  true, and all four decoded-pixel PNGs were verified. The renderer fixture was
  also checked to be absent from the main APK and present only in androidTest.

Verified artifact hashes:

```text
tv-debug.apk
9cb3032f42bd4ee2eafac3b1ec1874ce1ed01f46ecdf10d8b43858cc4e1d4456
tv-debug-androidTest.apk
37cb40f4fc4a99223c662dd36540c823b0c02572dfdcf2b2dadf59f4d6b8c11c
lib/x86_64/libmoonlight-core.so inside the tested debug APK
3a355d9781c9c5a97155113241a272e5dbe8fbee5a60e4e9ea75ae3ca553f150
```

An earlier booted attempt lost its emulator during instrumentation startup
(emulator exit 137; ADB offline; no assertion result captured). It is recorded as
environment-inconclusive, not as a renderer pass or assertion failure. The
successful run above used the frozen APK and an initialized AVD with lower RAM
while builds were idle. This does not establish the cause of the earlier exit.

No GameStream sender/network session, native Opus decode, physical device,
audible output, or performance acceptance is claimed by this execution.

### Separate optional UI probe

After the renderer suite, the actual `TvActivity` launched successfully on the
same APK. The emulator's active network exposed only CELLULAR transport, so it
was correctly rejected as a receiver LAN. The home page was visible behind
emulator-wide Quickstep/System ANR dialogs. These OS dialogs prevented a reliable
host-picker/D-pad/Back check; that optional UI probe is **inconclusive**, not a UI
pass. No phone selection, pairing confirmation, key creation, or network session
was attempted. This does not invalidate or extend the independent renderer
assertions above.
