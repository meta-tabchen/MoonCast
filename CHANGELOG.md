# Changelog

## 0.3.0 — 2026-10-08 (preview)

- Stable smart cinema acquire/lock/rescan alongside the four existing framing modes.
- Independent General/iPad/TV/computer profiles, Movie/Game/Power saver presets and live capture/network/thermal statistics.
- Android 14+ single-app consent, Shizuku independent app display, and optional Shizuku touch/key input.
- Original-file LAN HTTP player with tokenized links, byte ranges and explicit stop.
- Experimental shared-capture fan-out to up to three receivers and individual native disconnect; pinned session JNI bridge rebuilt in CI.
- Experimental Root/Shizuku physical panel-off casting with manual restore, owner cleanup and 30-second watchdog leases.
- Generated-content MuMu checks cover rendering, profiles, file transfer, Shizuku/Root input and panel API/recovery. MuMu lacks a compatible hardware AVC encoder; multi-session encode/transport/decode remains a real-device check. No new phone/iPad interoperability claim.
- Synchronize file listener shutdown/connection registration; wait for blocked accept to finish before returning from close.
- Keep scrolling content clear of Android 15 system bars; improve file action layout and disabled control contrast.
- English/Chinese usage and feature-specific validation records updated. Debug-key-signed preview; full native-core source rebuild remains unverified.

## 0.2.2 — 2026-10-07 (preview)

- Preserve capture-error state when an exception has no message; avoid a null dereference in the stop callback.
- Correct CI SDK package selection and pin current supported actions; clean-runner CI passes.
- Retain 0.2.1 localization and native runtime payload. No new device tests are claimed.

## 0.2.1 — 2026-10-07 (preview)

- English default and Chinese primary UI resources, pairing/status/notification translations, and Android per-app language declaration.
- Wrapping for longer translated video/input options; version label follows build metadata.
- Bilingual project documentation, workflow comparison, compatibility/validation scope, contribution templates, and Android CI.
- Native binaries, protocol, and capture/input behavior retained from 0.2.0. Lower-level diagnostics are only partially localized.

## 0.2.0 — 2026-10-07 (preview)

- Independent timer sampling for automatic borders, including paused frames; stabilization, near-black border and small gesture-indicator handling.
- Opt-in Accessibility/Root remote input and shared crop/fill coordinate mapping.
- Card-based settings UI and expandable diagnostics.

## 0.1.3 (preview)

- Optional local media mute while audio capture succeeds, saved-volume restoration and crash recovery.

## 0.1.2 (preview)

- Fixed centered 16:9 video-region preset with whole-screen portrait fallback; prior framing modes retained.

Historical builds remain development milestones, not a compatibility guarantee. See [validation](docs/validation.md).
