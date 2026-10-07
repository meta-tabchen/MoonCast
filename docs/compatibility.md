# Compatibility and limits

| Component | Requirement / status |
| --- | --- |
| Sender | Android 8.0+ / API 26, Surface-capable hardware H.264 encoder |
| Sender runtimes | arm64-v8a, armeabi-v7a, x86_64 |
| HEVC | Advertised only if a hardware HEVC encoder is detected; full parameter support remains device-dependent |
| Playback audio | Android 10+; source app capture policy and permission apply |
| Receiver | Existing Moonlight client; actual compatibility varies with decoding/input capabilities |
| Ordinary capture | Explicit whole-screen MediaProjection consent; new grant for each session on current Android |
| Accessibility control | User must enable the service; single-finger gestures/navigation only |
| Root capture/input | Experimental `su` / `app_process` / hidden APIs; ROM-dependent |
| Network | Trusted LAN, reachable host ports; multicast discovery optional |
| Concurrent receivers | One active session; multiple paired clients can be remembered, simultaneous streams are not supported |
| Image formats | Lossy H.264/HEVC SDR; no HDR, AV1, lossless RGB, or original-file playback |
| Protected content | DRM/secure layers unsupported |

## Known evidence

One PGP110 phone running Android 15 has been used for installation and basic Android → iPad streaming. The user's latest feedback for 0.2.0 was informally positive. This does not establish compatibility across models or verify every feature. Version 0.2.1 adds localization/presentation changes; streaming and input behavior need regression testing.

Android TV, other Moonlight client platforms, long sessions, reconnects, actual audio/muting behavior, and Root/Accessibility input need systematic reports. Please do not label a receiver supported solely because Moonlight is available on it.

## Networking

Default native host ports: TCP 47989, 47984, 48010; UDP 47998, 47999, 48000. LAN discovery uses `_nvstream._tcp` mDNS. Guest Wi-Fi, client isolation, or multicast restrictions can prevent discovery/connection. This preview is intended for a trusted local network; it is not an Internet-hosting service and has not received a security audit.

## Reporting a combination

Use the [compatibility issue form](https://github.com/meta-tabchen/MoonCast/issues/new?template=compatibility.yml). Include phone model/Android/ROM, app version, Moonlight/client hardware and version, codec, requested resolution/FPS/bitrate, framing/input/audio choices, and actual result. Separate “installed”, “paired”, “video”, “audio”, “mute”, and “input” observations. Redact private IPs, keys, certificates, and pairing data.
