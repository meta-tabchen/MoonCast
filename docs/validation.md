# Validation scope

Date: 2026-10-08 (Asia/Hong_Kong). Current source: **0.3.0 preview**, versionCode 8.

## 0.3.0 feature evidence

The seven features were implemented and committed separately; see [feature-by-feature
behavior and limits](feature-development.md). Existing MuMu Android 15/12 development
emulators were reused, with generated test content and explicitly authorized Shizuku/Root.
No physical phone/receiver was connected for this cycle.

- Portable geometry, cinema acquisition/lock/rescan, volume recovery, statistics, live HTTP file byte-range/revocation and display-power lease tests pass.
- Android 15 GPU instrumentation passes actual EGL/SurfaceTexture fit/fill, fixed region, paused sampling, portrait resize, controls over locked borders and frame limits.
- Profile migration/isolation/persistence and generated SAF file service original bytes/range/stop tests pass.
- Independent app display produces pixels and receives Shizuku touch/key/cancel; display release passes. Android 12 Root input broker touch/key/cancel/cleanup passes.
- Multi-output GPU checks pass one capture feeding different sizes, removal/addition and survivor frames. Pinned native session bridge loads and rejects inactive handles. Its three-ABI NDK CI build passed.
- A debug-only official Moonlight-core loopback harness reaches native TLS launch. MuMu has no hardware AVC encoder and its software encoder rejects native configuration. Full multi-session encode/transport/decode, native selective disconnect, shared audio and receiver interoperability are **not passed** by these tests.
- Shizuku actual panel-power RPC/manual restore/30-second expiry/cleanup pass, Android stays interactive. Root bounded API/expiry probe passes. Physical phone panel effect, continued Moonlight playback and power savings are unmeasured.
- Screen mirroring remains lossy SDR H.264/HEVC. Original-file byte equality is a separate HTTP workflow, not a rendered-quality/HDR guarantee.

Local 0.3.0 debug/release builds and both lint variants pass. Release lint reports 0 errors
and 27 warnings (retained); 170 default/Chinese string keys match. APK versionCode 8,
minSdk 26 and targetSdk 35 are checked. MuMu install/launch and English/Chinese primary
page visual checks pass. Signature v2 and ZIP alignment checks pass before publication.

The APK contains twelve pinned native libraries (nine upstream plus three session bridges).
Release builds exclude debug test clients, test core binaries and the root power probe.
CI remains desktop build/lint/portable/static checks; it does not replace these Android or
actual phone/receiver checks. Full native-core source rebuilding remains unverified.

## Historical 0.2.2 publication checks

- Local `assembleRelease` and `lintDebug` complete successfully. 0 errors, 15 warnings. Warnings are retained; see the detailed Chinese record.
- Portable Java crop/fit/input-coordinate/stabilization tests and media-volume recovery tests pass.
- Default and Chinese resource sets contain the same primary UI keys. APK metadata includes the Chinese configuration plus default English; the manifest declares English/Chinese app languages.
- APK payload verification checks the nine pinned native libraries, three ABIs, and nine JNI exports per ABI. Signature and zip alignment are checked before publication.
- No Android device was connected during 0.2.2 preparation. English/Chinese visual layout, installation, and actual streaming regression tests for this version have **not** been run on a device.
- CI is configured to build/lint, run portable tests, and verify the debug APK. CI does not test MediaProjection, Root, actual audio output, or a Moonlight receiver.

## Earlier runtime evidence

0.2.0 was installed on a PGP110 / Android 15 phone. The user informally reported no obvious issues; no feature-by-feature report accompanied that feedback. An earlier build streamed from Android to iPad; the user reported fill mode working and auto crop leaving duplicate borders.

0.1.2 GPU instrumentation passed on the real phone with generated test content: SurfaceTexture/EGL output, centered 16:9 cropping, fit/fill, color orientation, and portrait-size changes. That does not establish that the new timer-driven auto detector in 0.2.0 passes real video or paused-frame end-to-end tests. The added paused-frame test passes on MuMu in 0.3.0; it has not been repeated on that phone.

The current auto detector's pure Java checks use synthetic frames and a small sample from the actual phone screenshot. Those verify geometric/stabilization logic, not every player's video boundary.

## Still unverified on physical phones/receivers

- Actual phone Accessibility/Root/Shizuku input and OEM behavior beyond the generated emulator checks.
- “Phone silent, receiver audible” playback/mute behavior, restoration, and crash recovery on real ROMs.
- Broad Android TV/client decoding, sessions, reconnects, rotation, long-running and thermal behavior.
- Full NDK native-source rebuild; current APK uses pinned upstream runtimes.
- Latency, lost frames, PSNR/SSIM, power, high bitrate or 4K60 performance.
- Security audit and complete diagnostic localization.

Do not infer these results from a successful compile, native ABI check, or informal feedback. The [detailed Chinese development record](../VALIDATION.md) retains historical test results. Report exact combinations using the [compatibility form](https://github.com/meta-tabchen/MoonCast/issues/new?template=compatibility.yml).

The Android CI run on main also passed in a clean GitHub runner. Version 0.2.2 includes the SDK/action configuration fix and a null-safe capture-stop error guard.

## Historical 0.2.2 APK identity

Version 0.2.2 / code 7; debug-key-signed release-variant preview. Size: 14821713 bytes. SHA-256: `b87d64fcf9b8876a07b6cdb5e80f47ae95a8b26d1303b214b835b3fa7a05f93a`.
