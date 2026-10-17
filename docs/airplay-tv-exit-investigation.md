# TV exit investigation, after 0.7.4/code62

The user confirmed that code62 fixes the picture ratio. The remaining priority is
that the TV leaves the mirror while the phone still reports an active connection.
No root cause or TV compatibility pass has been established.

用户已确认 code62 的比例正常。当前优先问题是电视退出投屏，手机仍显示连接。
尚未确认根因，也没有电视兼容性通过结论。

## Evidence and boundaries

- User diagnostic at elapsed 57,808 ms: 1,229 video packets, one configuration,
  57 heartbeats, last write at 57,774 ms, PTS age 34 ms, no reported transport
  failure, NTP48/32 counters zero. Successful writes do not prove rendering.
- Actual selected encoder: `c2.qti.avc.encoder`, 1280×720, requested 30 FPS and
  4 Mbps. The request is Baseline/Level 4, one-second I-frame interval, no
  B-frames. Actual SPS, actual slice types and keyframe intervals were unknown.
- Audio failed at DISCOVERY before ANNOUNCE/SETUP/RECORD. This explains the lack
  of captured audio, but is not proof of the video-exit cause. Audio changes are
  paused to isolate the video/session problem.
- The only locally authorized ADB phone was SDK37 with MoonCast code55, while
  the user's failing report was SDK35/code62. It was not treated as the target
  and no APK was installed there.
- The user subsequently confirmed it returns to the TV system launcher. This
  supports an ended receiver activity/session but does not prove a process
  crash. Receiver identity and precise exit interval are still unknown.

## Audited path

`AirPlayVideoEncoder` copies the MediaCodec output range before releasing the
buffer; it retains `presentationTimeUs`. It treats each output buffer as one
access unit and submits that buffer's final NAL in the same JNI invocation.
This assumes complete encoder output: `BUFFER_FLAG_PARTIAL_FRAME` is not
assembled; code63 now reports its count and last buffer flags/offset/size.
Actual target output format remains unobserved. `AirPlayH264` normalizes AVCC lengths
or Annex B and merges separate SPS/PPS callbacks. A replacement Java encoder has
fresh parameter-set state. The service generation guard rejects stale callbacks.

The legacy Go path appends a four-byte big-endian length for each supported NAL,
including multiple slices. Configuration replacement waits for an actual IDR,
then sends the configuration before video. A single mutex covers header and
payload and excludes heartbeat writes; partial writes are completed. The reader
reports EOF/reset/HTTP rejection. Native `connected` tracks transport, not a
receiver rendering acknowledgement.

A specific framing defect was subsequently reproduced against the exact code62
normalizer source SHA256: an avcC-declared stream with first NAL length 256–511
was mistaken for an Annex B start code. Code63 now honors the avcC framing
declaration and validates each length. Without such a declaration, it preserves
the previous Annex B behavior and records ambiguity rather than guessing.
A fresh output-format callback clears the old framing declaration; a standalone
PPS callback does not erase it. The 256 length cases and a valid 300-byte SEI
followed by a real twenty-frame clip pass, including independent strict decoding.
This is a proved parser defect, not a proved cause of the target TV exit.
Unsupported/partial encoder outputs, actual target SPS and receiver behavior
remain runtime questions.

## Falsifiable hypotheses

| Hypothesis | Evidence that would support it | Evidence against it / next isolated test |
|---|---|---|
| Later IDR, NAL lengths or configuration switch breaks decoding | NAL-length error, key flag/IDR mismatch, generation change or missing IDR immediately before exit | Strict complete-payload lengths, stable configuration, expected IDRs across the exit reduce this hypothesis |
| Actual H.264 limits exceed the receiver | SPS reports a different profile/level/refs, B slices, or an unexpected change | After reading actual SPS, vary one supported encoder setting only; do not change framing or several codec settings together |
| PTS discontinuity or sender delay | Backward/repeated PTS, large gap or increasing age near exit | Stable PTS and bounded write/age across exit reduce this hypothesis; no session-relative timestamp experiment without evidence |
| Receiver closes or network stalls | EOF/reset, state change, stalled ACK counter, queued unacked data/retransmission growth | ACK progress with stable media counters after visible exit points toward receiver decode/render/session handling, not sender transport success |
| Timing/control compatibility | Missing timing correlated with exit and a same-phone working reference showing the required exchange | Zero NTP packets alone is inconclusive; compare the full connection with working Mirroring360 before adding control channels |

## Framing correction and diagnostic candidate

Patch `0006-bounded-media-and-tcp-observation.patch` observes existing packet
construction. It changes no encoder parameters, framing geometry, audio,
heartbeat schedule, timestamps, reconnect policy, encryption or packet bytes.

Java reports selected Annex B/AVCC counts, ambiguous input, avcC declaration,
resolved ambiguity, partial-frame count and last buffer flags/offset/size.
The structural report contains actual SPS profile/constraints/level/ref count;
configuration generation; IDR count/age/max interval; B-slice and key-flag
mismatches; complete AVCC length validation; repeated/backward PTS and maximum
gap/age/write time. Thirty four-second media buckets retain approximately the
last two minutes. There are no screen bytes or raw SPS/PPS in the report.

On Android/Linux, optional read-only `TCP_INFO` adds state, unacked segments,
total retransmits, RTT and acknowledged byte counters to thirty four-second
samples. Unsupported platforms/kernels are explicitly unknown; an absent
extended counter must not be interpreted as zero ACKs. TCP ACKs still do not
prove playback. This adds no network request, packet capture or permission.

The existing “Copy casting diagnostics” action and private report persistence
are reused. At the visible TV exit, copy diagnostics promptly and record the
approximate elapsed time and what appeared on the TV. Do not stop the sender
first if it still says connected. This single report is the smallest useful
next target observation; a packet trace is optional escalation if counters and
metadata cannot distinguish the hypotheses.

## Source research

- [Legacy receiver packet parser](https://github.com/espes/Slave-in-the-Magic-Mirror/blob/master/airplay.py#L54-L99): 128-byte headers, NTP32.32 timestamps and strict per-NAL lengths.
- [RPiPlay mirror receiver](https://github.com/FD-/RPiPlay/blob/master/lib/raop_rtp_mirror.c#L242-L340): complete payload reads and repeated length-prefixed NALs. This is a reference implementation, not the unknown target TV.
- [Experimental iOS5/6 UxPlay branch](https://github.com/iskamag/UxPlay-legacy/blob/legacy/IOS6.md): legacy 7100 and NTP48 clock support. Experimental branch behavior is not a universal TV requirement.
- [Android MediaFormat](https://developer.android.com/reference/android/media/MediaFormat#KEY_LEVEL): requested level is a hint; examine actual SPS. Requested configuration is not actual encoded-stream evidence.

No primary evidence established a universal 15/30/60-second old-TV timer.
The reference sender's 15-second watchdog and ten-second I-frame setting are
not a proven receiver exit rule. Photo/URL-video `/reverse` and modern RTSP
`/feedback` must not be injected into the upgraded legacy binary media stream.

## Acceptance limits

Byte-identical fixture output, strict independent parsing, decoder checks,
longer host soaks, race tests and APK integrity checks establish bounded host
properties only. They cannot establish this TV's persistent playback. Preserve
each failed or skipped subcheck and keep code62 as the ratio-working baseline.
The final 0.7.5/code63 contains the avcC framing correction plus diagnostics,
not a declared fix for this TV exit. The native code passed a 600-second host
soak plus reconnect, 12014 decoded frames, 60 configuration changes and NTP48/32.
The old 600-second invocation hit Go’s default ten-minute timeout; its failed
log is retained separately from the successful explicit-fifteen-minute rerun.
The full Java runner still fails the pre-existing OriginalMediaServerTest with
Connection reset; its remaining six groups pass. This is not an all-tests-pass
claim.

Delivery is to the Mac desktop only, with the existing package and signer.
No GitHub, Library or Feishu publication is authorized.
