# AirPlay sender correction / AirPlay 发送修正

Version **0.7.1**, versionCode **58**, package `com.mooncast.community`.

## Confirmed defects and scope / 已确认问题与范围

The saved “AirPlay 1 compatibility” switch selected an upstream shortcut that
sent an empty `SETUP rtsp://receiver:port/video RTSP/1.0` with an interleaved
Transport header. It skipped `/info`, receiver-selected pairing, FairPlay and
session/stream binary plists. That shortcut is not a general AirPlay mirroring
handshake. The ordinary path was already `RTSP/1.0`; an HTTP 501 response alone
does not establish the request protocol or prove the receiver lacks AirPlay.

0.7.1 makes both quality choices use the existing negotiated path: `/info`,
capability-selected raw or HomeKit pairing, FairPlay where supported, session
SETUP, audio/video stream SETUP and RECORD. The old preference remains a 720p30,
4 Mbps, baseline-H.264, video-only quality choice. It no longer selects bare
transport. No fallback guesses a port, bypasses pairing, or changes TV/network
settings. Discovery still uses the `_airplay._tcp` advertised port; manual entry
retains the supplied port. The pinned native AAR/source are unchanged and verified.

原“AirPlay 1兼容模式”会跳过能力查询、配对与FairPlay，直接发送空体`/video SETUP`。
新版两个画质选项均走已有完整协商流程，按接收端能力选择配对方式；旧偏好仅保留
720p30／4Mbps／H.264 Baseline／仅视频限制。不猜测或强改端口，不绕过认证，
不要求电视安装MoonCast。原生AAR与固定源码均未修改，并检查原始哈希。

Native error callbacks previously discarded their argument. The UI now retains
a structural stage, status (e.g. `SETUP/session; status=501`) or bounded error
category, and saves the result for the matching session before teardown so a
short-lived process cannot hide it. Full native debug remains disabled. Response
bodies, keys, identities and frame samples are never copied into diagnostics.

错误回调原先直接丢弃底层原因，统一提示模式／PIN／局域网。新版显示失败阶段、
状态码或有限错误类别，并在退出前按会话保存，避免轮询错过。不会记录响应正文、
密钥、配对身份或帧内容，也不打开全量Debug。

## References and comparison / 开源实现对照

- [doubletake mirroring implementation](https://github.com/omarroth/doubletake/blob/568a06ed32afa9fafdf229a798db0786f4d020f2/internal/airplay/mirror.go): pinned sender session/stream negotiation and encrypted media.
- [pyatv RTSP implementation](https://github.com/postlund/pyatv/blob/master/pyatv/support/rtsp.py): RTSP/1.0, CSeq, binary plist SETUP; its sender-local-IP/session URI differs from the bundled sender. That difference alone is not a proven interoperability failure and is not speculatively rewritten here.
- [UxPlay dispatch](https://github.com/FDH2/UxPlay/blob/master/lib/raop.c) and [SETUP handler](https://github.com/FDH2/UxPlay/blob/master/lib/raop_handlers.h): RTSP method dispatch, binary plist and stream type 110/streamConnectionID handling.

The supplied screenshot reportedly shows 0.7 connecting then receiving 501 after
SETUP before media. No original packet capture, TV model, or complete original
request was supplied. It does **not** establish whether that attempt enabled the
shortcut. This release fixes the verified shortcut and diagnostic defects; it
does not claim the screenshot's TV root cause has been reproduced or eliminated.

用户截图提到SETUP后501，但缺原始报文、电视型号与请求全貌，无法确认是否开启旧
简化模式。修复对象为代码中已确认的握手分支和错误丢失，不宣称已复现并排除该电视
全部故障。若仍失败，所需证据为新版失败阶段／状态、电视品牌型号与系统版本、
发现端口及是否来自DNS-SD；无须公开PIN、密钥、私有日志或个人画面。

## Reproducible checks / 可复现检查

`scripts/run_airplay_sender_tests.py` tests structural errors, body redaction,
progress allowlisting, stale-session rejection and failure retention. The wiring
check prevents restoring the bare branch. `scripts/run_airplay_wire_tests.py`
prepares the exact pinned source with its existing patches and runs an offline
Go loopback receiver: bare empty `/video` gets synthetic HTTP 501, negotiated
binary session/audio/video SETUP succeeds. It checks request version, CSeq,
Content-Type, binary plist signature, stream IDs and ordering. This is a mock
protocol-contract test, not pairing or physical-TV acceptance. Go 1.25+ required;
the native AAR is not rebuilt by these tests.

Final build, identity, download and CI outcomes are recorded in
[release-0.7.1-validation.md](release-0.7.1-validation.md). No target-TV media,
audio, latency, sustained-streaming or universal-compatibility claim follows.
