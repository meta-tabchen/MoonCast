# 0.8 (code 65)

- Consolidate local AirPlay candidates without publishing intermediate releases; preserve package and the fixed community test signer.
- Support legacy HTTP mirroring while retaining authenticated negotiation and bounded cleanup; preserve the corrected captured-content aspect ratio.
- Parse explicitly declared AVCC framing before Annex B detection; retain SPS/NAL/PTS and bounded TCP/media diagnostics.
- Keep writable media alive after a bare peer send-side FIN, report HTTP rejection before waiting for its body, and select the newest valid diagnostic snapshot.
- Add race/loopback/strict-decode regressions and timestamped diagnostic provenance. Host checks are not target-TV acceptance: the reported receiver exit remains unverified.
- The Mac full Java runner retains an OriginalMediaServerTest connection-reset failure; other eleven groups pass independently. See [validation](docs/release-0.8-validation.md).

# 0.6.0 (code 56)

- Use numeric version names throughout app metadata and About, and `MoonCast-0.6.0-<abi>.apk` release filenames.
- Preserve the fixed package/signing identity, final selected icon and casting behavior.
- Keep earlier tagged versions and downloads available; stage and validation limits remain in documentation.

# 0.6.0 browser preview.4 (code 55, local development candidate)

- Generate temporary browser-link QR codes offline with pinned Apache-2.0 ZXing; copy and QR share one selected link and Stop clears both.
- Add device-aware browser quality presets and advanced dimensions/FPS/bitrate ceilings, preserving the verified default. Changes apply to the next capture lifetime.
- Carry the same immutable profile through initial capture, resize and viewer reconnects; verify WebRTC parameter acceptance.
- Query reported hardware H.264/VP8 capabilities and filter unsupported browser codecs. Add AirPlay 2 advanced quality controls and select a reported-compatible hardware encoder; retain AirPlay 1 bounds.
- Display browser target ceilings alongside measured received pixels, codec, decoded FPS and RTP video Mbps. Receiver framing remains independent.
- Pin and bundle QR core production source, POMs and notices, with offline source rebuild and pixel decode checks.
- Update code-55 APK/CI/source metadata; no new universal compatibility or lossless-quality claim.

# 0.6.0 browser preview.3 (code 54, development branch)

- Add an independent Android-to-browser WebRTC mode using the existing MediaProjection/GLES capture pipeline.
- Serve a temporary LAN receiver page and bounded HTTP signaling, with one viewer, full ICE negotiation and reconnection leases.
- Forward only Android-permitted playback PCM through an open-source WebRTC audio callback; microphone recording remains disabled.
- Add bilingual browser controls for explicit playback/sound, fullscreen, Fit/Fill, zoom, pan and reset.
- Replace the phone's large protocol buttons with one lightweight selection strip, including Browser.
- Read installed versionName/versionCode in phone and TV About; show an optional recorded build ID.
- Keep role-menu cancel, Back and same-role selection in the active page without tearing down its sender or receiver.
- Preserve JNI Zero classes required by the native WebRTC SDK in shrunk release APKs.
- Move device/language/About into a compact title-bar Settings entry, center the phone layout on tablets, and show receiver links only while sharing.
- Show unexpected browser sender exits as failures, preserving bounded diagnostics instead of silently reporting a normal Stop.
- Preserve existing protocols and fixed community signing identity. See docs/browser-validation.md for measured and untested scope.

# 0.5.0 unified preview (code 50)

- One application with automatic phone/TV role selection and remembered manual override.
- Transactional role switching waits for private process/resource cleanup before committing.
- Unified My TV / APK transfer and all 0.4.6 phone + 0.4.5 TV audio fixes retained.
- Receiver OpenSSL has a distinct ELF identity and runs separately from sender runtimes.
- Four standby themes with remote-focus preview, explicit apply and persistent selection.
- Updated bilingual setup, security, migration, native build and validation documentation.
- Experimental separate package; no automatic migration or production-signature continuity.

# 0.4.5 AirPlay audio transition candidate

- Consume known empty RTP sequence slots instead of waiting for impossible resends; apply explicit FLUSH boundaries.
- Preserve decoded audio/video clocks and normal loss recovery; rebuild native runtimes for three ABIs.
- Authorized independent audio-test package, versionCode9. Launcher label MoonCast; version and package appear in About.
- Host regressions do not establish Hongguo/iPhone/physical-TV interoperability; see docs/tv-airplay-audio-transitions.md.

# Changelog

## TV 0.4.4 — 2026-10-11 (APK validation fix preview)

- Accept ordinary APKs with explicitly false Play split-required metadata or optional split inventory. Continue rejecting actual split APKs, required flags, uses-split and unresolved required metadata.
- Refresh install-source permission and revalidate on actual foreground return, including OEM early/missing result callbacks. Retain a narrow ten-minute review lease across Settings recreation, with explicit next-click installation and actionable expiry messages.
- Restyle the review action with clear waiting/checking/permission/install states and focused remote navigation; rename the home action Receive APK.
- Preserve local review and user-controlled Android installation; no package-name whitelist or self-update restriction is introduced.
- Generated unsigned APK regression reproduces old rejection/new acceptance; previously delivered signed MoonCast APKs also pass structural checks. This does not establish the cause of an unprovided user APK or physical TV installer compatibility.

## 0.3.1 — 2026-10-08 (preview)

- End the host process with the last receiver. The native host only frees a peer-to-session entry once that session is marked stopping, and a session the host merely parked kept matching the next client to encoders that had already exited; the client then failed with an initial ping timeout instead of streaming. Reconnecting asks for screen capture consent again.
- Deliver Stop through a separate command looper and arm a process-exit deadline before potentially blocked resource cleanup. Fatal capture failures also stop the native host.
- Recover stale, dead and unanswered UI service bindings; show stopping immediately and restore local volume after host death.
- Add debug-only blocked-main/blocked-cleanup lifecycle checks, zero-output GPU reconnect coverage and optional physical-phone Moonlight loopback checks. Native binaries remain unchanged.

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
