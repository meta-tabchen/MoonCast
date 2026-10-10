# MoonCast TV receiver (0.1.0 preview)

This is a **separate Android TV receiver application**, package `com.mooncast.tv`. The existing `app/` MoonCast phone sender is unchanged. Install the TV APK from this branch's GitHub prerelease; do not replace the phone sender APK.

## Start receiving / 开始接收

1. Install the preview APK on Android 8.0+ TV. Both ARM64 and ARMv7 are included (x86_64 is available for testing). This is debug-key-signed preview software, not production signing. CI runs use ephemeral keys; a later preview may require uninstalling the previous TV app, losing its preferences and pairing identity. A stable production upgrade/signing path is not configured.
2. Put your TV and phone on the same **trusted** Wi-Fi/Ethernet LAN. Disable AP/client isolation. Start MoonCast TV and select **Start receiver / 启动接收**.
3. For an iPhone, open **Screen Mirroring / 屏幕镜像**, select the displayed MoonCast TV name, and enter the PIN shown on the TV when prompted. Compatibility is experimental and requires device validation.
4. In a DLNA-capable application, select MoonCast TV as the renderer and play an unprotected supported URL. DLNA is media URL playback, not Android screen mirroring.
5. Press OK or Menu to show TV-remote controls. Choose framing, quality or Stop. Back offers an explicit stop-and-exit. Leaving the app stops listeners, discovery and audio; it does not continue as a hidden background receiver.

## Implemented compatibility matrix

| Protocol / feature | Implementation | Validation / limitations |
| --- | --- | --- |
| AirPlay screen mirroring | Actual UxPlay protocol core, JNI bridge, Android MediaCodec AVC/HEVC decoder, mDNS discovery, PIN prompt | No physical iPhone/TV interoperability claim; depends on iOS, network and device decoder |
| AirPlay audio | Native receiver audio engine, Oboe output | TV remote controls physical volume; sender volume interoperability unverified |
| AirPlay HLS media | Native local proxy with Media3 HLS playback and transport feedback | Clear content only; individual sender applications may restrict output |
| DLNA / UPnP AV | SSDP renderer discovery, AVTransport, RenderingControl, ConnectionManager, GENA notifications | HTTP(S) media supplied by controllers; codecs/container depend on TV; not screen mirroring |
| MP4 / HLS / DASH / audio | AndroidX Media3, device codec support, audio focus | No DRM keys, encrypted-service bypass or universal codec guarantee |
| Google Cast / Chromecast | Not implemented or advertised | Requires a different receiver ecosystem; DLNA cannot substitute for Cast |
| Miracast / Wi-Fi Direct display | Not implemented or advertised | Requires suitable platform support; not a normal LAN socket protocol |
| AirPlay 2 multiroom / DRM | Unsupported | No claim of certified Apple compatibility or DRM playback |
| Moonlight / GameStream client | Not implemented in this TV app | The original MoonCast phone sender still uses the official Moonlight receiver |

Discovery is not proof of playback. Compilation, native-library verification and generated protocol tests are not evidence of physical iPhone/TV interoperability, latency, 4K60, HDR, thermal stability or a security audit.

## Framing and quality

- **Fit** preserves the whole source aspect ratio with letterboxing.
- **Fill** uses uniform scaling to fill the screen, intentionally cropping edges.
- **Smart cinema** samples 160×90 frames twice a second, ignores mostly-dark frames, requires eight stable paired-border observations and then locks the crop. Use Rescan after content changes. This is a heuristic and can mistake dark scenery; return to Fit if needed.
- **Manual crop** trims symmetric left/right and top/bottom edges in 1% steps (up to 25% each), followed by proportional fit. No stretching.
- **Adaptive quality caps** choose available Media3 tracks up to 1080p or 720p. They do not upscale missing detail, change a fixed file's encoding or override an AirPlay sender. Mirroring requests up to 1080p60 and decodes supported source formats. No invented “AI enhancement” claim.

AirPlay and DLNA share one active playback experience. Starting DLNA temporarily stops AirPlay's native listener/audio to avoid two audio sources; stopping media re-enables AirPlay discovery. Network changes stop existing playback and rediscover the new LAN. Audio/video continue only while this Activity is foreground.

## Security and privacy

The app uses no cloud account, analytics or microphone/camera capture. Pairing identity stays in private app storage. AirPlay requests a PIN. DLNA control is not authenticated: any allowed local subnet device may request media or change in-app volume. Never port-forward the receiver. AirPlay's native listeners bind available interfaces, so trusted-LAN use matters; this preview is not a hardened Internet service. DLNA callbacks and incoming controls are restricted to the selected IPv4 subnet and bounded in size/time/concurrency. Media URLs may access public CDN hosts. Servers must provide ordinary valid HTTP framing. Seeking more than 8 MiB into a server that ignores byte ranges is rejected rather than downloading unbounded skipped data. The HTTP transport validates each manifest/segment/key request and redirect, rejects resolved loopback/link-local/infrastructure addresses, and allows the native HLS proxy only on its current explicit port. DNS is re-resolved by the platform at connect time, so this is not a complete DNS-rebinding defense. Protected media and credentials are not extracted or bypassed. Do not enter sensitive media URLs into untrusted controller apps.

## Rebuild and release

```sh
python3 scripts/prepare_airplay_native.py
python3 scripts/prepare_airplay_source.py
python3 scripts/run_tv_unit_tests.py
python3 scripts/run_airplay_unit_tests.py
./gradlew :tv:assembleRelease :tv:lintRelease :tv:testDebugUnitTest
python3 scripts/verify_tv_apk.py tv/build/outputs/apk/release/tv-release.apk
```

See `native/airplay/` for exact upstream commits, runtime/source SHA-256 and retained licenses. AirPlay is based on jqssun/android-airplay-server / UxPlay under GPLv3; Media3 is Apache-2.0. The generated corresponding-source archive accompanies APK and SHA256SUMS in each prerelease. Preview releases are immutable and identified by CI run and commit. Build steps and source fetching must finish before release publication.

## Device acceptance still needed

Install on a TV and check: cold launch / remote focus; PIN pairing; iPhone portrait and landscape video + audible audio; pause/resume/reconnect; DLNA MP4/HLS seek/pause/volume; AirPlay↔DLNA takeover; Wi-Fi/Ethernet change; no audio/listener left after Stop/Back/Home; re-entry; cinema crop dark scenes/subtitles; long playback and frame drops. Record TV model, Android version, sender model/OS, app, codec and content type. No personal capture is required.
