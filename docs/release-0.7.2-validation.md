# MoonCast 0.7.2 validation / 验证记录

- Application: `com.mooncast.community`, numeric version `0.7.2`, versionCode `59`.
- Baseline APK: 0.7.1/code58 ARM64, SHA256 `15d4c8a390a9860e909f8428228e5bbb2b970ca1615610eaa1dbb80439fc2a5d`.
- Baseline source: `70100fd89e0dacc1b0220403eee2f8d2894e6f2f` plus retained local 0.7.1 repair.
- Runtime/source commit and final hashes: pending build completion; not published.

## Checks

- PASS: old prepared native source fails missing-info regression; patched source passes.
- PASS: 404/405/501 proceed to normal pair-setup; receiver 403 is retained.
- PASS: 400/401/403/470/500, malformed successful plist and transport failures stay fatal.
- PASS: unknown capabilities still negotiate FairPlay; synthetic 401 remains an error.
- PASS: existing negotiated session/audio/video SETUP wire shape and selected FairPlay regressions.
- PASS: wrapper cancellation, stale generation, blocked writer and lifecycle tests.
- PASS: 17 diagnostic/privacy/failure-retention checks; 803 bilingual resource contracts.
- FAILED first attempt: host OriginalMediaServerTest could not bind a socket in the sandbox.
- PASS retry: complete host unit suite with permitted loopback access. The original failure log is retained.
- PASS: fresh source preparation, source lock and exact client/FairPlay source comparison.
- PASS: official NDK r29 archive checksum verification. SHA256 `ce5e4b100ec5fe5be4eb3edcb2c02528824ff9cda3860f5304619be6c3da34d3`.
- PASS: offline native AAR rebuilt for ARM64, ARMv7 and x86_64; new source/AAR/JNI manifest verified; all ELF LOAD segments >=16 KiB; no personal developer paths in runtime bytes.
- PASS: all Java class names and public APIs identical to previous AAR; JAR bytes differ under the recorded JDK17 build.
- PASS: 15 device-fixture metadata tests, installed build-info checks and host identity/BC regression. No device acceptance implied.
- Final APK / signature / lint: pending.
- ADB evidence: timed out while enumerating devices; no device change performed.
- Physical user-TV authentication, media, audio, latency and sustained streaming: NOT TESTED.
- GitHub commit/tag/release/download/CI: NOT PUBLISHED / NOT RUN.

The user reports the prior 0.7.1 attempt failed at GET /info with status501 while
Mirroring360 on the same Android phone and an iPhone can mirror to that TV.
This build fixes a verified early-abort defect; mock checks do not prove the TV's
subsequent protocol requirements or successful mirroring. No TV/network settings,
pairing storage or real credentials were changed.

修正对象是可选/info查询导致的提前退出。全部mock和主机检查不能替代用户电视认证、
媒体与稳定性实测。首轮沙箱端口测试失败及后续独立重试通过均保留，不改写历史。
