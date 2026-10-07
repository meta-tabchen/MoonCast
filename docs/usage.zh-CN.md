# MoonCast 0.2.1

Android 手机作为 GameStream Host，电视使用官方 Moonlight。专用局域网发射端，基于 Mirror 已移植的 Sunshine 协议核心，应用层重新实现。GPLv3。

## 使用

1. 手机安装 `MoonCast-0.2.1-preview.apk`，电视安装官方 [Moonlight Android](https://github.com/moonlight-stream/moonlight-android/releases)。两端连接同一局域网。
2. 手机点击「启动发射端」，允许共享**整个屏幕**。可选同步播放音频；不使用麦克风。
3. 电视发现 `MoonCast`，或手动输入手机显示的局域网 IP。
4. 电视显示四位 PIN 后，在手机的配对框输入 PIN。
5. 电视打开 `Desktop`。手机切换到要投屏的应用，电视即可显示手机屏幕。
6. 在手机或通知栏停止。非 Root 下电视断开后 Host 会结束；重新启动会申请新的授权，已配对电视无需重新配对。

建议起步设置：1080p、60 FPS、30–50 Mbps、HEVC。这些设置在 Moonlight 中选择，Host 使用实际协商结果。可增加码率和分辨率，但设备硬编码器和电视解码器决定上限；本版本不承诺任意手机支持 4K60 / 150 Mbps。

## 手机本地声音

开启「同步手机播放声音」后可选择「投屏时关闭手机声音（停止后恢复）」，默认关闭。静音只在接收端请求音频、音频捕获启动成功后生效；等待连接、只投视频、未获得音频权限和 Root 仅视频模式不会修改音量。投屏结束、系统结束共享、音频读取失败或捕获出错时恢复原媒体音量。只调整 `STREAM_MUSIC`，不修改铃声或闹钟音量，也不改变发送 PCM 的增益。

手机媒体音量设置为 0 前先保存恢复记录，进程意外退出后下次打开会尝试恢复。用户在投屏期间主动调到非零音量时，结束投屏会保留新设置。固定音量设备或不允许修改音量的系统会继续音频投屏并记录静音失败。此版本未在用户真机上验证静音时音频捕获的 ROM 行为；接收端持续有声需要实测。

## 视频黑边

默认选择「视频区域 · 固定中央 16:9」。横屏时直接截取屏幕中央最大的 16:9 区域，通过 GPU 保持比例投到接收端，无需手动框选，也不使用黑色像素检测。适用于居中播放的 16:9 视频；例如 2412×1080 手机截取中央 1920×1080，两侧各去除 246 像素。该预设不会自动识别其他比例或偏移的视频。竖屏恢复整屏。升级到本版本首次打开会选中此模式，以后记住用户选择。

原有「整个手机屏幕」「自动去黑边」「视频铺满」三个模式保留，可以在投屏中切换。iPad 比 16:9 视频更方，保留完整视频时必要的上下黑边仍会存在；铺满会裁掉部分视频内容。

0.2.1 的自动检测每 250 ms 在 GPU 中采样一个 192×128 小图，独立于新画面回调，因此暂停画面也可完成检测。连续三个稳定结果后收缩区域，连续六个结果才扩大区域以避免短暂播放器浮层引起跳变；暗场保留上次结果，旋转重置。黑色阈值容忍接近黑色的边框，少量彩色手势条不再阻止检测；采样 GL 错误显式报告，日志显示真实采样结果与区域更新。实际手机截图样本和几何单测通过，新的实际投屏效果仍需用户验收，不能当作任意播放器都能可靠识别的保证。固定视频区域模式继续可用。

## 远程操控（预览）

总开关默认关闭，投屏中可实时关闭。开启后只接收当前投屏会话的输入；裁切、铺满和屏幕旋转采用与渲染相同的坐标映射，接收端比例黑边内的点击被忽略。停止时释放触摸/按键。现有原生核心的 JNI 回调已接通，无需重新编译核心；手柄、滚轮和右键仍未实现。

- **非 Root · 无障碍**：在系统设置中启用「MoonCast 远程操控」。支持点击、单指滑动，以及 Escape 返回、Windows/Home 主页、菜单键最近任务。滑动在抬手后通过 `dispatchGesture` 执行，已提交的系统手势可能继续到结束；不支持任意键盘文字输入、持续按住或多点触摸。服务不读取窗口文字。
- **Root · 输入注入**：支持持续触摸、最多十点、相对/绝对鼠标左键拖动和基础键盘映射。Root 控制可以与普通 MediaProjection 抓屏搭配，不必启用 Root 屏幕捕获。通过单独的 uid-0 app_process 和验证双方 UID 的本地 socket 注入输入；停止或拥有者断开时取消触摸、释放按键并退出。依赖系统隐藏 API 与 ROM 权限，尚未完成用户真机功能测试。
- 使用 Root 屏幕捕获时，反控采用 Root 注入后端。

建议先在「整个手机屏幕」模式测试点击、拖动和返回，再切自动去黑边/固定区域确认点击位置。无障碍或 Root 授权失败时查看诊断日志。接收端需要配置触摸/鼠标输入；iPad 可先使用 Moonlight 的触控板模式。本版本的原生入口只转发鼠标左键，不提供右键返回。

## 范围

- 非 Root：MediaProjection → VirtualDisplay → SurfaceTexture / OpenGL ES 比例与黑边处理 → MediaCodec 的 Surface 输入 → Sunshine RTP/FEC → Moonlight。
- H.264、HEVC SDR；HEVC 仅在检测到硬编码器后对外提供。
- PIN / 证书配对、HTTP/HTTPS Server API、RTSP、UDP 视频、ENet 控制握手、FEC 都复用 Sunshine 核心。
- 一个接收端；可选鼠标左键/触摸反控，Root 支持基础键盘；手柄、滚轮和右键未实现。
- Android 10+ 可选 AudioPlaybackCapture → 48 kHz stereo → Opus。第三方应用必须允许被捕获；Root 首版仅视频，音频为静音流。
- 不登录账号，运行时无云端依赖；默认端口为 TCP 47989/47984/48010 与 UDP 47998/47999/48000。自动发现需要局域网允许 mDNS，失败可手动加 IP。
- 画质仍属于有损视频编码，通常为 YUV 4:2:0。没有实现数学无损、原文件播放、HDR、AV1 或受保护层捕获。高码率不等于严格无损。

## Root 实验后端

勾选「Root 免录屏确认」后启动，`su` 运行 app_process 守护进程，使用 uid 0 的系统 Context 创建镜像虚拟显示，经相同 GPU 处理后进入硬编码器。APK 通过随机名称的本地 Unix socket 发送 PIN / 显示模式 / 停止命令，只接受 uid 0 的对端。连接丢失时守护进程退出，防止 APK 被关闭后留存后台发射端。不会修改分辨率、锁屏设置或系统分区。

Root 后端依赖 ROM 的特权 DisplayManager 行为与隐藏 API；**未通过实机验证前应视为实验实现**。本版本没有保证开机自启、息屏持续显示、免锁屏或 secure layer 捕获。失败时切换非 Root 模式。

## 编译（使用已固定的原生核心）

JDK 17、Android SDK Platform 35、Build Tools 35.0.0。源码包内附带已校验的原生运行库。需要重新提取时：

```sh
python scripts/prepare_native.py /path/to/mirror-v0.0.34.apk
./gradlew :app:assembleDebug :app:lintDebug
```

不传 APK 路径时脚本从原项目固定版本下载并校验 SHA-256；这些下载仅在开发机器发生。Windows 使用 `gradlew.bat`，并在 `local.properties` 中配置 `sdk.dir`。

首版 release 构建仍使用本地 debug 签名，仅作为 preview。正式分发应配置自己的 release keystore。

## 从源码重建原生核心

`../native/sunshine` 是对应版本的完整 C/C++ 源码与固定依赖。建议 Linux 构建环境，需要 Android NDK `27.0.12077973`、CMake `3.31.1`、Python、Perl、GNU make 和 patch：

```sh
./gradlew :app:assembleDebug -PnativeFromSource
```

CMake 从源代码下载并构建 Boost 1.86.0 和 OpenSSL 3.5.5。该模式使用项目内源码而非 `jniLibs`。本次若只验证了预编译核心的构建，不能把它等同于完整 NDK 重编译通过；实际验证结果见 [VALIDATION.md](../VALIDATION.md)。

## 生命周期与调试

Host 运行在 APK 的独立 `:host` 进程。普通投屏只创建一个 VirtualDisplay、不会重复使用 Android 14+ 的授权 token。停止时释放虚拟显示、播放音频捕获、MediaProjection、mDNS、唤醒锁，再终止 Host 进程，确保原生端口和线程全部结束；手机 UI 不被终止。配对证书保存在应用私有目录，不写入日志，也不备份。

内置测试画面包含文本、细线、RGB 色块、灰阶、计时器和运动圆点，可用来观察清晰度、色彩和大致延迟。计时器对比只是估算，不是专业延迟测试。

```sh
adb logcat -s MoonCast Sunshine MoonCastRoot
```

若黑屏，检查是否授予整个屏幕、是否播放 DRM/FLAG_SECURE 内容、设备编码器是否支持 Moonlight 请求的尺寸/码率。竖屏画面在横屏电视上保留比例和黑边，不裁切或拉伸。

原生核心当前沿用上游实现与约束，包括最低编码帧率、编码 Profile/Level 配置、未对每一种厂商编码器完成探测。先用 1080p60 验证，再增加参数。原生代码没有为本项目伪造新的能力声明。

协议和 Android 规则参考：[Mirror 源码](https://github.com/jqssun/android-display-mirror)、[Sunshine 源码](https://github.com/LizardByte/Sunshine)、[Android MediaProjection 文档](https://developer.android.com/media/grow/media-projection)。
