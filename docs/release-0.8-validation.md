# MoonCast 0.8 / code65 validation

This release consolidates the local 0.7.3–0.7.6 candidates. Those intermediate versions are not new public releases. Package: `com.mooncast.community`. Fixed public test certificate SHA-256: `290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2`. The About screen reads the installed package version/code; build identity is separate.

## Runtime scope

The source includes authenticated capability negotiation and legacy HTTP mirroring, captured-content ratio handling, explicit AVCC framing, bounded media/TCP diagnostics, send-side FIN handling, immediate HTTP rejection and newest-snapshot selection. The five encoder/framing/capture files from the delivered code63 remain unchanged in the code64 lifecycle change and this version-only code65 build. See [lifecycle reproductions](airplay-0.7.6-lifecycle.md) and [framing comparison](airplay-0.7.5-m360-comparison.md).

## Retained host evidence

- 31 named Go race cases passed for the lifecycle candidate. The quick suite skipped the long soak explicitly.
- A separate 180-second real TCP soak sent peer CloseWrite at 30 seconds and continued receiving video to 180 seconds: 3,595 video packets, 179 receiver-observed heartbeats and 18 configs, followed by 25 reconnect frames. All 3,620 frames decoded with strict FFmpeg checks. Sender writes are not playback acknowledgement.
- H.264 framing has 276 checks. The Mac full Java runner exited 1 at the existing OriginalMediaServerTest Connection reset failure; eleven other groups passed, with the remaining groups run separately. This is not an all-tests-pass claim.
- This release's two APKs, build/lint, version/signature/native/DEX/ZIP checks and archive hashes are recorded in the Release provenance assets. Do not infer success from this source document before those results exist.

## Device and CI limits

The target TV previously returned to its launcher before sender FPS fell. No new physical-TV stability, audio interoperability, latency or end-to-end acceptance is established. No phone pairing data was erased and no device APK was installed for this release.

CI runs are attached to the exact public commit. Push-triggered manual device jobs are skipped, not passed. Public Release provenance records actual run URLs and outcomes separately from local checks. A failed CI job must remain visible even if local APK verification passes.

## Source distribution

The curated source ZIP records its exact committed snapshot and per-file hashes. The app-signing PKCS12 is excluded; the public test fixture remains in Git. WebRTC source/DEPS coordinates and build mappings are pinned, but the archive is not a full offline Chromium/WebRTC SDK tree and the prebuilt WebRTC native SDK was not independently rebuilt. Existing native licenses and source locks remain intact.

## 中文摘要

0.8/code65统一此前本地修复，不发布中间版本。固定包名、公测签名与历史记录保留。主机半关闭、拒绝响应、诊断新旧选择及严格解码回归有证据；完整Java脚本仍有OriginalMediaServerTest连接重置失败，其余11组独立通过。目标电视退出问题尚未完成真机验收，不能称稳定性已修复。双ABI产物、公开下载哈希及CI实际结果以本次Release来源记录为准；跳过的设备任务不算通过。
