# Browser preview validation

This record describes local Mac validation on 2026-10-12. The final code-54 APK
was installed on a Xiaomi Pad 8 (Android 17), then its real screen capture and
permitted playback capture were received in Mac Chrome. The source content was a
dedicated, permission-free synthetic color-bar/moving-dot/440 Hz test application.
The user authorized this development task to operate ADB after the separate QA
task relinquished the device. No ADB server reset, permission override, user-data
clear, certificate bypass or personal-content recording was used.

## Real Android to Mac Chrome

APK `0.6.0-browser-preview.3`, versionCode 54, build `7a843a1`, SHA-256
`04792d9fccd097461f659e2b9f8bfc2499eeb293724b546977ab2685e02bb825`,
keeps the fixed community package and signer. Its normal MediaProjection consent
was accepted on the device. The actual production Android HTTP/signaling server
and WebRTC stack delivered H.264 video at 1280×854 and Opus audio at 48 kHz to
Chrome 154.0.8037.99 on macOS. No SDP/candidate rewriting or browser-generated
sender was used for this test. Decoded frames, received audio bytes/samples and
nonzero audio energy rose over successive observations after the sound gesture.
Physical speaker listening and audio/video synchronization were not measured.

The live picture continued during Fit/Fill controls. A short recording of the
actual receiver page shows the labelled synthetic Android content. Its GIF/WebM
contains no recorded audio track; captured-audio evidence is in receiver RTP
statistics. The initial recording attempt missed its content-review deadline;
that failed evidence was retained, and a fresh run completed the recording.
`tests/browser_android_receiver.cjs` records these conditions and explicit
recorded/skipped status instead of inferring success from a generic phase marker.

Normal Android Stop ended MediaProjection, removed the BrowserHostService, closed
the browser peer/tracks and stopped serving the old session URL. The temporary
test application was then uninstalled; MoonCast and user data were retained.
Published UI screenshots are from code 54 and contain no session URL. Phone
sharing screenshots containing private addresses/tokens were retained only for
internal review. The About screenshot confirms the installed version/build.

This is a real Android capture-to-browser result using synthetic source content.
It does not validate every Android device, protected source app, TV browser,
network configuration, rotation, single-app capture, latency or lossless quality.

![Real Android capture received in Mac Chrome; synthetic source content](assets/browser-android-demo.gif)

![Installed code-54 Android browser page](assets/browser-android-ui-code54.png)

![Installed version and build identity](assets/browser-android-about-code54.png)

The Android test decoded 5→84 H.264 frames over three seconds. After sound was
enabled, decoded samples rose 160,320→257,280 and total audio energy rose
0.000325059→0.013733485 over two seconds. The full report and capture assets are
delivered as the code-54 real-device evidence bundle. A separate earlier
browser-only fixture is documented below and must not be used as Android evidence.

## Browser media and UI

The production receiver assets were served on an ordinary private-LAN HTTP origin
and exercised in Chrome 154.0.8037.99 on Mac. The page reported
`isSecureContext=false`, `RTCPeerConnection` available and `getUserMedia` unavailable.
No certificate bypass, camera permission, microphone permission, autoplay override,
disabled mDNS policy, or disabled browser sandbox was used.

The test sender produces a labelled canvas and a 440 Hz oscillator through an
actual WebRTC peer connection. A default two-Chrome mDNS attempt did not establish
ICE in this local setup. The passing fixture replaces only its own synthetic
sender's answer candidate address with its known private interface IPv4, preserving
the candidate port. This models a native sender's numeric host candidate; the
production receiver offer and browser policy remain unchanged. It does **not**
prove that the Android WebRTC implementation negotiates or captures correctly.

All 12 checks in `tests/browser_receiver_smoke.cjs` passed:

- Real ICE/DTLS-SRTP video and audio; manual connect and explicit sound gesture.
- Lost first answer recovery, another viewer's busy response, and same-tab reload.
- Audio capability downgrade, reconnect after sender loss, six-attempt retry limit,
  and explicit disconnect cleanup.
- Fit/Fill, zoom, drag pan, reset, fullscreen entry/exit, English/Chinese, and
  layouts at widths 375, 768 and 1440.
- A recording of the actual receiver page, with a visible synthetic-source label.

Decoded video advanced from 31 to 43 frames at 640×360. The selected ICE pair was
nominated and succeeded. After the sound gesture, the playing video element was
unmuted and inbound audio contained 70,080 samples with nonzero total energy
(`0.00028946087678760314`). Physical speaker audibility was not measured. The test
recorded no CSP violations or unhandled browser errors.

![Actual browser receiver with synthetic WebRTC media](assets/browser-synthetic-demo.gif)

The 5.1-second GIF is an actual webpage capture with synthetic media, not a mockup
or Android capture. Its source WebM has no audio track. See
[`tests/browser-receiver-README.md`](../tests/browser-receiver-README.md) for the
reproducible command and fixture limitations. The evidence bundle includes the
machine-readable report, asset SHA-256 values, screenshots and recording.

## Native and build checks

The first ARM64 community Release build and Android lint passed. Lint reported zero
errors and 11 warnings; the TrustAllX509TrustManager warning is in the existing
Bouncy Castle dependency, not browser signaling. The final handoff records the
final APK hash and checks after all UI changes.

The pinned WebRTC AAR resolved and compiled against Android API 35. The release APK
verifier checks all 11 native payloads, ELF dependencies, packaged webpage bytes,
license notices and WebRTC classes. The community identity verifier checks the
unchanged package and certificate and versionCode 54 above the published 51. An
upgrade-compatible static result is not a device installation result.

The initial code-52 release lacked the JNI Zero `JniInit` class despite a literal
native SDK reference and no AAR consumer shrinker rules. Code 53 explicitly keeps
JNI Zero classes and the APK verifier now requires `JniInit`. This is a necessary
native initialization safeguard; device behavior is recorded separately by QA.

The portable Java suite covers signaling bounds, local HTTP policy, session state
and three-mode transitions. Separate dialog callback tests exercise cancel/back,
reselecting the active role, and confirmed role changes for both phone and TV.
The audio callback has been locally checked for a bounded 10 ms cadence and
silence filling, and allowed test-app AudioPlaybackCapture produced nonzero
decoded browser audio in the real-device test above.

## Remaining device acceptance

The earlier QA task verified phone/TV role-menu cancel and current-role selection
on code 52. Code 54's reference-style tab and installed About version were reviewed
on the actual tablet. Remaining acceptance should include wider phone/TV layouts
and the cases below; a passing Mac/tablet test is not universal compatibility.

Check screen and single-app capture, portrait/landscape rotation, hardware codec
selection, actual audible allowed playback, protected-content behavior, Stop from
both app and notification, process/peer loss, reload, network changes, and repeated
switches to the existing sender protocols. Also check denied audio permission and
Android versions below 10 with video-only behavior. Record real-device observations
separately. TV browsers, other desktop browsers, iPhone mirroring, throughput and
latency claims require additional evidence.
