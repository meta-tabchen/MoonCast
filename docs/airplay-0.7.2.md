# AirPlay optional capability follow-up / 可选能力探测修正

Version **0.7.2**, code **59**, package `com.mooncast.community`.

The user tested 0.7.1 and reported `stage=GET/info; status=501`; the same
Android phone can mirror through Mirroring360 and an iPhone can mirror to the
same TV. These reports establish an interoperability problem in MoonCast, not
a general inability of that receiver to mirror. No original receiver response,
TV model or physical-TV test is available in this build environment.

The pinned sender treated every failed `/info` request as fatal before pairing.
The small native patch treats **404, 405 and 501 only** as a missing optional
capability endpoint. Authentication/permission statuses, transport errors and
malformed successful plists still fail. This follows the optional-information
principle in [pyatv's RTSP info request](https://github.com/postlund/pyatv/blob/master/pyatv/support/rtsp.py),
with a narrower allowlist than pyatv's non-200 behavior.

Unknown features are distinct from explicitly absent features. After a missing
`/info`, the existing raw pairing path still exchanges and verifies its keys;
FairPlay SAP is still attempted after successful pairing. An authentication
error is not converted to a successful session. Existing successful capability
responses retain their pairing/FairPlay selection. Normal negotiated binary
session/audio/video SETUP and RECORD remain unchanged; no guessed port,
global HTTP rewrite, empty SETUP or authentication bypass is added.

This does not add the older HTTP `/stream` mirroring protocol. HTTP
`/server-info` XML alone would not implement that media/encryption session.
If this TV also rejects the subsequent pairing or media requests, its actual
new stage/status and receiver capabilities are still needed to choose the next
protocol fix. **This release has no claim of successful TV interoperability.**

用户实测0.7.1在GET /info收到501时退出，但相同手机使用Mirroring360及iPhone均可
投同一电视。本版修正发送器把可选能力查询当作必需步骤的问题：仅404、405、501可
继续正常认证；认证拒绝、连接错误及成功响应中的非法plist仍报错。能力未知时不能
当作明确不支持FairPlay，故保留后续FairPlay协商。正常配对、加密媒体和分阶段SETUP
不变，不猜端口、不把全部请求改HTTP、不走空SETUP，不绕过认证。

仅有mock及主机回归证据，不代表已在用户电视投屏成功。也未实现老式HTTP /stream
镜像会话；查询HTTP server-info不能替代实际媒体协议。若仍失败，请使用新版准确
阶段/状态与电视型号定位下一步，无须提供PIN、配对密钥或个人画面。

## Reproducible checks / 可复现检查

- Old source fails the 404/405/501 regression; patched source reaches the real
  pair-setup endpoint and preserves a mock receiver's 403 rejection.
- 400/401/403/470/500, malformed 200 plists and transport failures stay fatal.
- Unknown capabilities still send FairPlay m1 and propagate its mock 401;
  explicitly absent FairPlay capabilities retain the prior selection.
- Existing negotiated session/audio/video SETUP wire-shape, FairPlay and
  cancellation/lifecycle regressions exercise the exact freshly prepared source.
- Native source lock, built AAR/JNI hashes, APK identities and validation outcomes
  are recorded in `native/airplay-sender/prebuilt-manifest.json` and
  [release validation](release-0.7.2-validation.md).

Mock tests are protocol-contract checks, not pairing or streaming acceptance on
a TV. Existing diagnostic filtering keeps response bodies, keys and identities
out of user-visible errors; native debug remains disabled.
