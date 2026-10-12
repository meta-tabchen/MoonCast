# TV cinema visual preview 0.4.3

This is a local co-installable preview (`com.mooncast.tv.preview`), versionCode 7.
It retains the existing local test signing identity; it is not a production release.

## Standby and active playback

- Standby uses a single 1672 × 941 lunar-lake bitmap, a restrained scrim, larger typography,
  consistent icon strokes and overscan-safe controls. The original background was generated
  specifically for this preview on 2026-10-11 with the built-in image-generation tool;
  it includes no third-party logos or photographed people. The prompt requested a premium
  cinematic Android TV standby background, no text/icons/UI, a blue-gray cratered moon
  upper-right over a still dark mountain lake, black navy sky, low silhouettes, subdued cobalt
  reflections, left dark negative space and no device frame. No runtime image generation or
  background network access is used.
  The asset is included with this project's source, under the project license.
- While media, AirPlay mirroring or GameStream owns playback, Back/Menu opens a translucent
  floating control panel above the existing TextureViews. No standby bitmap is drawn in this
  state. No live blur or video-frame capture is used for the background.
- Return to playback receives focus. Back dismisses the overlay without ending the session.
  End cast is an explicit, separate action, using the established stop/cleanup path.
- Framing and Settings remain available; opening them does not change renderer ownership.
  Applying an existing setting that explicitly requires a receiver/player restart retains its
  established behavior. Crop editing remains the existing live editor.
- Receiver protocols, native libraries, audio and input plumbing, and the APK receive workflow
  (port 48152, no code/binding, APK review and system installer confirmation) are unchanged.

## Verification

`python scripts/test_tv_cinema_contract.py` checks source guardrails for transparent rendering,
standby exclusion during playback, surface-independent show/hide, distinct stop and remote focus.
It is a static regression check, not decoder or audio evidence.

`TvUiTestDriver` additionally checks rendered overlay alpha and remote focus/Back behavior.
With an actual decoded fixture it verifies that the exact player, TextureView and SurfaceTexture
remain in place and that playback time continues to advance while the overlay is visible.
Offline mirror/GameStream navigation uses clearly labeled simulated ownership and is not
AirPlay/GameStream interoperability evidence. These device checks require an Android runtime.

Physical-TV remote behavior, real AirPlay/GameStream sender sessions and audible sound continuity
must be checked on hardware; successful compilation and static tests do not establish those.

### Local build record (2026-10-11)

- Main and instrumentation Java compilation passed.
- ARMv7 coexist assembly and lint passed: 0 errors, 41 warnings.
- Five pinned native libraries, source/JNI provenance, package/version metadata, APK signature
  and ZIP alignment checks passed; the native payload was not modified.
- TV protocol, geometry, framing and HTTP portable suites passed. This local JDK image lacks
  `--release 17` symbols, so these runs used Java 17 source/target on JDK 21. The committed
  test runner remains unchanged; this is not an additional JDK 17 runtime result.
- Cinema source-contract checks and whitespace checks passed.
- Device instrumentation is provided and must be reported separately after it runs.
