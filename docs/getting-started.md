# Getting started

## Requirements

- Android 8.0+ phone with a hardware H.264 encoder; HEVC depends on the device.
- An [official Moonlight client](https://moonlight-stream.org/) and a trusted local network. Android TV and iPad are intended use cases, not a guarantee for every model.
- Android 10+ and an eligible source app for playback audio.
- Root is optional. Ordinary screen capture needs neither Root nor ADB.

Download the APK from this repository's [Releases](https://github.com/meta-tabchen/MoonCast/releases). The preview is signed with a local debug key. APK signature verification and checksums are provided, but this is not a production signing setup. Future signing changes may require uninstalling; that removes pairing data.

## Connect

1. Start MoonCast on the phone and tap **Start casting**. Chinese: **启动投屏**.
2. Approve sharing the **entire screen** in Android's capture dialog.
3. Open Moonlight on the receiver. Discover the phone or add the **Phone LAN IP** shown in MoonCast.
4. Initiate pairing in Moonlight and enter its four-digit PIN in MoonCast.
5. Open **Desktop** in Moonlight, then switch the phone to the app you want to show.
6. Stop using MoonCast or its notification. Ordinary capture stays shared when the receiver disconnects. Wait for the reconnect-ready state while the native transport restarts; a new connection can replace a previous session whose disconnect is still pending. Pairing survives app restarts.

Start with 1080p60, H.264 or HEVC, and 30–50 Mbps in Moonlight. Resolution/FPS/bitrate come from the client negotiation, not a promise from MoonCast. Increase them only after your encoder, decoder, and network work reliably.

## Choose framing

| Mode | Use it when | What remains / gets removed |
| --- | --- | --- |
| Whole screen · Fit | Showing the phone UI or testing input | Complete screen; aspect-ratio bars are expected |
| Auto crop · Fit | A landscape player has near-black outer borders | Heuristic crop; necessary receiver letterboxing remains |
| Fill screen · Crop edges | Filling the receiver matters more than seeing all edges | Some picture content is cropped |
| Video region · Centered 16:9 | Landscape video is centered and 16:9 | Deterministic center crop; no manual selection |
| Smart cinema | Stable landscape border acquisition | Locks after eight consistent samples; holds through controls/dark frames until rescan/resize/mode change |

The fixed preset selects 1920×1080 from a 2412×1080 phone. It does not identify the player or query the original video's bounds. It falls back to the full screen in portrait. A 4:3 receiver cannot display a complete 16:9 video without letterboxing or distortion/cropping.

Auto crop samples a small GPU image every 250 ms, including a paused frame. Shrinking requires three stable candidates; expansion requires six. Dark frames hold the previous crop. This reduces some false changes but does not reliably identify every player or source aspect ratio.

## Sound

Enable **Stream playback audio** before starting. Android displays an audio permission prompt, but this implementation captures eligible playback, not the microphone. Source apps may opt out; protected audio is unsupported.

**Mute phone locally** only takes effect after the receiver requests audio and capture starts. It modifies media volume, leaves alarms/ringtones alone, and restores the saved value on stop or capture failure. A recovery journal supports retrying restoration on the next app launch after a crash. If you deliberately change phone volume to a nonzero value, the new value is kept. Fixed-volume devices may refuse muting. Verify that the receiver remains audible on your ROM.

Root screen capture is currently video-only. Root input can still be paired with ordinary MediaProjection capture and its audio path.

## Optional remote input

The switch defaults off. Enable it only if you want the paired receiver to operate the phone.

- **Accessibility**: enable the MoonCast service in Android Settings. Supports taps, single-finger swipes executed on release, Back, Home, and Recents. It does not read window text. No arbitrary keyboard typing, multi-touch, or continuous held touch. A gesture already submitted to Android may finish after disabling.
- **Root**: grant `su` when starting the input broker. Experimental continuous touch, up to ten pointers, and basic keyboard mapping. Depends on ROM permissions/hidden APIs.
- **Shizuku**: install/start official Shizuku separately and authorize MoonCast. Continuous touch and basic keys use an owned shell/root helper; no permission is silently enabled.

Start input tests in Whole screen mode; then check cropped/fill coordinates. Black-bar clicks are ignored. Gamepad, wheel, and right-click are not implemented. On iPad try Moonlight's touchpad mode first.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Phone not found | Same subnet, Wi-Fi client isolation, mDNS; add the IP manually |
| Black protected video | DRM / `FLAG_SECURE` layers cannot be captured |
| Other black screen | Entire-screen authorization, codec support, requested dimensions; try H.264 1080p |
| No playback audio | Android 10+, permission, source capture policy; Root capture is video-only |
| Duplicate borders | Centered 16:9 preset for suitable content; auto detection is heuristic |
| Touch does nothing | Remote input enabled, Accessibility/Root authorized, Moonlight input mode |
| Touch location wrong | Record mode, orientation, phone and negotiated sizes; file a report |
| Port in use | Stop another Sunshine/GameStream host on the phone |

**Connection and device settings** contains encoder information, a deterministic visual test pattern, and diagnostics. Lower-level diagnostics may still include Chinese or upstream English messages. Do not post certificates, private keys, pairing records, full screen captures, or private network details.

## Profiles, presets and dashboard

Choose General/iPad/TV/computer before starting; each stores independent app preferences.
This is manual selection, not client identity detection. Movie/Game/Power saver cap capture
submissions at 60/120/30 FPS; bitrate and requested resolution/FPS still belong to Moonlight.
The dashboard shows app-UID outbound traffic, submitted frames, CPU/driver submission time,
battery temperature and OS thermal state. It does not measure receiver FPS or latency.

## App display and file cinema

Choose Single app on Android 14+ and pick the app in system consent. Input is disabled
because the source window position is unavailable. Independent app display requires
Shizuku, then a connected receiver: use Launch app on that display. It sends video only.
Apps can refuse secondary displays or protected capture.

Original-file cinema selects one media document and shares a tokenized LAN browser link.
Open that link in a compatible receiver browser, not in Moonlight. File bytes are original;
codec/container/HDR decoding depends on the browser. Stop sharing revokes the link, and
selecting another file replaces it. Anyone holding the link can read it while active.

## Watch party and local panel off (experimental)

Enable Watch party before casting to permit up to three receivers. Framing is shared,
input is off and audio packet duration must match. Different sizes create separate encoders;
phone hardware/network capacity can limit this. Use the receiver list to disconnect one.
MuMu GPU fan-out passed; full multi-session encode/transport/decode still needs phone tests.

Local display off is a per-session option requiring Root capture or Shizuku. It starts
after submitted frames and keeps Android interactive. Restore local display, stop, capture
error and owner loss attempt restoration. A missing heartbeat expires in 30 seconds.
Unsupported or ambiguous physical-panel APIs refuse the operation. A forcibly killed
privileged helper/system failure can defeat software recovery; use the physical power
button. Phone panel effect, continued playback and battery savings are not measured.
