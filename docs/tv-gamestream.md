# Integrated GameStream receiver / 内置 GameStream 接收

## Status and scope

This document describes the **unreleased GameStream integration in the current source
tree**, reviewed on 2026-10-11. The published **TV v0.3 preview remains the AirPlay/DLNA
release**; it has not gained GameStream through this documentation change. No new
release, production signing identity, or physical phone-to-TV acceptance is claimed.

MoonCast TV now contains a real, view-only GameStream client implementation using the
official [Moonlight Android v12.2 source](https://github.com/moonlight-stream/moonlight-android/tree/b48494cb96bff23d8886c4775cc4f39a1075495d),
fixed at `b48494cb96bff23d8886c4775cc4f39a1075495d`. It receives the **MoonCast phone
sender's Desktop stream inside the TV app**, using MoonCast's own decoder, audio output,
and framing controls. It does not launch the separate Moonlight application. MoonCast
remains an independent GPLv3 project, not an official Moonlight product.

This is a companion-app route. **Android's system Cast / Wireless display picker does
not connect to this GameStream receiver.** AirPlay, DLNA, Miracast, Google Cast and
Lelink have different protocol and authorization requirements; see the
[system-casting assessment](tv-android-system-casting.md).

## Using a development build

1. Use an Android 8.0+ TV and a MoonCast Android phone sender on the same trusted
   private Wi-Fi/Ethernet IPv4 subnet. Disable access-point/client isolation. Do not
   expose receiver or sender ports to the Internet.
2. On the phone, tap **Start casting** and approve Android's screen-capture request.
   Use eligible, unprotected content. Playback-audio capture also depends on the
   phone's Android version, sender settings, and whether the source app allows it.
3. Open MoonCast TV and choose **Connect Android phone**. It is also available under
   **Advanced settings**. A bounded `_nvstream._tcp` search lists nearby hosts. If the
   phone is missing, rescan or choose **Add by IP address** using the phone's displayed
   private IPv4 address, optionally followed by its HTTP port, for example
   `192.168.1.20:47989`.
4. Select the phone and explicitly confirm **Pair** on the TV. Verify the device and
   address first: a discovery name alone does not authenticate it. Enter the TV's
   four-digit PIN in **MoonCast on the phone**. Successful pairing saves the host
   certificate and proceeds to its Desktop stream. A saved paired phone can be
   selected again to connect.
5. Press OK/Menu for the TV controls. Use **Framing** for Auto, Fit, Fill, or live
   four-edge crop/zoom/pan. Back while GameStream is connecting cancels the attempt;
   during playback, Back opens/returns from the controls without stopping the picture.
   **Stop playback** disconnects this TV session. Leaving the Activity or changing
   network stops the session; there is no background playback.

Only canonical RFC1918 IPv4 literals are accepted: `10.0.0.0/8`, `172.16.0.0/12`, or
`192.168.0.0/16`, and the address must also be on the selected TV subnet. Public IPs,
hostnames, URLs, IPv6 and off-subnet private addresses are rejected. These restrictions
are checked again before pairing/connection. Discovery is local, bounded and does not
automatically pair, create keys, or start playback.

### Pairing, cancellation and forgetting

- The explicit TV Pair action creates an AndroidKeyStore identity only if one is
  absent. Its private key is not exported; hardware backing depends on the TV. Opening
  the app or browsing hosts does not create a GameStream pairing identity.
- The phone's certificate is saved only after successful PIN pairing and verification.
  Subsequent connections use that pin. A certificate mismatch is an error; the saved
  certificate is not silently replaced. Verify why the phone's identity changed,
  explicitly **Forget** its old local record, then pair again if appropriate.
- Cancel/Back ends the attempt and suppresses a later automatic connection. It does
  not undo pairing already accepted by the phone. If an authorized atomic trust save
  has already started, it may complete and leave the paired phone in the local list;
  use **Forget** afterward if desired.
- **Forget is local only:** it removes this TV's saved host record/certificate. It
  does not revoke the TV on the phone, delete the TV's GameStream key, or send a remote
  unpair request. Remote revocation is not available from this TV UI.
- Saved host trust data is app-private and excluded from backup. Uninstalling/clearing
  app data loses local trust. Do not share pairing identities, keys, PINs or private
  logs in bug reports.

## Actual stream choices and limits

| TV quality choice | Negotiated request |
| --- | --- |
| 720p · 60 fps | 1280×720, 60 fps, 15 Mbps |
| 1080p · 60 fps (default) | 1920×1080, 60 fps, 30 Mbps |
| 1080p · 30 fps | 1920×1080, 30 fps, 20 Mbps |

These settings are passed into the GameStream configuration and apply to the **next
connection**. They are requests, not measured output or performance guarantees. The
sender, TV codec, network and thermal conditions determine actual delivery. The
Media3 quality limit and AirPlay mirror presets are separate controls.

- **Video:** H.264/AVC, SDR, decoded by Android MediaCodec into a TextureView Surface.
  No HEVC, AV1, HDR, 4K preset, or high-refresh-rate claim is made for this integration.
- **Audio:** stereo Opus from the protocol core becomes 48 kHz PCM16, copied through a bounded
  queue and written to AudioTrack. Surround and encoded passthrough are not supported.
- **Timing:** low-latency best-effort output; audio offset is **0 ms only**. There is no
  shared source-clock A/V calibration or signed advance/delay control. The Media3
  −500…+500 ms calibration does not apply to this live GameStream path, and AirPlay's
  native jitter-buffer setting does not apply either. Low-latency API requests and
  software queue bounds do not establish actual end-to-end latency or lip sync.
- **Diagnostics:** requested resolution/fps/bitrate are distinct from the UI's decoded
  size, decoder name, output fps and local drop count. The adapters also expose
  output-buffer counters, PCM submitted, and local queue/drop observations.
  These do not measure physical screen presentation, audible sound, total network
  loss or source-to-screen latency. No end-to-end latency number is claimed.
- **View only:** no keyboard, mouse, touch, gamepad or remote-control injection is
  exposed. The client selects Desktop and refuses to replace another running host
  app. Stopping disconnects this TV; it does not send a host Quit command or stop the
  phone's capture service. Stop casting on the phone when screen sharing is finished.
- **Protected content:** Android capture permission does not grant DRM/FLAG_SECURE
  bypass. Black video or absent audio from protected/non-capturable apps is not fixed
  by pairing, a higher bitrate or TV cropping.

### Framing stays in MoonCast TV

The decoded GameStream TextureView uses the same `VideoFraming`/`FramingSettings`
pipeline and app-wide framing preference as AirPlay and DLNA: Auto/remove black bars,
Fit, Fill, and live manual edges/zoom/pan. Source/size changes reset transient detector
coordinates and cancel obsolete edits. A crop detected in one video is not copied
into another video.

A portrait phone picture can be encoded inside a landscape frame. Auto may crop
stable encoded black borders, subject to its conservative detection rules. It does
not rotate the phone, turn portrait content into landscape, or force-fill a landscape
TV. Fit preserves the remaining picture's aspect ratio; Fill deliberately crops
edges. Manual crop/zoom can hide captions and controls. Use **Restore full image** or
Fit if content is missing. The TV cannot recover pixels already cropped by the sender.

GameStream shares one foreground playback owner with AirPlay/DLNA. Handover uses
protocol-specific stop barriers:

- GameStream's successful stop future includes successful renderer release; a reported
  exception or a stuck release does not permit replacement playback.
- The Media3 barrier waits for playback-thread Stop processing, successful audio
  inactivity acknowledgement (pause/flush), and all outstanding AudioTrack retirement
  callbacks. Upstream Media3 sends a retirement notification from a `finally` block:
  that callback does **not** prove that an exceptional vendor hardware-resource release
  succeeded. This is a narrower acknowledgement than the GameStream renderer barrier.
- AirPlay retains its old decoder and a failure state when `MediaCodec.release()`
  throws, blocking a replacement source. Surface attachment/retirement runs off the
  main thread. GameStream retains its Surface/SurfaceTexture until decoder detachment
  or successful renderer release is confirmed.

These are implemented lifecycle safeguards, not proof of every vendor codec's behavior
or a physical cross-protocol handover acceptance result.

## Source, build and validation

- [`moonlight-core/`](../moonlight-core/) contains the reduced upstream Java/native
  client core and the view-only adapter API; it does not embed Moonlight's full UI.
  [`SOURCE_LOCK.json`](../moonlight-core/SOURCE_LOCK.json),
  [`SOURCE_PATCHES.diff`](../moonlight-core/SOURCE_PATCHES.diff),
  [`JAVA-DEPENDENCIES.json`](../moonlight-core/JAVA-DEPENDENCIES.json), and
  [`NATIVE-PROVENANCE.md`](../moonlight-core/NATIVE-PROVENANCE.md) record pins, changes,
  dependencies and reproducibility limits. Source inventories also pin the recursive
  native inputs and selected Maven source archives/POMs. Preserve upstream license
  headers, dependency notices and corresponding source when distributing.
- [`tv/.../gamestream/`](../tv/src/main/java/com/mooncast/tv/gamestream/) owns host
  discovery, explicit pairing, local trust, session control and real MediaCodec /
  AudioTrack adapters. The actual renderers are separate from the synthetic test
  fakes under `tests/gamestream_render_stubs/`.
- JDK 17, SDK Platform 35 and Build Tools 35.0.0 build the reduced integration.
  Prepare its native inputs first using NDK 29.0.14206865 and CMake 3.31.10 on Linux:
  OpenSSL 4.0.2, Opus 1.6.1 and the JNI/common-C/ENet/nanors core are built from fixed
  source for all three ABIs. The selected runtimes do not link the historical upstream
  prebuilt OpenSSL/Opus archives. Native symbols are isolated from AirPlay's crypto.
  Source-only restoration is also supported separately from compilation; see
  [exact preparation and source-delivery instructions](../moonlight-core/scripts/SOURCE-BUILDING.md).
- Normal Gradle builds require the prepared runtimes and their actual-build manifest;
  a missing runtime/manifest or source-lock/hash mismatch fails instead of emitting a
  Java-only receiver. Generated runtimes and the manifest are not committed. The
  optional `-PmoonlightNativeFromSource` mode calls the same preparation/verification
  route. A tested relink under a different path produced different bytes, so CI verifies
  its own same-run manifest rather than claiming bit-for-bit reproducibility from the
  reference binary hashes. Canonical build-environment guards fix source paths and
  remove inherited compiler overrides; release packaging rejects uncommitted source files.

Relevant checks, to rerun against the final tree:

```sh
python3 moonlight-core/scripts/prepare_moonlight_core.py --build \
  --ndk /path/to/android-ndk-r29 --cmake /path/to/cmake
python3 moonlight-core/scripts/verify_moonlight_core.py
./gradlew :moonlight-core:testDebugUnitTest :tv:assembleDebug \
  :tv:assembleDebugAndroidTest :tv:assembleRelease :tv:lintDebug :tv:lintRelease
python3 scripts/run_gamestream_host_tests.py
python3 scripts/run_gamestream_discovery_tests.py
python3 scripts/run_gamestream_render_tests.py
python3 scripts/run_gamestream_controller_tests.py
python3 scripts/run_tv_provenance_tests.py
```

The host/core checks exercise selection, LAN restrictions, trust handling and session
lifecycle. Portable renderer tests use explicit synthetic Android fakes for callback,
queue and cleanup contracts. They cannot validate a physical codec, speaker, hardware
key provider, real discovery, phone capture or end-to-end streaming. Building the
instrumentation APK is not executing it. Retain the existing TV/AirPlay/DLNA and
Media3 regression checks in the [receiver guide](tv-receiver.md).

### Development verification — 2026-10-11

The following local checks have passed. They are not a published-build or
physical-device acceptance record:

- Two rounds of TV debug APK, unsigned release APK and instrumentation APK builds,
  plus debug/release lint.
- 34 core JVM tests.
- Portable GameStream renderer checks: 20,093 contract assertions and 109 synthetic
  lifecycle assertions.
- Portable host/trust checks: 267 assertions.
- Controller race checks: 14 scenarios, including cancellation and ownership cleanup.
- Existing PCM checks: 57,178 assertions; Media3 handoff-barrier checks: 26 assertions;
  process-wide Media3 stop-registry checks: 7 assertions.
- Canonical native build-environment and committed-source packaging guards: 4 tests,
  including preservation of export-ignored build inputs and exact Git blob bytes.
- AirPlay decoder release-failure regression: 18 synthetic scenarios / 998 checks;
  four temporary failure-path mutations were detected.
- Actual Android renderer suite: **56/56 assertions passed** on the frozen development
  APK using Android API 33 AOSP x86_64 software emulation. Production MediaCodec decoded
  generated 160×96 H.264 into real Surface pixels; AudioTrack accepted generated
  48 kHz stereo PCM. Surface replacement/detachment and renderer lifecycle were also
  exercised. See the [device-suite record and exact APK hashes](gamestream-renderer-device-validation.md).

The renderer suite does not start the host picker, pair, create keys, decode native
Opus, receive network video or establish audible output. The separate host-picker
UI probe was **inconclusive** because AOSP system/Quickstep ANR dialogs blocked a
reliable remote-control check; it is not a UI pass. No physical MoonCast-phone →
integrated-TV pair/video/audio/rotation/reconnect acceptance or measured latency/quality
result is claimed. Device acceptance must record the exact phone/ROM, TV/firmware,
app versions, network, requested preset and observed result. Check PIN cancellation, changed
certificate refusal, local Forget, rotation/framing, audio-focus loss, network changes,
Surface recreation, Stop/background cleanup, and AirPlay↔DLNA↔GameStream handover with
generated unprotected content before broader compatibility claims.

### Publication and signing

The current integration is unpublished. Existing v0.3 release assets remain unchanged.
New TV publication is gated on an externally provisioned, securely retained signing
identity and an independently chosen public certificate fingerprint. Without complete
external signing inputs the release variant is unsigned; there is **no debug-key
release fallback**. Development debug APKs do not satisfy the release gate. This work
does not create/upload a key or configure persistent signing secrets. See
[TV signing and publication](tv-signing.md) for the actual gates, source/checksum
requirements and owner-managed setup.

## 中文说明

### 版本与连接方式

这是**当前源码树已接入、仍在测试的未发布 GameStream 接收功能**。已发布的 TV v0.3 预览版仍是
AirPlay/DLNA 版本；没有据此新增发布、正式签名或手机到电视端到端实测承诺。

新路径把固定版本的官方 Moonlight Android v12.2 GPLv3 客户端核心接入 MoonCast TV，自行用
MediaCodec 解码、AudioTrack 播放，并复用 TV 裁切管线；无需跳转独立 Moonlight 应用。
**手机端必须运行 MoonCast 发送端，不能从安卓系统“投屏/无线显示”选择器连接这条路径。**
Lelink 厂商集成是另一条真实候选路线，但商业接收 SDK 的使用/分发权与 GPL 相容性仍待解决；
不能把所有安卓系统投屏都当成 Miracast，也不能把本次 GameStream 接入称作 Lelink 接入。

1. 手机与电视接入同一可信私有 IPv4 子网。在手机 MoonCast 点“启动投屏”，批准屏幕捕获；音频还要
   符合系统、发送端设置与源应用的捕获限制。受保护/DRM 内容不支持绕过。
2. 在 TV 首页或高级设置打开“连接安卓手机”，等待 `_nvstream._tcp` 发现，或手动填手机显示的 IP。
   仅接收同子网 RFC1918 IPv4 字面量，可追加 HTTP 端口；不接受域名、URL、公网地址或 IPv6。
3. 核实手机名称与地址后，在电视明确确认“配对”，把**电视显示的四位 PIN 输入手机 MoonCast**。
   配对成功后保存手机证书并连接 Desktop；网络发现本身不代表身份已经验证。
4. 遥控器确认/菜单打开裁切与播放控制。停止播放只断开这台 TV，不向手机发送 Quit、不停止手机捕获；
   连接中按返回会取消尝试，开始播放后返回只打开/收起菜单。结束共享后也在手机停止投屏。
   退出 TV 应用或网络变化会停止会话。

“取消/返回”会取消本次尝试并阻止之后自动连接，但不撤销手机已接受的配对。已经开始的授权原子信任
写入可能完成并留下本地记录，可随后“忘记”。**忘记只删除 TV 本地手机记录和证书，不是远端撤销**，
也不删除 TV 的 GameStream 密钥。手机证书变化会拒绝连接，不会静默覆盖；核实原因后明确忘记并重新配对。

### 画质、裁切、声音

- 三档实际传入协商参数：720p60 / 15 Mbps、1080p60 / 30 Mbps（默认）、1080p30 / 20 Mbps。
  只影响下次连接，实际输出取决于手机、TV 解码器与网络。
- 首版限定 H.264 SDR + 立体声 Opus/PCM16；不支持 HEVC、HDR、环绕声、反控或远程退出主机应用。
- 复用 AirPlay/DLNA 的全局 Auto/Fit/Fill 与四边裁切、缩放、平移设置；新来源/尺寸会重新识别黑边，
  不沿用上个视频的检测坐标。竖屏画面可能装在横屏编码帧里，裁黑边不会把竖屏内容强行铺满横屏电视。
  需要时手动调整，丢失内容时恢复完整画面；TV 无法找回手机发送前已裁掉的像素。
- GameStream 是尽力低延迟输出，**音频偏移仅 0 ms**，没有带正负号的音画校准；Media3 的
  −500…+500 ms 处理和 AirPlay 抖动缓冲不应用到这条路径。本地队列/输出统计不等于实际唇音同步、
  可听输出或端到端延迟，不提供未测量的毫秒数。
- 编译、静态检查、便携假实现测试以及测试 APK 构建，都不能替代真实手机与电视配对、画面、声音、
  旋转、重连和三协议切换验收。当前构建/lint、核心 JVM、便携渲染/主机信任/控制器与 PCM/Media3
  交接检查已通过；实际 Android 渲染套件在 API 33 AOSP x86_64 模拟器的固定开发 APK 上通过
  **56/56**，验证生成 H.264 的真实 Surface 像素、AudioTrack 写入与生命周期，见
  [执行记录与精确 APK 哈希](gamestream-renderer-device-validation.md)。它不包含手机网络发送、
  配对、原生 Opus 解码或可听声音验收。主机选择界面另受 AOSP 系统 ANR 干扰，结果不确定，不能算
  UI 通过；仍没有真实手机到 TV 端到端通过声明。Media3 交接确认 Stop、
  音频 inactive 和 AudioTrack retirement 回调，不保证异常厂商硬件 release 真正释放；GameStream
  自身释放异常或挂住则不放行下一来源。

当前选用的三架构原生核心连同 OpenSSL/Opus 已按固定源码重新构建；不再使用上游历史预编译静态库。
构建清单绑定本次真实产物与源版本，跨目录重新链接的字节不一致，因此不宣称逐字节可复现。源码恢复、
标准化构建环境、对应源码与发布打包要求见[原生构建说明](../moonlight-core/scripts/SOURCE-BUILDING.md)。

新的 TV 发布必须先由所有者在仓库外安全配置并长期保留签名身份，通过证书指纹与发布检查；
没有外部签名时只产生未签名 release 构建，不再用另一个 debug 签名伪装可持续升级发布。
历史 v0.3 下载与事实保留不变。详见[签名说明](tv-signing.md)。
