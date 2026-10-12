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
