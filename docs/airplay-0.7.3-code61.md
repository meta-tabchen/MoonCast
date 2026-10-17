# Local AirPlay follow-up: 0.7.3 / code61

This is a local candidate, with no upload, release, or messaging action. It keeps
the already requested `0.7.3` versionName, increases versionCode from 60 to 61,
and preserves the community package/signing identity. The native build's `074`
temporary staging path and evidence directory are internal labels, not versionName.

The user reported that code60 produced picture on the TV, had no sound, exited
after roughly ten-plus seconds twice, and sometimes showed a very small image
until reconnecting. These are user observations. No agent independently tested
either candidate on this TV. The fixed defects below do not establish the sole
cause of the TV's timed exit.

## Confirmed defects and changes

* The successful HTTP-7100 branch returned before creating an audio session or
  PCM pipe. `HasAudio` was false, so Android never started playback capture even
  when the sound option was selected. Compatibility quality also disabled that
  option. Code61 independently opts into sound, discovers `_raop._tcp` only on
  the selected IP, authenticates and negotiates ANNOUNCE/SETUP/RECORD and volume,
  and connects Android-permitted 44.1 kHz stereo PCM to ALAC and RTP. Digest nonce
  counts start at one independently of CSeq and retain their client nonce. Audio waits
  for the first actual video packet. Capture permissions and source-app capture
  restrictions remain enforced; there is no microphone or privileged fallback.
* Legacy codec and video headers omitted their source/display rectangle fields.
  Code61 derives actual encoded dimensions from SPS, fits the display rectangle
  into the receiver dimensions, writes its centered position, and updates these
  fields in both codec and media packets. Changed config waits for its matching
  IDR. The Java normalizer merges separate SPS/PPS callbacks without discarding
  the other set or accumulating stale duplicates. Reconnection has fresh state.
* Android rescaled the native-selected canvas a second time. It now uses the
  exact negotiated canvas. Whole-screen capture follows physical display metrics;
  selected-app capture follows content-resize callbacks. AirPlay preserves the
  initial capture density when resizing. A static surface requests encoder frame
  repetition, while the output canvas stays fixed through phone rotation.
* Heartbeats formerly carried a changing media timestamp. Their 128 bytes are
  now zero except type=2 and flags=30, with zero payload, every second. A single
  mutex covers each complete header and payload, including concurrent video.
  TCP writes have a three-second deadline; direct net.Conn writes have no
  userspace output buffer to flush. HTTP-stream reading has no response timeout:
  receivers that upgrade without sending HTTP ACKs remain supported. EOF and
  write/heartbeat errors close resources, clear native connection state first,
  and produce one epoch-guarded disconnect callback. Android clears `connected`
  before publishing failure/stop, and checks transport state every 500 ms.
* The shared UDP socket handles the distinct 48-byte HTTP-mirror NTP and
  32-byte RAOP timing formats, restricted to the selected IP. Video timestamps,
  audio synchronization and timing replies use the same session-relative clock.
  An audio-only failure ends its pipe, allowing Android to restore handset volume
  while the existing video transport continues.

Wire facts were checked against the
[public protocol description](https://nto.github.io/AirPlay.html). A separate
reference-app investigation supplied behavioral comparisons; no proprietary
implementation was copied. The known-working video handshake remains in place.

## Evidence and scope

Evidence is retained in `../evidence-0.7.4/` beside this checkout.

| Check | Result |
| --- | --- |
| Real-time mirror soak | PASS: 180 seconds, 3,564 video packets, 179 heartbeats, 18 configuration updates across 128x72, 640x360 and 180x320 fixtures |
| Clocks during soak | PASS: continuous selected-peer NTP48 and RAOP32 response checks; media timestamps monotonic |
| Receiver EOF and reconnect | PASS: receive-side close detected; separate reconnect sent fresh config and 25 frames |
| Independent H.264 decoding | PASS: ffprobe strict decoding produced 3,589 total frames with zero codec-error output |
| PCM to audio transport | PASS: 614 RAOP ALAC/RTP frames (final authentication run) decoded sample-for-sample to supplied PCM; authenticated setup; no audio before video; receiver closure ended audio |
| Native lifecycle | PASS: failure clears connection/audio/dimensions, unblocks PCM, coalesces duplicate errors and rejects obsolete-worker failures |
| Final race batches | PASS: full wrapper suite plus selected legacy, authentication, optional-info, negotiated SETUP-wire and ALAC regressions |
| Java H.264 ownership checks | PASS: 17 checks, including split parameter-set updates and fresh-session state |
| Failure privacy/retention | PASS: 24 checks |
| Localization | PASS: 803 bilingual entries/contracts |
| Full host test script | FAIL at unchanged OriginalMediaServerTest POST handling: macOS Connection reset; retained, no unrelated media-server changes |
| Upstream modern SETUP fixture | FAIL both on unmodified code60 prepared source and code61: fixture expects pre-transformation request ordering; repository negotiated-SETUP wire tests PASS |
| Physical TV / Android UI | NOT independently tested; no UI/device acceptance claim |

The first ffmpeg null-output attempt inferred conflicting timestamps from the
three raw fixture streams and reported muxer DTS errors. Raw H.264 does not
retain the network timestamps. Those logs remain; strict ffprobe decoding is the
codec check, and the wire soak independently checks actual transport timestamps.

Audio scope is advertised ALAC (`cn=1`) with explicit unencrypted transport
(`et=0`) or authenticated FairPlay (`et=4`). RSA-only, AAC-only, missing RAOP
advertisements, authentication refusal and source-app capture restrictions can
still leave video-only operation. These are reported as an unavailable audio
session rather than a claim that the TV is old or cannot play sound. TCP liveness
does not prove that a receiver has displayed or played every packet.

The AAR manifest pins all runtime patches, module inputs, three JNI libraries,
rebuilt Java bindings, toolchain and source-lock hash. This local checkout has
uncommitted changes; the historical local base commit is not the exact final tree.
