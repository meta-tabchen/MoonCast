# Unified setup and troubleshooting / 合并版使用排障

See both README language versions for roles, protocols and installation.

## First installation / 首次安装

Use the [current Release](https://github.com/meta-tabchen/MoonCast/releases/latest) (0.9 / code66). Each APK contains both phone and TV roles; choose by the **Android system ABI**, not the device's role or CPU marketing name. `arm64-v8a` is the 64-bit ARM build; `armeabi-v7a` is the 32-bit ARM build. An ARM64 processor can run a 32-bit Android system. Check the manufacturer's system information if unsure; do not assume every TV needs ARMv7. Minimum Android version is 8.0.

**On a phone:** download the matching APK, open it from your browser's downloads or file manager, and review Android's installation prompt. If Android offers an install-source permission, it applies to the app opening the APK. Review and decide yourself; MoonCast does not grant it.

**On a TV without MoonCast yet:** use a browser/file manager already available on that TV to open the Release and downloaded APK, if it supports this; alternatively copy the matching APK to a USB drive and open it with the TV's available file manager. Availability of browsers, USB storage, package installers and install permissions depends on the television and its administrator. These are possible first-install routes, not newly tested OEM compatibility. If your TV does not expose an allowed route, follow its manufacturer's instructions rather than disabling protections.

**After MoonCast is installed on the TV:** later ordinary APK transfers can use MoonCast's phone-to-TV flow below. This feature cannot bootstrap an empty TV: MoonCast must already be present and running on the receiving device.

**If installation fails:** keep the existing installation and note the exact system message. Check Android version, system ABI, complete download and available storage; a same-package update must also satisfy Android's signature and version checks. Do not uninstall or clear data as the first troubleshooting step: that removes private settings and pairing records. Current community upgrades retain the package/signature; older standalone/0.5.0 packages have different identities, as explained in the [README migration section](../README.md#community-updates-and-one-time-migration). A public test signature supports upgrade continuity, not publisher authentication. Download only from the intended Release and compare its checksum.

请使用[当前 Release](https://github.com/meta-tabchen/MoonCast/releases/latest)（0.9 / code66）。每个 APK 都含手机和电视模式，按**安卓系统 ABI**选包：`arm64-v8a` 为 64 位 ARM，`armeabi-v7a` 为 32 位 ARM。64 位处理器也可能运行 32 位系统；不确定时查厂商系统信息，不要默认电视都选 ARMv7。最低 Android 8.0。

- **手机首次安装：** 下载匹配的 APK，从浏览器下载列表或文件管理器打开，核对系统安装提示。如系统提供安装来源授权，由你自行审阅决定；授权对象是打开 APK 的应用，MoonCast 不会自动授予。
- **电视还没有 MoonCast：** 若电视已有的浏览器／文件管理器支持，可直接打开 Release 下载并打开 APK；也可将 APK 拷到 U 盘，用电视已有文件管理器打开。浏览器、U 盘、安装器和权限入口因电视／管理策略而异，这些是可选路径，不是新增厂商实测。没有允许的入口时遵循厂商说明，不要求关闭安全保护。
- **电视已安装 MoonCast：** 后续可用手机向电视传普通 APK，再由电视确认。接收端必须已安装并运行 MoonCast，不能靠这个功能完成空白电视的首次安装。
- **安装失败：** 先保留旧应用，记录系统原话，检查 Android 版本、系统 ABI、下载完整性及空间；同包升级还要满足签名和版本要求。不要先卸载或清数据，以免丢失设置和配对。当前社区版保持包名／签名，历史独立版及 0.5.0 有不同身份，见 [README 迁移说明](../README.zh-CN.md#社区版升级与旧版迁移)。公开测试签名只保证升级身份连续性，不认证发布者；只从预期 Release 下载并核对校验和。

[Android ABI reference / ABI 官方说明](https://developer.android.com/ndk/guides/abis) · [Current validation limits / 当前验证范围](release-0.9-validation.md)

## Device mode / 设备模式

Phone header: Device mode · About → Switch phone / TV mode.
TV: Settings → Device mode. Automatic uses Android TV UI mode or Leanback support;
manual Phone/TV overrides persist. Changing roles stops the sender/receiver first.
If cleanup is still pending, the app refuses to start the other role. Retry after
it stops; do not run two old/new receivers to work around a port conflict.

手机顶部「设备模式 · 关于」或电视设置「设备模式」可切换。自动模式按设备识别；
手选结果记忆。切换会中断当前会话，录屏授权不会被悄悄恢复。

## Discovery / 找不到电视

Check the receiver is actually in TV mode and running. My TV merges verified
identity/address information and capability tags, not names alone. A normal AirPlay
receiver remains selectable for casting even if APK receiving is disabled. Rescan;
if multicast is unavailable on the LAN, manually enter the receiver IP once and use
the remembered endpoint. An IP change requires correcting the saved endpoint. Guest
Wi-Fi/client isolation or VPN routing can block discovery and direct traffic.
Do not disable OS/router protections blindly; use a network you control.

先确认电视在接收模式并已启动。关闭 APK 接收不会阻止 AirPlay 发现。支持重新扫描，
局域网不转发组播时可手动输入一次 IP；IP 变更后须更新。访客网络隔离和 VPN 路由
可能导致不可达，不建议盲目关闭系统或路由器安全设置。

## APK errors / APK 安装问题

An enabled MoonCast APK receiver advertises capability and listens only on fixed
TCP 48152. A port conflict is an error, never a random-port fallback. The transfer
is unencrypted trusted-LAN HTTP. Expired, oversized, truncated or hash-mismatched
uploads fail explicitly. A selected filename is not a trust decision. Own apps
are allowed; Android still decides signer, downgrade, SDK/ABI and split compatibility.
After unknown-source permission settings return, the TV refreshes authorization
and revalidates the reviewed private file. The user must tap install again.

## Sound/video / 声画

The native audio queue includes fixes for no-audio RTP marker slots and FLUSH
sequence boundaries. They do not shorten the packet-loss window or alter PCM/NTP
alignment. A source app can still omit audio at a track boundary. Report protocol,
source app, device/version and whether sound eventually resumes; never include
private pairing keys or unredacted network logs.

DRM restrictions, OEM codecs, HDMI and source audio routes need real-device tests.
0.9 offers manual transparent-window dimming, not physical panel-off. Root/Shizuku physical panel-off remains gated pending device-specific safe-restoration evidence; optional root input is a separate feature. See [manual controls and limits](manual-controls-0.9.md).

## Downloads and platform limits / 下载与协议边界

Use the signed ARM64 or ARMv7 assets linked from the README. Choose by the device's Android ABI, not by phone versus TV: each variant contains both roles. An x86 emulator test does not establish ARM physical-device compatibility. For the current 0.9 / versionCode 66 build, fixed public test signature and actual test boundaries, see [0.9 validation](release-0.9-validation.md). The generated-media emulator checks do not establish physical phone/TV acceptance or Moonlight resolution-switch interoperability. [0.7 validation](release-0.7-validation.md) remains historical. [Community validation](community-validation.md) records historical 0.5.1 results; historical 0.5.0 results describe that older package only.

Miracast, Google Cast/Chromecast, OEM system casting and AirPlay 2 multiroom are not implemented. A manual AirPlay IP is a fallback attempt, not verified evidence that the address supports AirPlay or APK installation. APK receiving must be explicitly enabled on a capable MoonCast TV and confirmed there; GameStream still requires its protocol PIN.

请从 README 下载已签名的 ARM64 或 ARMv7 附件，按安卓系统 ABI 选择，不按手机/电视区分。
当前 0.9 / code66 的固定公开测试签名及实际验证范围见[0.9 验证记录](release-0.9-validation.md)。生成媒体的模拟器检查不代表手机／电视实机验收或 Moonlight 分辨率切换通过；[0.7 验证记录](release-0.7-validation.md)仍保留为历史。
[社区版验证](community-validation.md)保留的是历史 0.5.1 结果，不能作为本版新增真机验收。自动发现失败可在 My TV 手动输入 IP；这不代表已经验证对方支持 AirPlay，更不代表支持 APK。APK 须在支持该功能的 MoonCast 电视端开启、核对并确认。GameStream 的协议 PIN 配对仍须完成。Miracast、Chromecast、厂商系统投屏和 AirPlay 2 多房间未实现。

主题设置中，遥控器左右移动只预览；确认键应用并保存，返回或关闭会放弃未应用的预览。四种背景只用于待机，播放中的半透明设置不会替换视频画面。实际声画质量、实体设备互通和系统安装仍以验证文档的未测试边界为准。

## Language / 语言

Choose Follow system, English or 简体中文 in the phone settings or TV advanced settings. Changing language inside MoonCast asks to stop the active session before applying it. Cancel or reselect the current language leaves the session unchanged. Android 13 and later also exposes the app language in system Settings; older supported versions use MoonCast's saved choice. Unsupported system languages fall back to English.

Changing language directly in Android Settings may leave an already-running casting or file-sharing notification in its previous language until that session stops and its service is recreated. Stop and start a new session to refresh it. Live external-switch notification refresh has not been validated.

在手机设置或电视高级设置选择「跟随系统」、English 或简体中文。应用内切换语言会先确认停止当前会话；取消或重新选择当前语言不会停止。Android 13 及以上也可在系统设置中选择应用语言；较早版本使用 MoonCast 保存的选择。其他系统语言回退为英文。

直接在安卓系统设置中更换语言时，已经运行的投屏或文件分享通知可能暂时保留原语言，直到停止会话并重新启动服务。可停止后重新开始以刷新通知；系统外部切换时通知即时刷新的行为尚未验证。
