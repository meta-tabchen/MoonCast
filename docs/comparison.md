# Choosing a casting tool

MoonCast is useful when the sender is an **Android phone** and the receiver already runs **Moonlight**. It is not the only Android Moonlight host: Mirror already provides that capability and supplies this project's native core.

| Question | MoonCast 0.2.1 preview | Mirror | scrcpy | Sunshine |
| --- | --- | --- | --- | --- |
| Primary sender | Android phone | Android | Android | Desktop host |
| Receiver | Existing Moonlight client | Moonlight, AirPlay, DisplayLink | Desktop scrcpy app on Windows/macOS/Linux | Moonlight client |
| PC needed between phone and receiver? | No | No for Moonlight/AirPlay | A desktop runs the standard receiver | Desktop is the sender |
| Ordinary screen sharing privileges | MediaProjection consent; no Root/ADB | Android display sharing without privileged access | ADB authorization for ordinary mirroring | OS-specific capture permissions |
| Remote input path | Opt-in Accessibility or experimental Root | Optional Shizuku (ADB/wireless debugging/Root modes) | ADB and supported HID/UHID paths | Desktop input integration |
| Project emphasis | Phone video framing, playback/local audio choice, two optional input backends | Multi-protocol display sharing and virtual displays | Mature phone-to-desktop mirroring, control, recording | Desktop/game streaming |

Mirror's capabilities above follow its current README, checked on 2026-10-07. MoonCast vendors the older **fixed v0.0.34** native snapshot; it does not inherit every capability in current Mirror. This table compares normal workflows, not every optional mode or third-party receiver.

## When another tool is a better fit

- **Choose scrcpy** for a phone on a computer, recording, broad desktop input features, or its mature debugging workflow.
- **Choose Mirror** for AirPlay/DisplayLink, broader virtual-display workflows, or Shizuku-based input.
- **Choose Sunshine** when the desktop is what you want to stream.
- **Choose MoonCast** when you want a focused Android-to-Moonlight sender and are willing to help validate a preview across devices.

For original photos/video files, a compatible direct-file player can avoid screen recapture and re-encoding. MoonCast currently mirrors the screen; it does not implement original-file serving or DLNA media playback.

## Quality claims

No side-by-side latency, bitrate-efficiency, power, PSNR/SSIM, or thermal benchmark has been run. MoonCast uses lossy H.264/HEVC SDR encoding. High bitrate is not mathematical losslessness. Moonlight client support for HDR/AV1/high refresh rates does not mean this Android sender supports them.

## Primary sources

- [scrcpy official README](https://github.com/Genymobile/scrcpy): desktop platforms, ADB setup, capture/control workflows and license.
- [Mirror official README](https://github.com/jqssun/android-display-mirror): Android display sharing, receiver protocols, optional Shizuku input.
- [Sunshine official getting-started documentation](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2getting__started.html): desktop host installation and platform-specific capture.
- [Moonlight official site](https://moonlight-stream.org/): client ecosystem. Actual interoperability with MoonCast is still being tested.
- [MoonCast validation](validation.md): what has and has not been verified in this project.
