# Unified setup and troubleshooting / 合并版使用排障

See both README language versions for roles, protocols and installation.

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
Phone panel-off/root input features remain experimental and opt-in.

## Downloads and platform limits / 下载与协议边界

Use the signed ARM64 or ARMv7 assets linked from the README. Choose by the device's Android ABI, not by phone versus TV: each variant contains both roles. An x86 emulator test does not establish ARM physical-device compatibility. For the exact community build, signature and device-test results, see [community validation](community-validation.md); historical 0.5.0 results describe that older package only.

Miracast, Google Cast/Chromecast, OEM system casting and AirPlay 2 multiroom are not implemented. A manual AirPlay IP is a fallback attempt, not verified evidence that the address supports AirPlay or APK installation. APK receiving must be explicitly enabled on a capable MoonCast TV and confirmed there; GameStream still requires its protocol PIN.

请从 README 下载已签名的 ARM64 或 ARMv7 附件，按安卓系统 ABI 选择，不按手机/电视区分。自动发现失败可在 My TV 手动输入 IP；这不代表已经验证对方支持 AirPlay，更不代表支持 APK。APK 须在支持该功能的 MoonCast 电视端开启、核对并确认。GameStream 的协议 PIN 配对仍须完成。Miracast、Chromecast、厂商系统投屏和 AirPlay 2 多房间未实现。

主题设置中，遥控器左右移动只预览；确认键应用并保存，返回或关闭会放弃未应用的预览。四种背景只用于待机，播放中的半透明设置不会替换视频画面。实际声画质量、实体设备互通和系统安装仍以验证文档的未测试边界为准。

## Language / 语言

Choose Follow system, English or 简体中文 in the phone settings or TV advanced settings. Changing language inside MoonCast asks to stop the active session before applying it. Cancel or reselect the current language leaves the session unchanged. Android 13 and later also exposes the app language in system Settings; older supported versions use MoonCast's saved choice. Unsupported system languages fall back to English.

Changing language directly in Android Settings may leave an already-running casting or file-sharing notification in its previous language until that session stops and its service is recreated. Stop and start a new session to refresh it. Live external-switch notification refresh has not been validated.

在手机设置或电视高级设置选择「跟随系统」、English 或简体中文。应用内切换语言会先确认停止当前会话；取消或重新选择当前语言不会停止。Android 13 及以上也可在系统设置中选择应用语言；较早版本使用 MoonCast 保存的选择。其他系统语言回退为英文。

直接在安卓系统设置中更换语言时，已经运行的投屏或文件分享通知可能暂时保留原语言，直到停止会话并重新启动服务。可停止后重新开始以刷新通知；系统外部切换时通知即时刷新的行为尚未验证。
