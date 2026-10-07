# 0.2.2 发布验证

日期：2026-10-07（Asia/Hong_Kong）。versionCode 7、versionName 0.2.2。

- 新增英文默认和中文界面、配对/状态/通知资源，Android 单应用语言声明；较长选项可换行，版本号读取构建元数据。
- `assembleRelease` / `lintDebug` 通过：0 errors, 15 warnings。Root 隐藏 API、JNI 动态库加载、API 属性和依赖内部代码相关 warning 仍保留。
- 纯 Java 几何、裁切稳定性、反控坐标、音量恢复测试通过；两种语言资源键一致，文档相对链接存在。
- APK 含三种 ABI 的九个固定原生库，JNI/原生 SHA 校验通过。APK v2 签名与 zipalign 检查通过。
- 当前未连接 Android 设备，没有执行 0.2.2 真机安装、英文视觉布局或投屏回归；用户之前反馈对应 0.2.0。
- 当前仍为 debug 密钥签名的 preview，未配置正式发布签名；底层诊断并非完全国际化。
- APK 大小：14821713 bytes；SHA-256：`b87d64fcf9b8876a07b6cdb5e80f47ae95a8b26d1303b214b835b3fa7a05f93a`。

以下为此前开发记录，不应把旧版的设备测试当作 0.2.2 已测试的证明。

# 0.2.1 发布验证

日期：2026-10-07（Asia/Hong_Kong）。versionCode 6、versionName 0.2.1。

- 新增英文默认和中文界面、配对/状态/通知资源，Android 单应用语言声明；较长选项可换行，版本号读取构建元数据。
- `assembleRelease` / `lintDebug` 通过：0 errors, 15 warnings。Root 隐藏 API、JNI 动态库加载、API 属性和依赖内部代码相关 warning 仍保留。
- 纯 Java 几何、裁切稳定性、反控坐标、音量恢复测试通过；两种语言资源键一致，文档相对链接存在。
- APK 含三种 ABI 的九个固定原生库，JNI/原生 SHA 校验通过。APK v2 签名与 zipalign 检查通过。
- 当前未连接 Android 设备，没有执行 0.2.1 真机安装、英文视觉布局或投屏回归；用户之前反馈对应 0.2.0。
- 当前仍为 debug 密钥签名的 preview，未配置正式发布签名；底层诊断并非完全国际化。
- APK 大小：14821561 bytes；SHA-256：`9e4c53c686967770b9940586a2b135877dc30153fd2e08a6b89b83d83629b241`。

以下为此前开发记录，不应把旧版的设备测试当作 0.2.1 已测试的证明。

# 验证记录

日期：2026-10-07（Asia/Hong_Kong）。当前版本：0.2.0 preview；下方历史真机 GPU 记录为 0.1.2。

## 0.2.0 当前状态

- 用户在真机自行试用后反馈「貌似没啥问题」，并要求托管代码。该反馈是用户试用结果，没有逐项提供自动去黑边、Root/非 Root 反控、键盘或音频的专项测试记录，仍保留下面的验证范围。
- 已安装到 PGP110 真机：versionCode 5、versionName 0.2.0，并打开新版界面。用户要求自己进行真机功能验收；没有替用户操作无障碍或 Root 授权。
- `assembleRelease` / `lintDebug` 通过，Lint 0 errors、15 warnings；v2 APK 签名、zipalign 与原生 JNI ABI 校验通过。
- Java 几何单测通过：实际手机截图小样本、接近黑色的边框、彩色手势条、短暂浮层/暗场稳定性、裁切/铺满反控坐标、黑边点击过滤和竖屏回退。
- 自动检测改为独立定时采样，并补充 GL 错误及采样结果日志。真实投屏中的黑边改善尚待用户验收，未声称原问题已被实际解决。
- 反控 JNI 入口、非 Root 无障碍服务、Root 输入代理、当前会话开关和视频区域坐标映射均已实现并通过编译；实际点击、拖动、键盘、按键释放、Root 授权和 ROM 兼容性尚未验证。非 Root 滑动在抬手后执行，已提交的系统手势可能继续到结束。
- 已给 Android GPU 测试增加暂停单帧用例，但 0.2.0 未运行该设备测试；历史 0.1.2 GPU 通过记录不能作为新定时采样实现通过的证据。
- 起初新增 MuMu 实例 2、3，随后遵从用户要求停止模拟器测试；两台新增实例均已关机，原有实例未修改。未删除虚拟机数据。
- APK SHA-256：`c0bf3608edc5ca4e34630f2d12a2ad093f625b3b3758655a3b5b74e4fb6d53a5`。

## 已通过

- JDK 17 / Gradle 8.9 / AGP 8.7.3 / Android SDK 35：`assembleDebug`、`assembleRelease`、`lintDebug`。
- Android Lint：0 errors。11 warnings，包括 Root 隐藏 API、app_process 的绝对路径加载、目标 SDK 35 和依赖库内部代码；没有把 warning 计作 runtime 验证。
- APK Signature Scheme v2：`apksigner verify --verbose` 通过。当前签名为本地 debug / preview 签名。
- `zipalign -c -v 4` 通过；原生库按压缩打包、安装时提取。
- 官方固定版本 APK SHA-256 校验通过，来源与每个原生库散列见 `native/prebuilt-manifest.json`。
- 最终 APK 含 arm64-v8a、armeabi-v7a、x86_64 的 Sunshine / OpenSSL 共 9 个原生库；每个架构的 9 个 Java native 入口均能在 ELF 导出表找到。
- 原生核心需要的 PIN / Surface / 停止 / 错误回调字符串存在；Java ABI 适配类全部随 APK 打包。
- APK 不包含上游 AirPlay Go library、DisplayLink library，也不包含上游整个应用的界面和服务。
- 源码包排除构建缓存、机器路径、签名私钥和 Git 元数据。
- 0.1.2 release preview 已通过 ADB 覆盖安装到用户真实手机 PGP110（Android 15），未操作其他任务的模拟器。
- 用户已在旧版完成 Android 手机 → iPad Moonlight 实际投屏，反馈「视频铺满」可用，而启发式自动去黑边仍有重复黑边。该问题尚未宣布修复。
- 实际播放画面截图为 2412×1080，中央视频 1920×1080，两侧各约 246 像素。新增固定中央 16:9 模式直接按此几何关系截取，不依赖像素检测。
- 纯 Java 几何测试通过：实际手机裁切范围、iPad 2388×1668 输出保留比例、较方横屏上下裁切、竖屏恢复整屏及旧模式边界。
- 0.1.2 在该真机运行 RenderingInstrumentation 通过：实际 SurfaceTexture / EGL 输出、固定 16:9 视频区域、旧模式 fit/fill、上下色彩方向、竖屏尺寸切换。测试使用生成画面，未把它计作真实 iPad 端到端验证。
- 0.1.2 APK SHA-256：`eb054f3b274a8e882b5ef761a47e1352014311045d80d253374f4731fbadd618`。
- 0.1.3 新增可选手机媒体静音：仅音频捕获启动成功时生效，停止、捕获中断和失败时恢复；原音量以原子文件保存用于意外退出恢复。
- 0.1.3 音量会话测试使用假的设备与持久记录，验证静音、重复清理、跨实例恢复、用户主动调音量、原本静音、记录写入失败和系统拒绝静音。它不验证真实 AudioPlaybackCapture 或扬声器。
- 0.1.3 `assembleDebug`、`assembleRelease`、`lintDebug`、APK 原生 ABI 检查、v2 签名与 zipalign 检查通过；Lint 0 errors、11 warnings。
- 0.1.3 APK SHA-256：`5635f648130af1fa43327819c5d77e0238a774d80a45332b62a27ede28efbf73`。
- 0.1.3 已覆盖安装到重新连接的 PGP110 真机，并由 `dumpsys package` 确认 versionCode 4、versionName 0.1.3；已打开应用设置页。

## 尚未验证

- 0.1.3 开发期间用户真机曾离线，打包完成时重新连接。新版音频尚未验证「手机无声、iPad 持续有声」、实际停止恢复音量和 ROM 兼容性；未操作其他任务的模拟器。
- 新的固定视频区域模式尚待用户在 iPad 上重连验证。任意视频比例、非居中播放器不属于这个固定预设的适用范围。
- 真实 Android TV 解码、系统播放音频、连续运行和重连稳定性尚未完整验证。
- Root app_process / DisplayManager 后端仅完成编译与静态检查，未实机验证，必须视为实验功能。
- 未在本机重新编译 NDK 核心；交付 APK 使用已固定、已校验的上游原生运行库。附带其对应源码与源码构建配置。
- 未测量延迟、丢帧、PSNR / SSIM、温控、4K60 或高码率稳定性，不能把本记录作为画质/兼容性承诺。

## 实机验收步骤

1. 在测试手机安装 APK。拒绝录屏授权时不应启动 Host；授予共享整个屏幕后 Host 应可发现。
2. 官方 Moonlight 添加手机 IP，测试正确和错误 PIN。重启 APK 后已配对电视应仍能连接。
3. 使用 H.264 1080p60、HEVC 1080p60 分别连接。打开内置测试画面，确认文本、灰阶、运动圆点和时间变化；再退出测试画面切到目标应用。
4. 后台运行、旋转手机、系统结束共享、电视断开、通知停止；检查 Host 端口关闭且再次启动正常，Android 14+ 每次新会话重新授权。
5. Android 10+ 勾选音频捕获，在允许捕获的媒体应用播放，确认电视收到音频；不允许捕获的应用应保持视频链路可用。
6. 在专用 Root 测试手机上验证 su 授权、Root 镜像、接收端重连、APK 停止与进程意外退出后的 daemon 清理。失败则使用非 Root 路径。
7. 基础链路稳定后再增加分辨率、码率和帧率，记录实际编码器、Moonlight 统计数据、网络和温度。真实 Android TV 仍需要单独测试。

## 重现静态检查

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug
python scripts/verify_apk.py
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
zipalign -c -v 4 app/build/outputs/apk/release/app-release.apk
```
