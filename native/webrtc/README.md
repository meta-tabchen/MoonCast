# Browser sender WebRTC dependency

MoonCast uses the unmodified Maven Central artifact
`io.github.webrtc-sdk:android:144.7559.15`. It contains the open-source native
WebRTC stack and Java API; no LiveKit account, hosted service, proprietary SDK,
STUN server or TURN service is required by this integration.

`SOURCE_LOCK.json` pins the AAR URL, SHA-256, each native ABI library, source
commit, build-wrapper release tag and the attribution files packaged in the APK.
The AAR itself has no license assets, so MoonCast supplies the upstream notices
under `app/src/main/assets/browser-webrtc-*`.

## Source and licenses

- WebRTC fork source: [webrtc-sdk/webrtc at 30d5e63ae91da483e06577b5c35ee91cc5e5c3db](https://github.com/webrtc-sdk/webrtc/tree/30d5e63ae91da483e06577b5c35ee91cc5e5c3db).
- The [wrapper v144.7559.15 release](https://github.com/webrtc-sdk/android/releases/tag/v144.7559.15)
  identifies that source revision. The Maven POM declares BSD 3-Clause.
- WebRTC's BSD license and PATENTS grant are preserved, along with the fork's
  Apache 2.0 patch NOTICE. The packaging repository has a separate MIT license;
  this does not replace the native dependency's licenses.
- The 788,358-byte [upstream third-party notice collection](https://github.com/webrtc-sdk/android/blob/v144.7559.15/Licenses/WEBRTC.md)
  is retained unchanged. Its 30 component headings include build tools as well
  as codec and cryptography components; it is not a verified list of the exact
  components linked into this AAR.
- Additional exact license files are included for the pinned [DEPS](https://github.com/webrtc-sdk/webrtc/blob/30d5e63ae91da483e06577b5c35ee91cc5e5c3db/DEPS)
  revisions of BoringSSL (`b94d71f87ff943a617d77f3ff029f9a01a1ec6bc`)
  and JNI Zero (within Chromium third_party
  `a3b630c291f1cf3b711687b1c4be322d3015512e`). Their source URLs and
  hashes are in `SOURCE_LOCK.json` under `licenses` and `dependency_pins`.
  The BoringSSL revision's license differs from the older composite text in
  the upstream collection; JNI Zero's BSD copyright is absent from that
  collection. The original collection is preserved, with these files supplied
  as an addendum. This addresses the identified notice gaps, not a claim that
  all component coverage has been audited.

The actual Maven POM declares no transitive Maven dependencies. This does not
describe the dependencies embedded in the native library. No complete linked
SBOM, exact upstream build run or generated GN dependency graph has yet been
verified for the published artifact. These remain release-audit requirements.

No SDK source or native bytes were changed. MoonCast's Java integration calls
public APIs: `SurfaceTextureHelper`, `VideoSource`, hardware video encoders,
`PeerConnection`, and the fork's recordingless audio-device callback.

## Obtain and rebuild the native source

Android native WebRTC builds are supported upstream on Linux. Use the pinned
[Android build guide](https://github.com/webrtc-sdk/webrtc/blob/30d5e63ae91da483e06577b5c35ee91cc5e5c3db/docs/native-code/android/README.md)
and depot_tools prerequisites. In a **separate, empty** working directory, obtain
`webrtc_android` with `fetch --nohooks webrtc_android`; configure the checkout's
source URL to `https://github.com/webrtc-sdk/webrtc.git`, fetch the source commit
above, check it out, then run `gclient sync` to acquire its pinned DEPS. Keep
the resulting WebRTC/Chromium dependency sources and their license files with
any corresponding-source distribution that needs them.

The pinned source contains
[`tools_webrtc/android/build_aar.py`](https://github.com/webrtc-sdk/webrtc/blob/30d5e63ae91da483e06577b5c35ee91cc5e5c3db/tools_webrtc/android/build_aar.py).
The upstream [Android build recipe](https://github.com/webrtc-sdk/webrtc-build/blob/main/docs/build.md#android)
uses this entry point, for example from `src/`:

```sh
vpython3 tools_webrtc/android/build_aar.py \
  --build-dir out/mooncast-aar --output out/mooncast-libwebrtc.aar \
  --arch armeabi-v7a arm64-v8a x86_64 x86 \
  --extra-gn-args 'is_java_debug=false rtc_include_tests=false rtc_use_h264=false is_component_build=false use_rtti=true rtc_build_examples=false treat_warnings_as_errors=false'
```

`rtc_use_h264=false` disables the optional native software H.264 path; Android
MediaCodec hardware H.264 remains available through the Java encoder factory.
The wrapper's pinned
[artifact-fetch workflow](https://github.com/webrtc-sdk/android/blob/v144.7559.15/.github/workflows/fetch-webrtc-artifacts.yml)
describes how upstream build artifacts reach its release. MoonCast has verified
the published artifact hash and Java API, but has **not** rebuilt this SDK or
established byte-for-byte reproducibility of that command. The full, multi-GB
WebRTC/Chromium source checkout is not vendored in this repository. A MoonCast
source archive alone must not be described as a self-contained WebRTC native
source distribution.

GPLv3 corresponding-source delivery remains subject to release review. Section
6(d) permits equivalent source access on another server with clear directions
next to the binary and an obligation to maintain availability; it does not
require one offline archive. A fixed source URL alone does not establish the
complete corresponding source required by section 1. The necessary dependency
sources, build configuration, modifications and scripts, and the applicable
system-library/tool exceptions must be checked for this artifact. Building
MoonCast with a cached AAR does not demonstrate rebuilding WebRTC from source.

## Integration boundaries

`BrowserRtcSession` preserves the service's one MediaProjection grant across
viewer reconnects. `VideoPipeline` renders into a WebRTC texture Surface; native
WebRTC handles negotiated encoding, ICE, DTLS-SRTP, RTCP and Opus. The browser
receives a send-only Android stream. Local ICE candidates are used without
configured public services, and cellular interfaces are excluded.

`BrowserPlaybackAudio` uses Android 10+ AudioPlaybackCapture for permitted
MEDIA/GAME/UNKNOWN usages in the same user profile. The SDK's default microphone
recorder is disabled before creating a peer. A bounded PCM queue and explicitly
paced 10 ms callback prevent unbounded backlog and recordingless ADM busy loops.
Unavailable or protected playback remains silent/video-only; no permission or
capture-policy bypass is attempted.

The HTTP page and same-origin signaling are intended for a trusted local network.
Receiving WebRTC does not require browser camera/microphone capture. Browser
autoplay, local-network policy and device/network reachability still need real
device testing. HTTP signaling is not authenticated TLS, even though WebRTC
media uses DTLS-SRTP. No manual certificate generation or certificate-warning
bypass is part of this mode.
