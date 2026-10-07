<p align="center">
  <img src="docs/assets/banner.svg" alt="MoonCast — Your Android phone, on a bigger screen" width="920">
</p>

<p align="center">
  <a href="https://github.com/meta-tabchen/MoonCast/actions/workflows/android.yml"><img src="https://github.com/meta-tabchen/MoonCast/actions/workflows/android.yml/badge.svg" alt="Android CI"></a>
  <a href="https://github.com/meta-tabchen/MoonCast/releases/tag/v0.2.2"><img src="https://img.shields.io/badge/release-0.2.2%20preview-blue" alt="0.2.2 preview"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 and later">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPLv3-blue" alt="GPLv3"></a>
</p>

<p align="center"><b>English</b> · <a href="README.zh-CN.md">简体中文</a></p>
<p align="center"><a href="https://github.com/meta-tabchen/MoonCast/releases/tag/v0.2.2">Download APK</a> · <a href="docs/getting-started.md">Getting started</a> · <a href="docs/comparison.md">Compare alternatives</a> · <a href="CONTRIBUTING.md">Contribute</a></p>

# MoonCast

**Turn your Android phone into a streaming host for Moonlight.** Mirror your phone to an iPad, Android TV, or another Moonlight client over your local network, with no PC in the middle.

MoonCast focuses on phone-to-screen casting: keep the whole picture, crop a centered video region, forward playback audio, and optionally control the phone from the receiver. Screen sharing works without root or ADB. Remote input is a separate, opt-in feature.

**Status: early preview.** Basic Android → iPad streaming has been tried on one real phone. Version 0.2.0 received positive informal feedback; cropping, audio muting, and remote input still need broader device validation. The primary interface follows your system language: English by default, Chinese on Chinese systems. English/Chinese documentation is included. Lower-level diagnostics are not fully translated yet.

## Why try it?

- **Use the Moonlight you already have.** No custom receiver app; PIN pairing and hardware decoding use the existing Moonlight ecosystem.
- **Cast the video region.** A fixed, centered 16:9 preset avoids manual selection; full-screen, automatic border removal, and fill modes remain available.
- **Choose where sound plays.** Forward eligible Android playback audio and optionally mute the phone, with volume restoration when the session ends.
- **Choose how input works.** Watch only, use Accessibility for single-finger taps/swipes, or try Root input injection for continuous touch and basic keyboard input.
- **Run locally.** No MoonCast account, advertising, or cloud service is required at runtime.

The protocol core comes from [Mirror](https://github.com/jqssun/android-display-mirror)'s Android adaptation of [Sunshine](https://github.com/LizardByte/Sunshine). MoonCast builds a focused Android app around that work. It is an independent project, not an official Moonlight or Sunshine release. See [credits and provenance](THIRD_PARTY_NOTICES.md).

## Get a picture in five steps

1. Install the [0.2.2 preview APK](https://github.com/meta-tabchen/MoonCast/releases/tag/v0.2.2) on an Android 8.0+ phone. It is a **debug-key-signed preview**, not a production-signed release.
2. Install [official Moonlight](https://moonlight-stream.org/#) on the receiving device. Put both devices on the same trusted LAN.
3. Tap **Start casting** (Chinese: **启动投屏**) on the phone and approve sharing the **entire screen**.
4. In Moonlight, add the phone's LAN IP if discovery does not find it. Enter Moonlight's four-digit PIN in MoonCast.
5. Open **Desktop** in Moonlight, then switch the phone to your video or app.

Start with H.264 or HEVC, 1080p60, and 30–50 Mbps in Moonlight. These are starting settings, not a performance guarantee. Hardware and network conditions determine what works.

For the original video, use **视频区域 · 固定中央 16:9**. To inspect the whole phone, choose **整个手机屏幕 · 保留比例**. A 4:3 iPad still needs letterboxing to show a complete 16:9 picture. [Setup, controls, and troubleshooting →](docs/getting-started.md)

## Pick the right tool

| Tool | Sender | Receiver / workflow | Best fit |
| --- | --- | --- | --- |
| **MoonCast** | Android phone | Existing Moonlight client; no PC intermediary | Focused LAN phone casting with video framing and optional input |
| [Mirror](https://github.com/jqssun/android-display-mirror) | Android | Moonlight, AirPlay, or DisplayLink; optional Shizuku for input | A broader Android display-sharing hub; MoonCast's native upstream |
| [scrcpy](https://github.com/Genymobile/scrcpy) | Android via ADB for ordinary mirroring | Windows, macOS, or Linux desktop app | Mature phone-to-computer mirroring, recording, and control |
| [Sunshine](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2getting__started.html) | Desktop host | Moonlight clients | Desktop and game streaming |

This is a workflow comparison, not a measured latency or quality ranking. [Detailed comparison and sources →](docs/comparison.md)

## What works, and what is still experimental?

| Area | Current implementation | Practical limit |
| --- | --- | --- |
| Video | H.264 / HEVC, hardware encoder, SDR | Lossy encoding; no HDR, AV1, or original-file playback |
| Framing | Whole screen, automatic crop, fill, centered 16:9 | Fixed preset assumes centered landscape video; automatic detection is heuristic |
| Audio | Android 10+ playback capture → Opus | Source apps must permit capture; Root capture is video-only |
| Local mute | Optional media-volume mute + recovery journal | Real ROM audio behavior needs testing; user volume changes are respected |
| Accessibility input | Taps, one-finger swipe on release, navigation | No arbitrary keyboard text, held touch, or multi-touch |
| Root input | Continuous touch, up to ten pointers, basic keys | Experimental hidden APIs and ROM permissions |
| Sessions | One active receiver | Simultaneous multi-receiver streaming is not implemented |

Root input can be combined with ordinary screen capture. Input defaults off. Root screen capture is a separate experimental option. Protected/DRM surfaces are not supported. See [compatibility](docs/compatibility.md) and [validation](docs/validation.md); no latency, 4K60, thermal, or visual-losslessness benchmark is claimed.

## Build and verify

Use JDK 17, Android SDK Platform 35, and Build Tools 35.0.0. Configure `ANDROID_HOME` or a local, uncommitted `local.properties`.

```sh
git clone https://github.com/meta-tabchen/MoonCast.git
cd MoonCast
./gradlew :app:assembleDebug :app:lintDebug
python scripts/run_unit_tests.py
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`. The default build includes pinned native runtimes; their source, licenses, and hashes are in this repository. Native-source rebuilding has a separate toolchain and has **not** been validated for this preview. [Full build guide →](docs/building.md)

## Help make the next release better

Device reports are particularly useful: phone/Android version, receiver/Moonlight version, codec, resolution/FPS/bitrate, and steps to reproduce. Please use the [compatibility report](https://github.com/meta-tabchen/MoonCast/issues/new?template=compatibility.yml) or [bug report](https://github.com/meta-tabchen/MoonCast/issues/new?template=bug.yml) and redact private data.

See [contributing](CONTRIBUTING.md), [security](SECURITY.md), and the [roadmap](docs/roadmap.md). If MoonCast is useful to you, a star helps other Moonlight users discover it.

## License and credits

MoonCast is distributed under [GPLv3](LICENSE), with retained third-party notices and licenses. The native core is pinned to Mirror commit `d7eea1b9e44eea38850bffa32ed0623506a2aa9a`; see [source lock](native/SOURCE_LOCK.json) and [runtime manifest](native/prebuilt-manifest.json). Thank you to Mirror, Sunshine, Moonlight, and their dependency maintainers.
