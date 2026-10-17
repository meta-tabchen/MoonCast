MoonCast 0.9 · versionCode 66 · com.mooncast.community

## 中文

- 新增可拖动、贴边收起的悬浮控制，离开 MoonCast 后可暂停/恢复投屏、恢复手机亮度、停止投屏；通知栏保留备用操作。默认保持手机亮屏，开始或重新连接不自动关屏。
- 新增明确命名的“调暗手机屏幕”：透明窗口请求系统最低亮度，提示首帧就绪后保留三秒取消时间，首次触摸恢复并吞掉完整手势，之后保持亮屏。不会修改系统保存的亮度。此功能不是物理关屏，实际最低亮度与省电效果取决于设备。
- 统一手机/电视/发现名称入口，忙碌会话的改名延后到下次启动；保留 UUID、证书与配对。Moonlight 应用卡片标题与设备身份分别处理。
- GameStream 保留客户端请求的分辨率和帧率，显示请求/编码/解码状态，降低分辨率后重新计算采集尺寸。电视物理 4K 输出与投屏分辨率分别显示。
- 修正接收端 JNI 原生线程的附着所有权，云端重建实际原生库；增加有界、仅事件码和数值的本地生命周期/解码/退出诊断。诊断异常不应中断停止清理。

### 验证范围与重要限制

使用步骤见[手动悬浮控制说明](https://github.com/meta-tabchen/MoonCast/blob/v0.9/docs/manual-controls-0.9.md)。完整证据与各轮失败见 [0.9 验证记录](https://github.com/meta-tabchen/MoonCast/blob/v0.9/docs/release-0.9-validation.md)。构建、签名和源码核验结果以随包 RELEASE-PROVENANCE.json 为准。

真实 AirPlay 回环已经覆盖原生握手、JNI、MediaCodec、不同颜色像素、停止及第二会话。离屏 EGL 测试发现复用 Surface 时消费者等待信号的问题，fresh Surface/主动采样对照能继续取得第二轮像素；该测试现象尚不能归因于用户电视，不能称电视退出已根治。普通 AOSP29 模拟器上的真实 TvActivity/TextureView 已通过两次原生会话、不同颜色像素、持续解码和 Activity 清理；这仍不是用户电视的实物验收。

**Root/Shizuku 物理关屏保持安全门槛，未开放为通用功能。** 隐藏系统电源接口的返回不能证明手机真的恢复；手机/OEM/权限撤销/进程退出故障必须有实物验证。调暗功能不替代这项尚未完成的验收。

软件模拟器的 Sunshine 编码器在 640×360 Surface 输入配置即不支持，因此双 Moonlight 客户端和真实 720p/1080p/4K 切换未取得该模拟器通过证据；不能把参数单元测试当实机流媒体通过。目标手机、Douyin 连续运行、耗电与用户电视仍需对应实物证据。历史 Mac OriginalMediaServerTest 连接重置失败保留，不因云端其它测试通过而抹去。

## English

- Draggable edge-collapsed casting controls remain available over another application, with real pause/resume, restore and Stop plus notification fallback. The phone stays lit on start/reconnect.
- Explicit “Dim phone screen” requests minimum window brightness with transparent pixels, a three-second countdown after the notice frame is ready and full-gesture touch restoration. Saved global brightness is unchanged. Minimum luminance and savings remain device-dependent; this is not physical panel-off.
- One device-name setting preserves persistent identities and pairing, with pending application during busy sessions. GameStream honors client resolution/FPS and reports negotiated/encoded/decoded status separately from physical TV output.
- Rebuilt native receiver libraries correct JNI thread-attachment ownership. Bounded private lifecycle/decoder diagnostics avoid content, URLs, addresses and pairing secrets; nothing uploads automatically.

See the linked validation record for exact checks, failed comparisons and hardware limits. Physical Root/Shizuku panel-off stays gated until device-specific restoration and fault-injection evidence exists. Actual TvActivity/TextureView passed two generated-media native AirPlay sessions, distinct pixels, sustained decode and Activity cleanup on ordinary AOSP29. This does not establish target-TV acceptance; an off-screen EGL consumer failure is not a diagnosed TV crash. The software emulator cannot configure the unchanged Sunshine Surface-input encoder, so it does not pass multi-client/resolution stream tests. Historical Mac test failures remain documented.

## Downloads / 下载

Each APK includes phone and TV roles. Choose ARM64 or ARMv7 for your Android system. Existing package and public community test signer are preserved, enabling static update compatibility with 0.8. Certificate SHA-256: `290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2`.

This intentionally public test identity provides upgrade continuity, not maintainer authentication. Source, notices, exact native/source provenance and SHA256SUMS accompany both APKs. The curated source excludes the app-signing PKCS12; external WebRTC source/DEPS/build mappings are included, not a complete offline SDK checkout or independently rebuilt WebRTC SDK. Older tags/assets remain unchanged.
