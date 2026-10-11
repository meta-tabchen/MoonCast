# Android system casting / 安卓系统投屏

Reviewed against the implementation and primary sources on **2026-10-11**. This is a
compatibility and implementation assessment, not a physical-device acceptance result.

## 中文

### 本版能做什么

MoonCast TV 当前实现 **AirPlay 接收与 DLNA 媒体播放**。DLNA 用于把视频或音频地址交给电视播放，
并不等于安卓系统的屏幕镜像协议。手机系统投屏列表可能使用 Miracast、Google Cast、Lelink 等厂商集成协议；
没有具体手机型号和入口信息，不能仅凭“安卓”确定是哪一种。

本版修复了两个真实的 DLNA 发现问题：连续搜索不同目标不再被同一个 100 ms 全局间隔丢弃；
合法的较大 MX 等待值会被限制到安全范围，而不是直接拒绝。仍保留同一控制端/端口/目标的重复请求限制、
有界全局响应预算、同子网限制与有界随机响应等待。UPnP 的设备架构允许缩短 MX，并要求处理大于 5 的值。
这项修复**不会让不支持 DLNA 的安卓系统选择器新增 MoonCast 条目**。
参见 [UPnP Device Architecture 1.1，第 1.3.3 节](https://upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf)。

### 乐播 / Lelink：安卓系统入口的真实可行路线

**安卓系统投屏不等于 Miracast。** 手机厂商可以在系统入口内置 Lelink 发送能力，电视上的普通接收 APK
就有机会被该入口发现并接收镜像；这条基于厂商协议的路线不应被下文的 Miracast 特权限制一概否定。
已有一手证据：

- 小米：乐播的 [MIUI 11 合作说明](https://www.lebo.cn/news/AboutNewsContent?id=467)明确说明无需另装手机 APP，
  从系统控制菜单即可投屏；[2020 年 MIUI 12 实例](https://www.lebo.cn/news/AboutNewsContent?id=569)
  进一步说明，在原本不兼容的电视上安装乐播 TV 版后可使用全能投屏。
- OPPO：乐播的 [2020 年合作说明](https://www.lebo.cn/news/AboutNewsContent?id=513)说明 ColorOS 集成发送 SDK；
  [OPPO 官方帮助](https://gcss-robot.oppo.com/online/preview.html?lang=zh&sign=Y2FyZElkPTkwJnJvYm90SWQ9MSZ0ZW5hbnRDb2RlPW9wcG8)
  特别提及 ColorOS 7.2 及以上对内置乐播能力设备的支持。
- vivo：[2020 年 vivo 官方说明](https://www.vivo.com.cn/brand/news/detail?id=507)确认智慧投屏接入乐播 SDK。
- 华为不能直接类推：[官方帮助](https://consumer.huawei.com/cn/support/content/zh-cn15912586/)针对所列
  HarmonyOS 2.x–4.x 版本区分 Miracast 音画镜像与 DLNA 媒体推送，没有证明这些系统入口内置 Lelink 镜像。

这些资料证明实现路线存在，不是 2026 年所有地区、手机型号、系统版本和接收 SDK 的兼容保证。
需要记录具体手机入口和版本，再按该组合验证发现、握手及音画；不能仅因设备能被搜到便认定支持屏幕镜像。

#### 能否保留 MoonCast 裁切与音频处理

[乐播接收端 API](https://cloud.lebo.cn/document/96bb7d1acda22ae5.html) §5.9 提供关闭内置播放器、
通过 `IFrameCallback` 接收 H.264 裸流与 PCM 音频的接口。这为内置接收、由 MoonCast 自己解码、裁切及处理音频
提供了具体可评估入口；并非只能启动独立 Activity。需取得对应版本 SDK 后验证帧格式、时间戳、旋转、缓冲及退出生命周期。
同页 §3.2 的 `setMirrorSurfaceType` 只是选择 SDK 渲染类型；§2.1 显示模式和 §2.2 播放器选择仅适用于媒体推送，
不能拿它们保证镜像裁切或任意 Surface 接管。公开文档不等于当前 APK 已集成或实机已通过。

#### 准入、授权和分发是接入前条件

[接收 SDK 集成](https://cloud.lebo.cn/document/8ba0e1377798c440.html)使用 APPID/APPSECRET；
[License 认证](https://cloud.lebo.cn/document/6c3bd08f6ffb1890.html)还涉及云端授权和稳定设备标识 `tsn`，
有渠道、数量及有效期等状态。不能把能下载 SDK、个人能注册网站或 Android 运行时权限等同于接收 SDK 使用及分发权。

接收 SDK 所链接的 [2024-05-01 软件许可与服务协议](https://cloud.lebo.cn/document/9f0ac07af3f46788.html)
§2.1 要求注册并认证为企业开发者；§4.1 限制转让/再许可，§4.5(5)–(6) 对提供软件组成部分、二进制及源码
要求事先书面许可。个人项目是否可获例外授权、价格和具体渠道许可需厂商确认。
本仓库使用 GPLv3，专有 SDK 与 MoonCast/UxPlay 组合分发的相容性也必须单独审查；
[GNU 官方 FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#GPLIncompatibleLibs)说明不相容库的链接需要相应版权人的例外许可。
不能先打包公开发布，再靠安装后的授权弹窗补齐；独立包装层或可选构建也不会自动取得分发权。
这是待取得许可和验证的集成路线，不能据此断言技术上不可行，也不能承诺当前即可发布。

本轮没有找到许可明确、完整实现且已验证 OEM 系统镜像兼容的开源 Lelink 接收端，这是检索结果而非不存在的证明。
[UxPlay](https://github.com/FDH2/UxPlay)、[RPiPlay](https://github.com/FD-/RPiPlay)提供 AirPlay 镜像，
下述 MiracleCast 面向 Miracast；公开 SDK 调用示例或包装层不等于其底层协议已开源。

### Miracast 的实际边界

本节仅描述 Miracast/WFD 路线，不适用于否定上文的 OEM Lelink 系统集成。

- Android 14 / API 34 起，`WifiP2pManager.setWfdInfo` 已是公开 API，不能笼统地说它一直是隐藏 API。
  但它仍要求 `CONFIGURE_WIFI_DISPLAY`。当前 AOSP 将该权限定义为 `signature|knownSigner`，
  Wi-Fi 服务按调用者 UID 检查权限；普通侧载 APK 不能靠反射或普通运行时授权获得它。
  Wi-Fi Direct 硬件存在、附近设备权限获批，也不能替代这项权限。
  [官方 API](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager)
  · [AOSP 权限定义](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)
  · [服务端检查](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/service/java/com/android/server/wifi/p2p/WifiP2pServiceImpl.java)
- 真正的接收端还需要 Wi-Fi Direct/WFD 连接管理、RTSP 协商、RTP/RTCP、传输流解析、音视频解码和断线恢复。
  受保护内容还依赖相应 HDCP 能力与密钥，不能通过添加普通播放器补齐。
  [接收端实现要求](https://learn.microsoft.com/en-us/windows-hardware/design/device-experiences/wireless-projection-receiver-manufacturers)
  · [受保护内容与 HDCP](https://learn.microsoft.com/en-us/windows-hardware/design/device-experiences/wireless-projection-understanding)
- 电视自带投屏能被发现，说明电视已有可用的原生接收能力，**不代表 MoonCast APK 可以调用或接管它**。
  可落地的方向是验证该电视的正式 OEM 接口，或与厂商合作预装、授权签名和底层接收集成。
  如果只能启动电视自带接收器，必须标明这是转到原生接收功能；MoonCast 的裁切和音频校准不会自动应用到它。
  Root、框架修改和定制固件属于另一条需要单独授权、适配和实机验证的路径。

### Google Cast 的实际边界

Cast Connect 建立在设备已有的 Cast 基础设施之上，把配置过的发送端应用接到 Android TV 应用。
它需要 Cast 应用 ID、包名关联和发送端配置；侧载开发还涉及测试设备注册。
它不是把任意 APK 变成通用 Google Cast 系统镜像接收器的库。
[官方说明](https://developers.google.com/cast/docs/android_tv_receiver)
· [配置与侧载要求](https://developers.google.com/cast/docs/android_tv_receiver/core_features)

Chromium Open Screen 有真实的开源镜像接收实现，但其独立接收端测试需要发送端明确信任开发者证书。
这适合受控实验，不能据此声称未修改的安卓系统投屏已兼容。
只广播 `_googlecast._tcp` 或伪装设备名不构成协议支持。
[独立接收端说明](https://chromium.googlesource.com/openscreen.git/+/refs/heads/chromium/8032/cast/docs/USING.md)
· [身份验证要求](https://chromium.googlesource.com/openscreen/+/refs/heads/main/cast/protocol/streaming_session_protocol.md)

### 下一步需要验证

1. 记录手机品牌/型号、Android/系统版本及具体投屏入口；记录电视品牌/型号、固件和原生接收器名称。
2. 优先核实目标手机的 Lelink 系统集成及可授权接收 SDK；若实际使用 Miracast，再查 WFD/P2P 能力与 OEM 接口。
3. 获得可用接口及许可后做集成原型，验证发现、声音/画面、裁切、音频处理、旋转、退出、重连及网络中断。
4. 尚未取得可用接收集成时，继续提供真实的 AirPlay/DLNA 功能；伴随手机应用的 MediaProjection 路径可另行开发，
   但不能当作原生 Miracast/Google Cast 支持。

本次查到的开源候选并非成熟的通用普通 APK 方案：
[MiracleCast](https://github.com/albfan/miraclecast) 面向 Linux/systemd/wpa_supplicant；
[Intel WDS](https://github.com/intel/wds) 是已停止维护、需要另配网络和媒体实现的 Linux 组件。
两者使用 LGPL 系列许可；移植仍需逐项保留许可、来源和再分发义务，且不能解除 Android 权限限制。
任何厂商 SDK、受保护内容、签名或认证要求都必须单独核实，不能把开源协议代码视为相关授权。

### TCL Q9K：公开资料能确认到哪里

- TCL 中国官网的 [2024 年新品发布页](https://www.tcl.com/cn/zh/event/x11h)列出 Q9K 系列。
  这不足以确认 75Q9K 的 Android 版本、当前固件、原生接收器包名或协议；不能套用其他地区
  Google TV / QM9K 的说明书。本次未找到可公开核验的 75Q9K 专用接收接口或完整协议表。
- [TCL 开发者平台](https://ap-developer.tclyun.com/)明确面向商用/工程电视，列举 G62E、G90E、
  G50E、G60E 等机型，提供签名和 OTA 等服务；其[技术文档入口](https://ap-developer.tclyun.com/doc?id=38)
  要求登录。公开内容没有证明这些接口适用于消费级 75Q9K，不能据此调用猜测的系统服务或宣称已有 OEM 集成。
- [乐播接收端 SDK 功能表](https://cloud.lebo.cn/document/70c0a1c537e4fe69.html)列出 DLNA、Lelink 和苹果设备接收，
  镜像项列出 Lelink 和苹果设备。结合上文 OEM 发送端集成证据，它是安卓系统入口兼容的真实候选路线；
  无需先把它实现为 Miracast 或 Google Cast。但这些公开资料没有确认 75Q9K 当前固件的原生接收器，
  也不代表 MoonCast 已获 SDK 授权或已经通过该机型实测。

## English summary

MoonCast TV currently receives AirPlay and DLNA media. Android's system casting menu
may use Miracast, Google Cast, Lelink, or another OEM-integrated protocol. The phone model and exact casting
entry point are needed to identify which. The TV's built-in receiver being visible
does not prove this APK can access that privileged implementation.

The DLNA patch fixes back-to-back search-target suppression and excessive rejection
of valid MX values. It retains bounded duplicate filtering, a global response budget,
subnet restrictions, and randomized scheduling. It does **not** add Android system
mirroring support. The portable suite exercises real loopback UDP discovery and
HTTP/SOAP; these tests are not phone/TV interoperability evidence.

Miracast is a full WFD/P2P, RTSP, RTP/RTCP and media-receiver integration. Modern Android
exposes `setWfdInfo` publicly from API 34 but still enforces the privileged
`CONFIGURE_WIFI_DISPLAY` permission in the system service. Reflection does not bypass
that check. An OEM-approved API/signing and integration route can be viable;
root/custom firmware requires separate authorization and device-specific work.
The open-source Linux candidates above do not provide a verified ordinary-APK solution.

Cast Connect integrates a native app with existing Cast infrastructure and configured
senders. It does not implement a general system-mirroring receiver. Open Screen's
standalone receiver requires explicitly trusted developer credentials on the sender.
Neither a discovery advertisement nor a player implementation establishes compatibility,
licensing, certification, or protected-content authorization.

Next: identify the phone and TV/firmware, verify the actual protocol and OEM interfaces,
then build and test the supported integration on those devices. Launching the TV's own
receiver, if available through an authorized interface, must be labeled as delegation;
MoonCast's framing and audio calibration would not automatically apply there.

### TCL Q9K public-source check

TCL's [China 2024 launch page](https://www.tcl.com/cn/zh/event/x11h) identifies the Q9K
series, but does not establish the 75Q9K's current Android/firmware version, receiver
package, or supported protocols. Other regional Google TV/QM9K manuals are not proof.
The [TCL developer portal](https://ap-developer.tclyun.com/) targets commercial and
engineering TVs; its [API documents require login](https://ap-developer.tclyun.com/doc?id=38).
No public evidence reviewed establishes consumer-75Q9K applicability or a supported
receiver handoff API.

### OEM Lelink system casting is a viable integration route

The Miracast restrictions above do not rule out OEM system pickers with a built-in
Lelink sender. Lebo documents [MIUI system integration](https://www.lebo.cn/news/AboutNewsContent?id=467)
and a [MIUI 12 example that works after installing its TV APK](https://www.lebo.cn/news/AboutNewsContent?id=569).
[OPPO integration](https://www.lebo.cn/news/AboutNewsContent?id=513) and
[vivo's own announcement](https://www.vivo.com.cn/brand/news/detail?id=507) provide
additional primary evidence. These historical examples establish a real route, not
a current all-model/version compatibility matrix. Huawei's
[model/version-scoped guidance](https://consumer.huawei.com/cn/support/content/zh-cn15912586/)
instead distinguishes Miracast mirroring from DLNA media; do not generalize one OEM's
Lelink support to all Android system menus.

The [receiver API](https://cloud.lebo.cn/document/96bb7d1acda22ae5.html), section 5.9,
documents disabling its player and receiving H.264/PCM through `IFrameCallback`.
This is a concrete candidate for MoonCast-owned decoding, cropping and audio processing,
subject to version, timestamps, rotation, lifecycle and device testing. Surface-type
selection is not arbitrary Surface ownership; the documented media display/player
selectors explicitly do not establish mirroring controls.

The [receiver integration](https://cloud.lebo.cn/document/8ba0e1377798c440.html) and
[license-authentication guide](https://cloud.lebo.cn/document/6c3bd08f6ffb1890.html)
require credentials and cloud/device authorization. The receiver-linked
[2024-05-01 SDK license](https://cloud.lebo.cn/document/9f0ac07af3f46788.html), sections
2.1 and 4.1/4.5, requires enterprise-developer certification and restricts transfer,
sublicensing and redistribution without written permission. Individual eligibility,
distribution terms and compatibility with MoonCast/UxPlay's GPLv3 obligations require
separate clearance before bundling; see the [GNU FAQ on incompatible libraries](https://www.gnu.org/licenses/gpl-faq.en.html#GPLIncompatibleLibs).
Android runtime consent or an optional wrapper cannot supply those rights.
No complete, license-clear open-source Lelink receiver with verified OEM system-mirroring
compatibility was found in this review; that is a search finding, not proof none exists.
AirPlay/Miracast projects and wrappers around proprietary SDKs are not such evidence.
No reviewed evidence establishes that this Q9K firmware uses Lebo or that the current
MoonCast APK has integrated it.
