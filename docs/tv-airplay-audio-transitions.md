# AirPlay audio transition candidate (0.4.5 / code 9)

The reported symptom is a short silent start after each video switch, followed by
normal sound. No Hongguo/iPhone/physical-TV packet trace was available. The fixes
below address reproducible receiver defects, not a confirmed diagnosis of that
specific application's stream.

## Reviewed native changes

The pinned RAOP queue discarded known no-audio packets before recording their
RTP sequence numbers. The next real audio packet then appeared to follow an
unrecoverable loss. With a 256-packet window, a synthetic trace of one consumed
packet, one empty marker and the next audio packet waited 256 arrivals to resume.
At 480 samples per packet and 44.1 kHz, 255 intervals are about 2.78 seconds.
This is conditional timing arithmetic, not a measured device latency.

The candidate records these existing no-audio classifications as occupied,
zero-payload slots and consumes them without calling the decoder. Both ordinary
and retransmitted packets use the same classification. A genuine missing packet
still waits for retransmission; malformed/lookalike payloads do not become new
silence markers. The bounded scan never exceeds the existing 256-slot window.

The RTSP FLUSH path also parsed a sequence boundary but failed to apply it to
its RTP queue. It now calls the queue's existing inclusive-boundary reset API on
the RTP thread. No NTP mapping, decoded PCM queue, audio-output stream, sample
rate, resampling, buffer target, video timing or volume is changed by these patches.
No-data handling does not shrink the packet-loss recovery window.

The exact patches are in `native/airplay/patches/`; the original upstream source
archive remains unchanged. A distributable candidate must contain freshly built
patched runtimes for all three supported ABIs, matching the updated manifest and
rebuild recipe. Merely adding the patches to source does not fix the old binary.

## Evidence and limits

`scripts/run_airplay_rtp_transition_tests.py` extracts the actual pinned queue
functions and FLUSH dispatch, applies the overlays in a temporary directory and
executes them with deterministic collaborators. Crypto is a plaintext test stub.
It reproduces old-code stalls, then covers 1,000 transitions, header-only/empty/
ALAC-format-only packets, genuine loss and resend requests, duplicates, late
packets, sequence wrap, malformed/lookalike payloads, startup, repeated and
no-sequence FLUSH, and unchanged real-payload bytes.

ASan and UBSan pass. LeakSanitizer is disabled explicitly because this executor's
ptrace environment does not support it. 44.1/48 kHz checks concern rate-independent
packet sequencing and timing arithmetic, not hardware codec sample-rate switching.
Actual encoded playback, lip-sync, HDMI/TV audio output, Hongguo behavior and
subjective absence of glitches require physical acceptance testing.

## Installation identity

The previous build environment and its private test signer were unavailable.
The user authorized this one experimental APK under a distinct package:
`com.mooncast.tv.audiofix.preview`. Its desktop name is simply **MoonCast**;
About identifies the version and package. This candidate cannot update the old
signer's application. Keep the old app installed, but stop its receiver service
before running this one so their listening ports do not conflict. Signing is a
one-time experimental identity, not a newly provisioned production credential.

## Rebuild and binary provenance

All three ABIs were rebuilt from the included, checksum-pinned archives with
NDK 27.0.12077973, CMake 3.22.1 and Ninja. The six archived upstream patches and
the two MoonCast patches are applied once, in that order. No source is downloaded.
Run:
    python3 scripts/build_airplay_patched.py --source . --work /new/empty/directory
The recipe needs Python 3.12+, git, make, Perl, CMake/Ninja and ANDROID_NDK_HOME.
Copy the four output libraries per ABI to tv/src/main/jniLibs only after checking
their hashes and JNI exports. Build records retain both upstream and patched
source hashes. The manifest preserves original APK provenance under upstreamLibraries;
its libraries map now identifies the rebuilt binaries. prepare_airplay_native.py
verifies the patched binaries and refuses to replace them with the original APK.

Build the independently installable TV candidate with Gradle 8.9 / JDK 17:
    gradle :tv:assembleAudiofix :tv:lintAudiofix -PmooncastTvAbi=armeabi-v7a
SDK 35 and Build Tools 35.0.0 are required. The output is unsigned; supply your own
authorized signing identity. No private signing material is included.

A second clean-workdir rebuild reproduced the patched AirPlay JNI binary exactly.
Oboe differed only in its GNU build-id; OpenSSL records its build timestamp.
Therefore the recipe promises a reproducible build procedure and auditable source,
not byte-identical output for every dependency across build locations and times.

## Candidate validation

The ARMv7 APK passed Gradle assembleAudiofix and lintAudiofix; Android test sources
compiled successfully (not executed on a device). Static APK checks validated
package/version, non-debuggable status, all five bundled native libraries,
22 AirPlay JNI exports and 32 GameStream JNI exports, pinned source inputs,
zip alignment and APK signature. PCM offset tests passed 445,713 assertions;
APK permission-settings return lifecycle regression passed. AirPlay decoder,
session/watchdog, framing, audio-volume restoration and core unit suites passed.
The native transition suite passed ASan/UBSan. No actual iPhone/TV session was run.
