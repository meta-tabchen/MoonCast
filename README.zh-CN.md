# MoonCast · 一个 APP，两种模式

[English](README.md) · [使用与排障](docs/unified-guide.md) · [构建与原生库隔离](docs/unified-build.md) · [验证范围](docs/unified-architecture-validation.md)

0.5 是手机发送端与电视接收端合并的实验版。手机和电视安装同一个应用：Android TV / Leanback 设备默认进入接收模式，普通手机默认进入发送模式。设置里可手动切换并记住选择。切换时先停止旧模式，不支持同时发送和接收。

## 下载 0.5.0 合并预览版

[版本说明与全部附件](https://github.com/meta-tabchen/MoonCast/releases/tag/unified-v0.5.0-preview) · [ARM64 安装包](https://github.com/meta-tabchen/MoonCast/releases/download/unified-v0.5.0-preview/MoonCast-0.5.0-unified-arm64-v8a.apk) · [ARMv7 安装包](https://github.com/meta-tabchen/MoonCast/releases/download/unified-v0.5.0-preview/MoonCast-0.5.0-unified-armeabi-v7a.apk) · [完整对应源码](https://github.com/meta-tabchen/MoonCast/releases/download/unified-v0.5.0-preview/MoonCast-0.5.0-unified-complete-source.zip) · [SHA256SUMS 校验和](https://github.com/meta-tabchen/MoonCast/releases/download/unified-v0.5.0-preview/SHA256SUMS)

64 位 ARM 安卓系统选 ARM64，32 位 ARM 安卓系统选 ARMv7；以系统实际支持的 ABI 为准，不只看处理器宣传名称。两个安装包功能相同，均包含手机和电视模式，每台设备只选一个安装。下载附件是已签名的实验预览包；CI 生成的 unsigned 验证包不能直接作为安装版使用。本次不发布 x86 安装包。

统一版源码在 `unified/android-0.5.0` 分支。Git 克隆后先运行 `python3 scripts/restore_release_inputs.py` 恢复固定构建输入，再按[构建说明](docs/unified-build.md)操作；完整源 ZIP 已含这些依赖，不包含私有签名密钥。

## 两台设备怎么配合

- 安卓手机 → AirPlay：手机选 AirPlay，在统一「My TV」设备列表选择电视，再开始分享并确认系统录屏授权。接收端可以是 MoonCast 电视模式或兼容的第三方 AirPlay 接收器。
- 安卓手机 → Moonlight：手机选 Moonlight 并启动主机，电视模式或官方 Moonlight 客户端连接手机，按提示完成 GameStream PIN 配对。
- iPhone / iPad → 电视：在苹果系统的屏幕镜像或媒体输出列表选 MoonCast。
- DLNA：兼容控制端把支持的媒体地址推给 MoonCast 电视模式。

My TV 统一显示设备及能力。普通 AirPlay 电视不等于能收 APK。取消配对码指的是 APK 传输；GameStream 自身的 PIN 安全配对仍保留。

## 安装与首次使用

1. 选适合 CPU 的独立 APK，最低 Android 8.0。不同 ABI 的 APK 是同一应用的不同构建，每个都能独立安装使用，不需要再安装第二个组件。
2. 两台设备连接同一个可信局域网，网络须允许设备互相访问。电视先开接收模式；运行新版前停止旧版接收服务，避免占端口。
3. 手机按上面的协议流程操作。录屏、声音、远程输入权限分别选择，不会自动开启高级权限。
4. 电视设置可选月湖、极光、远山、午夜四种待机背景，记住上次选择。播放时设置面板半透明，待机背景不会盖住视频。

## 手机发送 APK 到电视

电视开启「安装手机 APK」，手机选择 `.apk` → My TV 选择支持 APK 的电视 → 上传。固定端口为 TCP 48152，不需要额外传输配对码。电视核对包信息并明确确认后，才打开 Android 系统安装器。

首次允许「安装未知应用」必须由你在系统设置操作。授权回来无需重新上传，在保留的审核页面再次确认即可。不会静默安装，也不能绕过 Android 的签名、降级、设备兼容或分包要求；扩展名为 APK 不代表任意文件都能成功安装。

## 重要边界

- APK 使用未加密 HTTP，仅限可信局域网，不能暴露公网。仍保留文件大小、并发、速率、超时、私有路径、哈希、解析及 FileProvider 限制。
- 本项目未实现 Miracast、Google Cast/Chromecast、厂商系统投屏协议或 AirPlay 2 多房间；GameStream 不进入 Android 系统投屏列表。
- DRM 视频、禁止录屏或禁止音频捕获的应用可能黑屏或无声音。屏幕镜像有损，不宣称原画无损或假 4K。
- Root / Shizuku / 无障碍控制仍为可选功能，不会自动授权，不改路由器或系统安全设置。
- 编译、主机测试和模拟器测试不等于所有手机电视实机兼容。发布时须列出实际通过及未验证项目。

## 从旧版迁移

新独立包名是 `com.mooncast.unified.preview`，桌面只显示 MoonCast，设置「关于」显示版本与包名。它与旧手机版/电视版共存，不覆盖旧签名，也不自动搬运旧配对密钥、设置及权限。实验签名不构成正式版本后续覆盖升级保证。验证好之前可以保留旧版，但同一时间只运行一个接收器。

项目按 GPLv3 提供对应源码。完整源包包含发送/接收依赖源、补丁、原生库校验和与构建方法。第三方许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，背景来源见 [主题说明](docs/tv-themes.md)。GitHub 发布需同步 APK、完整对应源码和 SHA256SUMS，不能只放截图或源码地址。
