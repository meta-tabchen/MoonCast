# Validation scope

Date: 2026-10-07 (Asia/Hong_Kong). Current source: **0.2.1 preview**, versionCode 6.

## 0.2.1 publication checks

- Local `assembleRelease` and `lintDebug` complete successfully. 0 errors, 15 warnings. Warnings are retained; see the detailed Chinese record.
- Portable Java crop/fit/input-coordinate/stabilization tests and media-volume recovery tests pass.
- Default and Chinese resource sets contain the same primary UI keys. APK metadata includes the Chinese configuration plus default English; the manifest declares English/Chinese app languages.
- APK payload verification checks the nine pinned native libraries, three ABIs, and nine JNI exports per ABI. Signature and zip alignment are checked before publication.
- No Android device was connected during 0.2.1 preparation. English/Chinese visual layout, installation, and actual streaming regression tests for this version have **not** been run on a device.
- CI is configured to build/lint, run portable tests, and verify the debug APK. CI does not test MediaProjection, Root, actual audio output, or a Moonlight receiver.

## Earlier runtime evidence

0.2.0 was installed on a PGP110 / Android 15 phone. The user informally reported no obvious issues; no feature-by-feature report accompanied that feedback. An earlier build streamed from Android to iPad; the user reported fill mode working and auto crop leaving duplicate borders.

0.1.2 GPU instrumentation passed on the real phone with generated test content: SurfaceTexture/EGL output, centered 16:9 cropping, fit/fill, color orientation, and portrait-size changes. That does not establish that the new timer-driven auto detector in 0.2.0 passes real video or paused-frame end-to-end tests. The added paused-frame device test has not been run.

The current auto detector's pure Java checks use synthetic frames and a small sample from the actual phone screenshot. Those verify geometric/stabilization logic, not every player's video boundary.

## Still unverified

- Actual Root/Accessibility taps, held touch, keyboard, release/cleanup behavior and ROM support.
- “Phone silent, receiver audible” playback/mute behavior, restoration, and crash recovery on real ROMs.
- Broad Android TV/client decoding, sessions, reconnects, rotation, long-running and thermal behavior.
- Full NDK native-source rebuild; current APK uses pinned upstream runtimes.
- Latency, lost frames, PSNR/SSIM, power, high bitrate or 4K60 performance.
- Security audit and complete diagnostic localization.

Do not infer these results from a successful compile, native ABI check, or informal feedback. The [detailed Chinese development record](../VALIDATION.md) retains historical test results. Report exact combinations using the [compatibility form](https://github.com/meta-tabchen/MoonCast/issues/new?template=compatibility.yml).

## Published APK identity

Version 0.2.1 / code 6; debug-key-signed release-variant preview. Size: 14821561 bytes. SHA-256: `9e4c53c686967770b9940586a2b135877dc30153fd2e08a6b89b83d83629b241`.
