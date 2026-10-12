<p align="center"><img src="https://raw.githubusercontent.com/meta-tabchen/MoonCast/unified/community/docs/assets/mooncast-hero-v2.png" alt="MoonCast: your Android phone on a bigger screen; model-generated concept artwork" width="100%"></p>

<p align="center"><sub>模型生成品牌概念图，非设备截图或实际互通测试。</sub></p>

<p align="center">
  <a href="https://github.com/meta-tabchen/MoonCast/releases/tag/community-v0.5.1"><img src="https://img.shields.io/badge/download-0.5.1_community_preview-547BEB" alt="Current download: 0.5.1 community preview"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 and later">
  <img src="https://img.shields.io/badge/roles-Send_%2B_Receive-8A6DE9" alt="Send and Receive roles">
  <a href="https://github.com/meta-tabchen/MoonCast/blob/unified/community/LICENSE"><img src="https://img.shields.io/badge/license-GPLv3-blue" alt="GPLv3"></a>
</p>

<p align="center"><a href="README.md">English</a> · <b>简体中文</b></p>
<p align="center"><a href="https://github.com/meta-tabchen/MoonCast/releases/tag/community-v0.5.1">下载 APK</a> · <a href="https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/unified-guide.md">使用指南</a> · <a href="https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/cropping.zh-CN.md">裁剪算法</a> · <a href="https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/unified-build.md">构建</a> · <a href="https://github.com/meta-tabchen/MoonCast/blob/unified/community/CONTRIBUTING.md">参与贡献</a></p>

# MoonCast · 让手机视频，真正用好大屏

**一个安卓应用，兼顾手机发送与电视接收，也认真处理让大屏变小的投屏黑边。**

视频本身可能已经被黑边包在手机画面里。把整张手机画面等比放进电视，又会增加一层留白。MoonCast 帮你利用这部分空间：检测稳定黑边、按比例显示有效画面，并在自动判断不确定时提供手动调整。

**0.5.1 预览版已知限制：** 构建、签名与模拟器合成同签名升级通过，但[整轮 CI 失败](https://github.com/meta-tabchen/MoonCast/actions/runs/38203238592)：Android 15 后续语言进程启动发生 ANR，Android 11 安装时包管理服务消失。后续堆栈支持软件模拟器严重调度拥塞；现已补充下方平板真机界面检查，双端投屏仍未验证。已知角色菜单缺陷：取消或选择当前角色也会重建界面／接收器。[完整验证结果](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/community-validation.md) · [旧版 0.5.0 下载](https://github.com/meta-tabchen/MoonCast/releases/tag/unified-v0.5.0-preview)

当前统一实现与构建请使用 [`unified/community`](https://github.com/meta-tabchen/MoonCast/tree/unified/community)。本页展示当前项目说明，main 的历史源码仍保留；下方技术链接明确指向统一版分支。

## 真实界面演示

![MoonCast 0.5.1 平板真机界面：切换语言、进入电视模式及打开设置](https://raw.githubusercontent.com/meta-tabchen/MoonCast/unified/community/docs/assets/mooncast-0.5.1-xiaomi-pad8-ui.gif)

*小米平板 8 · Android 17 / API 37 · MoonCast 0.5.1-community。16 秒真实操作，保留原速，仅剪去等待时间。演示中文切英文、手机界面切电视角色，以及电视设置与焦点移动。电视界面运行在这台平板上，没有展示双端投屏或音频效果。*

7 次实测冷启动为 268–509 毫秒，未观察到 ANR。手机／电视角色中的跟随系统、英文、中文选择均在重开后保留；语言菜单取消／同选项、设置返回及方向焦点检查通过。**0.5.1 已知问题：设备角色菜单**取消或选择当前角色仍会重建手机界面或电视接收器，不能与已通过的语言菜单检查混为一谈。本次单设备测试未验证 AirPlay 音频、TCL 电视投屏、设备发现或 APK 推送。

## 为什么用 MoonCast

- **减少黑边占用。** 电视「自动去黑边」寻找稳定、成对的黑色边缘，再把保留区域等比适配到屏幕，不靠拉伸人物和画面来填满。
- **减少画面来回跳变。** 电视检测器积累多次一致证据才锁定裁剪。锁定后，如果裁剪外出现可见内容，安全检查可以放宽区域，而不是不断收紧和放大。
- **遥控器就能微调画面。** 保留完整画面、自动去边、填满屏幕，或手动调整四边、缩放与位置。播放时的半透明设置面板便于边看边调，内容改变后可重新扫描。
- **手机电视装同一个应用。** 手机默认发送，Android TV / Leanback 默认接收；设置可切换并记住角色。切换会先停止旧角色，不支持同时收发。
- **找电视与传 APK 共用设备入口。**「我的电视」展示投屏及 APK 接收能力；传完仍由你在电视上确认安装。

MoonCast 0.5 为实验预览版，欢迎提交设备体验与兼容性反馈。

## 去掉黑边，不等于无损填满屏幕

![Auto and Fill concept comparison](https://raw.githubusercontent.com/meta-tabchen/MoonCast/unified/community/docs/assets/mooncast-auto-vs-fill.png)

*模型生成示意：左侧保留宽银幕内容与必要上下留白；右侧放大覆盖屏幕，红屋与边缘树木被裁掉。不是设备实测，也不是精确比例仿真。*

| 电视画面模式 | 怎么处理 | 需要知道 |
|---|---|---|
| **自动去黑边** | 检测稳定的近黑边，保留区域等比适配 | 检测可能暂不确定；宽高比不同仍可能留边 |
| **适应／完整画面** | 将完整源画面等比放入屏幕 | 保留源画面边缘，也保留原有黑边 |
| **填满屏幕** | 放大完整源画面直到覆盖屏幕 | 超出屏幕的内容会被裁掉 |
| **手动** | 独立调整四边、缩放和位置 | 由你选择允许隐藏哪些内容 |

自动检测不能补回像素、识别主体，也不能区分所有暗场与黑边。字幕、控制条、过暗或不对称画面都可能阻止锁定。想恢复完整画面就选「适应」，内容变了可「重新扫描」。**裁掉冗余黑色像素，不保证任何视频都能保留全部内容并铺满屏幕。**

## 三步开始投屏

安装并准备好网络后：

1. 电视打开 MoonCast 的**接收模式**。手机与电视连接同一可信局域网，网络允许设备互访。
2. 安卓手机选择 **AirPlay → 我的电视**，选接收端、开始分享并确认系统录屏授权。iPhone / iPad 在系统 AirPlay 屏幕镜像或媒体输出列表中选择 MoonCast。
3. 播放视频。电视首次安装默认「自动去黑边」；给稳定、可见的画面几秒钟，再按需要选择适应、填满、手动或重新扫描。

GameStream 和 DLNA 的连接方式见下文。

## 自动裁剪如何工作

电视从小尺寸预览采样，扫描成对近黑边，检查内部是否存在分布足够广的可见内容，再等待边界稳定后等比显示。普通黑边需要 8 个有效样本，宽黑边需要 12 个。锁定后，如果连续两次在裁剪外发现可见内容，安全检查可放宽裁剪。

这是**像素阈值与几何规则算法，不是 AI 场景识别**。手机发送端使用另一套裁剪规则，不能把电视端的所有保护都视为发送端能力。[查看具体实现、阈值与限制 →](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/cropping.zh-CN.md)

## 按场景选择用法

**在电视上看片：** 使用 MoonCast 接收模式，先选自动去黑边；遇到字幕或特殊构图，可随时切回适应。接收端也可播放兼容 DLNA 控制端推送的受支持媒体地址。

**手机投到已有 Moonlight 的 iPad、电视或电脑：** 安卓手机启动 GameStream 主机，从 Moonlight 客户端配对连接，无需电脑中转。配对、客户端参数与硬件解码沿用 Moonlight 工作流。

**播放原文件：** 手机的「原文件影院」将选中的照片、视频或音频通过带令牌的局域网浏览器链接提供，传输字节不转码。能否播放仍取决于浏览器的容器和编码支持。这与屏幕镜像、DLNA 接收、Moonlight 是不同路径。

## 能力与实际边界

| 领域 | 已实现能力 | 范围／限制 |
|---|---|---|
| 手机 → Moonlight | H.264 / HEVC SDR 屏幕编码，使用现有 Moonlight 客户端 | 有损编码；不宣称 HDR、AV1 或延迟实测成绩 |
| 手机 → AirPlay | 安卓录屏发送到兼容 AirPlay 接收端 | 协议变体和设备兼容性有差异 |
| 电视接收 | AirPlay 镜像／媒体、受支持 DLNA 地址、GameStream 客户端 | 各自独立协议路径，不是安卓系统投屏通用接收器 |
| 手机裁剪 | 完整屏幕、自动适应／填满、居中 16:9、稳定影院 | 自动检测仅横屏；固定 16:9 假设内容居中 |
| 电视裁剪 | 自动、适应、填满、四边手动、缩放／平移与重新扫描 | 基于规则；暗场和浮层可能影响判断 |
| 播放声音 | 可选转发允许采集的播放声音，本地静音与恢复 | Android 10+ 且源应用允许；Root 捕获仅视频 |
| 配置与预设 | 接收端偏好，观影／游戏／省电预设 | 不替代 Moonlight 分辨率／帧率／码率设置，也不自动授权 |
| 运行信息 | 捕获提交帧率、应用 UID 流量与温控信息 | 不是端到端延迟、接收端帧率或省电实测 |
| 可选反控 | 无障碍手势；Root／Shizuku 持续触摸与基础按键 | 默认关闭，需明确授权，受后端和 ROM 限制 |
| 应用捕获 | Android 14+ 系统单应用选择；Shizuku 独立显示 | 系统单应用缺少可靠输入映射；独立显示仅视频 |
| 多人观看 | 实验性最多三个 Moonlight 接收端 | 受编码器／网络限制；共用画面及音频包时长；关闭反控 |
| 本地屏幕电源 | 可选 Root／Shizuku 熄屏请求与恢复租约 | 实验性、依赖 ROM；实际面板与省电效果待实机验证 |
| 电视体验 | 遥控器设置、四种待机主题、半透明播放设置 | 主题用于待机，不覆盖正在播放的视频 |
| APK 传输 | 电视主动开启、包信息审阅、调用系统安装器 | 仅普通 `.apk`；无静默安装、`.apks` 或 `.xapk` 处理 |

进阶发送功能保留[逐项实现与历史验证说明](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/feature-development.md)。历史结果不能替当前统一 APK 验收；当前范围见[统一版验证矩阵](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/community-validation.md)。

## 下载 0.5.1 社区预览版

[版本说明与全部附件](https://github.com/meta-tabchen/MoonCast/releases/tag/community-v0.5.1) · [ARM64 安装包](https://github.com/meta-tabchen/MoonCast/releases/download/community-v0.5.1/MoonCast-0.5.1-community-arm64-v8a.apk) · [ARMv7 安装包](https://github.com/meta-tabchen/MoonCast/releases/download/community-v0.5.1/MoonCast-0.5.1-community-armeabi-v7a.apk) · [完整对应源码](https://github.com/meta-tabchen/MoonCast/releases/download/community-v0.5.1/MoonCast-0.5.1-community-complete-source.zip) · [SHA256SUMS 校验和](https://github.com/meta-tabchen/MoonCast/releases/download/community-v0.5.1/SHA256SUMS)

最低 Android 8.0（API 26）。64 位 ARM 安卓系统选 ARM64，32 位 ARM 安卓系统选 ARMv7；以系统实际支持的 ABI 为准，不只看处理器宣传名称。两个安装包功能相同，均包含手机和电视模式，每台设备只选一个安装。下载附件是已签名的实验预览包；CI 生成的 unsigned 验证包不能直接作为安装版使用。本次不发布 x86 安装包。

上方完整对应源码 ZIP 与本次 0.5.1 APK 同源，应用源码提交为 `27967d778c55042bb2adc926074bc3b1d94e19b9`，标签为 `community-v0.5.1`；后续开发位于 `unified/community` 分支。依赖恢复脚本仍校验并使用旧 0.5.0 源包中的固定原生输入，不会替换本版应用源码。Git 克隆后先运行 `python3 scripts/restore_release_inputs.py` 恢复固定构建输入，再按[构建说明](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/unified-build.md)操作；完整源 ZIP 已含这些固定依赖，但不含 MoonCast 应用的 PKCS12 签名文件；可按构建指南生成独立包名的无签名验证产物。签名社区构建使用完整 Git checkout 中由用户一次初始化的固定公开测试身份。

## 两台设备怎么配合

- 安卓手机 → AirPlay：手机选 AirPlay，在统一「My TV」设备列表选择电视，再开始分享并确认系统录屏授权。接收端可以是 MoonCast 电视模式或兼容的第三方 AirPlay 接收器。
- 安卓手机 → Moonlight：手机选 Moonlight 并启动主机，电视模式或官方 Moonlight 客户端连接手机，按提示完成 GameStream PIN 配对。
- iPhone / iPad → 电视：在苹果系统的屏幕镜像或媒体输出列表选 MoonCast。
- DLNA：兼容控制端把支持的媒体地址推给 MoonCast 电视模式。

My TV 统一显示设备及能力。普通 AirPlay 电视不等于能收 APK。取消配对码指的是 APK 传输；GameStream 自身的 PIN 安全配对仍保留。

## 手机发送 APK 到电视

电视开启「安装手机 APK」，手机选择 `.apk` → My TV 选择支持 APK 的电视 → 上传。固定端口为 TCP 48152，不需要额外传输配对码。电视核对包信息并明确确认后，才打开 Android 系统安装器。

首次允许「安装未知应用」必须由你在系统设置操作。授权回来无需重新上传，在保留的审核页面再次确认即可。不会静默安装，也不能绕过 Android 的签名、降级、设备兼容或分包要求；扩展名为 APK 不代表任意文件都能成功安装。

## 重要边界

- APK 使用未加密 HTTP，仅限可信局域网，不能暴露公网。仍保留文件大小、并发、速率、超时、私有路径、哈希、解析及 FileProvider 限制。
- 本项目未实现 Miracast、Google Cast/Chromecast、Lelink、其他厂商系统投屏协议或 AirPlay 2 多房间；GameStream 不进入 Android 系统投屏列表。
- DRM 视频、禁止录屏或禁止音频捕获的应用可能黑屏或无声音。屏幕镜像有损，不宣称原画无损或假 4K。
- Root / Shizuku / 无障碍控制仍为可选功能，不会自动授权，不改路由器或系统安全设置。
- 编译、主机测试和模拟器测试不等于所有手机电视实机兼容。发布时须列出实际通过及未验证项目。

## 社区版升级与旧版迁移

旧 0.5.0 使用 `com.mooncast.unified.preview` 与一次性测试签名。原签名身份已不再可用，不能承诺用后续构建覆盖它。

0.5.1 社区版采用固定包名 `com.mooncast.community` 与仓库公开的固定测试签名，后续递增 versionCode，以支持社区版之间正常覆盖升级。旧 0.5.0 到社区版需要一次迁移，不是每次更新都换包名或长期要求多版本共存。配对、权限与私有设置不能自动跨包搬运。

公开测试密钥不能证明安装包来自维护者：任何人都可用它签名。只从可信发布入口下载并核对发布校验和。未来商店版将使用独立包名与私有生产密钥，不与公开测试身份混用。

社区签名身份与构建失败保护见[签名说明](https://github.com/meta-tabchen/MoonCast/blob/unified/community/signing/community/README.md)。

## 语言设置

0.5.1 包含应用内语言入口；旧 0.5.0 不含此入口。历史 CI 中仅部分系统语言资源检查完成，该轮完整语言与界面回归未通过；后续平板真机语言结果及剩余限制见上文。

手机设置和电视高级设置提供「跟随系统」、English、简体中文。取消或选择当前语言不会停止会话；确认更换语言后才结束旧会话并重新打开当前角色。Android 13 及以上也支持系统应用语言选择。直接从系统设置切换时，活跃通知可能延后刷新，见[语言使用说明](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/unified-guide.md)。

## 构建与参与

需要 JDK 17、Android SDK 35、Build Tools 35.0.0 与 Gradle 8.9 / AGP 8.7.3。当前实现位于 `unified/community`。

```sh
git clone --branch unified/community https://github.com/meta-tabchen/MoonCast.git
cd MoonCast
python3 scripts/restore_release_inputs.py
python3 scripts/prepare_airplay.py --verify-sources
python3 scripts/prepare_airplay.py --verify-aar app/libs/airplaylib.aar
python3 scripts/prepare_unified_native.py
gradle :unified:assembleRelease :unified:lintRelease -PmooncastAbi=arm64-v8a
```

完整 Git checkout 的社区 release 构建使用用户一次初始化的固定公开测试身份。运行 `python3 scripts/verify_community_identity.py unified/build/outputs/apk/release/unified-release.apk` 校验；添加 `--previous /path/to/previous-community.apk` 可检查升级身份与递增 versionCode。缺失或被修改的签名输入会令构建失败，不会临时生成替代身份。独立私有身份实现前，production 构建明确禁用。Release 源码 ZIP 不含 MoonCast 应用的 PKCS12 签名文件；可显式加 `-PmooncastUnsignedVerification=true` 构建独立 `com.mooncast.community.verification` 无签名验证产物，不可覆盖社区版。原生库重建、依赖与源码打包见[构建指南](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/unified-build.md)。

设备报告请附发送端／接收端型号、系统和应用版本、协议、编码／分辨率／帧率／码率、画面模式及复现步骤。将「能连接」「有画面」「有声音」「裁剪正确」分别描述，删除日志中的隐私及配对信息。[反馈问题](https://github.com/meta-tabchen/MoonCast/issues) · [参与贡献](https://github.com/meta-tabchen/MoonCast/blob/unified/community/CONTRIBUTING.md) · [安全说明](https://github.com/meta-tabchen/MoonCast/blob/unified/community/SECURITY.md)。

[历史 0.3.1 手机发送端 README](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/historical-sender-0.3.1.zh-CN.md) 单独保留，不再折叠进当前项目介绍；内容仅适用于历史版本。

## 源码与致谢

手机 GameStream 主机基于 Mirror / Sunshine，AirPlay 发送采用固定版本 Go 发送库；接收端使用带补丁的 AndroidAirPlay / UxPlay、Media3 / DLNA 与 Moonlight core。两套 OpenSSL 通过不同库名与进程隔离，并非任意合并。MoonCast 是独立 GPLv3 项目，不是 Apple、Moonlight 或 Sunshine 的官方发行版。

项目按 GPLv3 提供对应源码。完整源包包含发送/接收依赖源、补丁、原生库校验和与构建方法。第三方许可见 [THIRD_PARTY_NOTICES.md](https://github.com/meta-tabchen/MoonCast/blob/unified/community/THIRD_PARTY_NOTICES.md)，背景来源见 [主题说明](https://github.com/meta-tabchen/MoonCast/blob/unified/community/docs/tv-themes.md)。GitHub 发布需同步 APK、完整对应源码和 SHA256SUMS，不能只放截图或源码地址。
