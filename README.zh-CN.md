<p align="center"><img src="docs/assets/mooncast-hero-v2.png" alt="MoonCast: your Android phone on a bigger screen; model-generated concept artwork" width="100%"></p>

<p align="center"><sub>模型生成品牌概念图，非设备截图或实际互通测试。</sub></p>

<p align="center">
  <a href="https://github.com/meta-tabchen/MoonCast/releases/tag/v0.9"><img src="https://img.shields.io/badge/download-0.9-547BEB" alt="Current download: 0.9"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 and later">
  <img src="https://img.shields.io/badge/roles-Send_%2B_Receive-8A6DE9" alt="Send and Receive roles">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPLv3-blue" alt="GPLv3"></a>
</p>

<p align="center"><a href="README.md">English</a> · <b>简体中文</b></p>
<p align="center"><a href="https://github.com/meta-tabchen/MoonCast/releases/tag/v0.9">下载 APK</a> · <a href="docs/browser-receiver.md">浏览器上手</a> · <a href="docs/unified-guide.md">电视指南</a> · <a href="docs/cropping.zh-CN.md">裁剪算法</a> · <a href="docs/unified-build.md">构建</a> · <a href="CONTRIBUTING.md">参与贡献</a></p>

# MoonCast · 从手机到大屏，也到浏览器

**安卓画面可以送到浏览器、AirPlay 接收端或 Moonlight 客户端；另一台安卓设备也能成为电视接收端。一个应用，多种连接方式，按场景选择画面呈现。**

视频本身可能已经被黑边包在手机画面里。把整张手机画面等比放进电视，又会增加一层留白。MoonCast 帮你利用这部分空间：检测稳定黑边、按比例显示有效画面，并在自动判断不确定时提供手动调整。

## 当前版本

**0.9 / code66 已提供下载。** 新增手动悬浮控制、透明最低亮度调暗、统一设备名及接收端诊断。Root/Shizuku 物理关屏仍需对应设备的安全恢复证据，未作为通用功能开放。[改动、实际测试结果与限制](docs/release-0.9-validation.md) · [悬浮控制使用说明](docs/manual-controls-0.9.md)。历史[0.8验证记录](docs/release-0.8-validation.md)、标签与附件继续保留。

## 0.7：启动问题修复与手机显示优化

- **修复已复现的GameStream身份初始化缺类。** 统一Bouncy Castle官方BOM配套：bcpkix／bcutil 1.85、bcprov 1.85.2。旧混合依赖在首次生成身份时会抛出NoClassDefFoundError；临时目录测试覆盖首次与已有身份，并检查不会重写已有密钥。
- **启动失败不会只留下“已停止”。** 保存有界的启动阶段／错误，主动回传后再清理host，避免短时进程退出早于界面轮询而丢失原因。
- **窄屏协议Tab更易读。** 手机两行等权布局，针对中英文与窄屏断词检查。
- **连接中仍可取消。** 保留连接期间的Disconnect入口，新增offer尚未完成时取消的桌面浏览器用例。
- **浏览器提供沉浸视图。** 控件自动隐藏并可触摸恢复，照顾安全区；全屏API缺失或被拒绝时降级为页面内沉浸，不承诺隐藏Safari浏览器或系统栏。

**验证范围：** Mac主机／模拟布局／桌面浏览器检查与最终两ABI签名／BC类型引用检查有结果；没有追加手机、iPhone或完整GameStream端到端验证。本地OriginalMediaServerTest连接重置失败保留，首轮CI有测试解析失败；修复后的b8be236 CI通过主机／BC回归、双ABI构建、lint及签名检查，手动设备任务跳过。[本版验证明细](docs/release-0.7-validation.md)

## 扫码连接，按设备选择画质

自动二维码与画质控制沿用此前code55实现。下方为图标修改前的历史短窗观察，不是0.7重新实测。

- **二维码自动出现。** 浏览器模式开始共享并确认录屏后，连接卡直接展示二维码和复制链接，不需要再点展开。两者对应同一个临时局域网 URL，二维码本地生成，不向外部服务上传。停止后清除；请像保护链接一样保护有效二维码。
- **在细节与流量之间选择。** 流畅上限为长边 720／24 fps／2 Mbps；均衡保留之前的 1280／30／8 Mbps 默认。清晰按设备报告能力选择最高候选，最多 3840／30 fps／24 Mbps，必要时降低到设备报告支持的帧率。
- **先保存设置，下次共享生效。** 高级设置提供尺寸、帧率和码率上限，并依据硬件能力筛选。修改不会重启或改变正在进行的捕获，停止后重新开始才应用；不主动把源画面放大到更高编码分辨率。
- **看清实际收到了什么。** 浏览器分别显示目标上限、接收尺寸、编码、解码帧率和 RTP 视频 Mbps。适应／填充／缩放只改变显示方式，不增加编码细节。

### 实测是观察值，不是预设保证

本轮 code 55 开发期间的短窗口观察：

| 预设 | 配置目标上限 | 浏览器实际接收 |
|---|---|---|
| 清晰 | 长边≤3840，≤30 fps，≤24 Mbps | 3200×2136，约25.2解码fps，2.6 Mbps RTP视频 |
| 流畅 | 长边≤720，≤24 fps，≤2 Mbps | 720×480，约22解码fps，0.48 Mbps RTP视频 |

这是短时观察，不是持续性能保证、发送编码器精确码率或全部局域网吞吐。清晰通过5项检查；流畅的自动停止观察超时，随后独立检查确认停止和旧链接关闭。**本轮未测均衡、旋转或实际摄像头扫码，最终仅图标变更构建未追加真机验证。** 硬件能力报告不保证稳定4K/30，实际质量仍受内容、CPU和网络影响。[画质设置与验证范围](docs/browser-qr-quality.md)

## 浏览器 preview.3：保留的真实安卓到Mac证据

![小米平板8真实捕获到Mac Chrome；画面内容为专用合成测试图](docs/assets/browser-android-demo.gif)

*MoonCast 0.6.0-browser-preview.3 / code 54 / build 7a843a1。小米平板 8（Android 17 / API 37）真实录屏，经正式 WebRTC 路径传到 Mac Chrome 154；片中是专用测试应用的色条与运动图形，不是网页模拟发送。GIF 无音轨。*

本次接收端实际解码 **H.264 1280×854**，并收到 **48 kHz Opus**：开启声音后，解码音频样本与非零能量持续增长。实际画面在适应／填充操作期间继续播放，安卓正常停止后捕获与连接关闭，旧 URL 无法访问。**这证明了该平板到该浏览器的采集、传输与解码路径；未进行扬声器听音、音画同步、旋转、TCL 电视或 iPhone 验收。** [查看实测范围](docs/browser-validation.md)

<p align="center"><img src="docs/assets/browser-android-ui-code54.png" alt="code54真实平板浏览器模式页面" width="48%"> <img src="docs/assets/browser-android-about-code54.png" alt="code54真实关于页面显示版本和构建标识" width="48%"></p>

*code 54 真实平板界面：轻量协议胶囊 Tab、浏览器共享页面与安装版本信息。截图不含会话链接。*

## 三步投到浏览器

1. 安卓 MoonCast 选择**浏览器**，按需开启播放声音，点击**开始共享**并同意系统录屏。两端连接同一可信局域网。
2. 在 Mac Chrome 打开安卓端显示的**完整临时链接**，点击**连接**。不需要安装接收应用、创建账号或配置云端信令。
3. 点击**开启声音**允许播放；用适应／填充、缩放、拖动平移或全屏调整。安卓端停止共享后，会话结束，旧链接不可再访问。

浏览器预览只接收一个观看者，音频默认关闭且只采集源应用允许的播放内容，不启用麦克风或反控。**浏览器模式没有自动去黑边**；下文 TV Auto 属于电视接收路径。HTTP 页面／信令只适合可信局域网，媒体使用 DTLS-SRTP。网络或 IP 改变后停止重开。[浏览器指南与安全边界](docs/browser-receiver.md)

能力预检接受某个报告支持目标的编码器，但 WebRTC 实际可能选择同 MIME 的另一个编码器，后者未必支持同一高级组合。这是静态发现的兼容风险，不是已实测的普遍故障；不否定上述平板清晰／流畅结果。其他设备看实际统计，失败时改均衡／流畅并重新共享。

## 为什么用 MoonCast

- **电脑打开浏览器即可接收。** 新增 Android → WebRTC 网页路径，当前已实测 Mac Chrome；免装接收客户端，会话结束后临时局域网链接不再可用。
- **减少黑边占用。** 电视「自动去黑边」寻找稳定、成对的黑色边缘，再把保留区域等比适配到屏幕，不靠拉伸人物和画面来填满。
- **减少画面来回跳变。** 电视检测器积累多次一致证据才锁定裁剪。锁定后，如果裁剪外出现可见内容，安全检查可以放宽区域，而不是不断收紧和放大。
- **遥控器就能微调画面。** 保留完整画面、自动去边、填满屏幕，或手动调整四边、缩放与位置。播放时的半透明设置面板便于边看边调，内容改变后可重新扫描。
- **手机电视装同一个应用。** 手机默认发送，Android TV / Leanback 默认接收；设置可切换并记住角色。切换会先停止旧角色，不支持同时收发。
- **找电视与传 APK 共用设备入口。**「我的电视」展示投屏及 APK 接收能力；传完仍由你在电视上确认安装。

部分 MoonCast 协议路径仍为实验性功能，欢迎提交设备体验与兼容性反馈。

## 去掉黑边，不等于无损填满屏幕

![Auto and Fill concept comparison](docs/assets/mooncast-auto-vs-fill.png)

*模型生成示意：左侧保留宽银幕内容与必要上下留白；右侧放大覆盖屏幕，红屋与边缘树木被裁掉。不是设备实测，也不是精确比例仿真。*

| 电视画面模式 | 怎么处理 | 需要知道 |
|---|---|---|
| **自动去黑边** | 检测稳定的近黑边，保留区域等比适配 | 检测可能暂不确定；宽高比不同仍可能留边 |
| **适应／完整画面** | 将完整源画面等比放入屏幕 | 保留源画面边缘，也保留原有黑边 |
| **填满屏幕** | 放大完整源画面直到覆盖屏幕 | 超出屏幕的内容会被裁掉 |
| **手动** | 独立调整四边、缩放和位置 | 由你选择允许隐藏哪些内容 |

自动检测不能补回像素、识别主体，也不能区分所有暗场与黑边。字幕、控制条、过暗或不对称画面都可能阻止锁定。想恢复完整画面就选「适应」，内容变了可「重新扫描」。**裁掉冗余黑色像素，不保证任何视频都能保留全部内容并铺满屏幕。**

## 三步投到电视

安装并准备好网络后：

1. 电视打开 MoonCast 的**接收模式**。手机与电视连接同一可信局域网，网络允许设备互访。
2. 安卓手机选择 **AirPlay → 我的电视**，选接收端、开始分享并确认系统录屏授权。iPhone / iPad 在系统 AirPlay 屏幕镜像或媒体输出列表中选择 MoonCast。
3. 播放视频。电视首次安装默认「自动去黑边」；给稳定、可见的画面几秒钟，再按需要选择适应、填满、手动或重新扫描。

GameStream 和 DLNA 的连接方式见下文。

## 自动裁剪如何工作

电视从小尺寸预览采样，扫描成对近黑边，检查内部是否存在分布足够广的可见内容，再等待边界稳定后等比显示。普通黑边需要 8 个有效样本，宽黑边需要 12 个。锁定后，如果连续两次在裁剪外发现可见内容，安全检查可放宽裁剪。

这是**像素阈值与几何规则算法，不是 AI 场景识别**。手机发送端使用另一套裁剪规则，不能把电视端的所有保护都视为发送端能力。[查看具体实现、阈值与限制 →](docs/cropping.zh-CN.md)

## 按场景选择用法

**在电视上看片：** 使用 MoonCast 接收模式，先选自动去黑边；遇到字幕或特殊构图，可随时切回适应。接收端也可播放兼容 DLNA 控制端推送的受支持媒体地址。

**手机投到已有 Moonlight 的 iPad、电视或电脑：** 安卓手机启动 GameStream 主机，从 Moonlight 客户端配对连接，无需电脑中转。配对、客户端参数与硬件解码沿用 Moonlight 工作流。

**播放原文件：** 手机的「原文件影院」将选中的照片、视频或音频通过带令牌的局域网浏览器链接提供，传输字节不转码。能否播放仍取决于浏览器的容器和编码支持。这与屏幕镜像、DLNA 接收、Moonlight 是不同路径。

## 能力与实际边界

| 领域 | 已实现能力 | 范围／限制 |
|---|---|---|
| 手机 → 浏览器 | WebRTC 单观看者；适应／填充、缩放／平移、可选播放音频 | 实测 Mac Chrome；无自动去黑边、反控、云中转；电视浏览器另测 |
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

进阶发送功能保留[逐项实现与历史验证说明](docs/feature-development.md)。历史结果不能替当前统一 APK 验收；当前浏览器路径见[浏览器验证记录](docs/browser-validation.md)，旧版记录仍保留。

## 下载 0.9

[手机／电视首次安装与排障](docs/unified-guide.md)：电视须先安装 MoonCast，之后才能接收手机推送的 APK。

[版本说明](https://github.com/meta-tabchen/MoonCast/releases/tag/v0.9) · [ARM64 APK](https://github.com/meta-tabchen/MoonCast/releases/download/v0.9/MoonCast-0.9-arm64-v8a.apk) · [ARMv7 APK](https://github.com/meta-tabchen/MoonCast/releases/download/v0.9/MoonCast-0.9-armeabi-v7a.apk) · [应用源码](https://github.com/meta-tabchen/MoonCast/releases/download/v0.9/MoonCast-0.9-source.zip) · [许可证清单](https://github.com/meta-tabchen/MoonCast/releases/download/v0.9/MoonCast-0.9-notices.zip) · [SHA256SUMS](https://github.com/meta-tabchen/MoonCast/releases/download/v0.9/SHA256SUMS)

最低Android 8.0。每台设备按系统ABI选择一个APK，每个均含手机与电视角色。版本 `0.9`，versionCode `66`；固定 `com.mooncast.community` 包名与现有公开测试证书不变。最终来源、产物哈希与检查结果以Release清单为准。公开测试签名保证升级身份连续性，不证明发布者身份。

应用源码与声明提供固定WebRTC外部源码／DEPS及构建获取映射，**不是完整离线SDK依赖树，预编译原生SDK未独立重建**。[源码范围](docs/browser-release-source.md) · [版本规则](docs/versioning.md)。

[0.6.0-browser-preview.4 / code55](https://github.com/meta-tabchen/MoonCast/releases/tag/browser-v0.6.0-preview.4) · [0.6.0-browser-preview.3 / code54](https://github.com/meta-tabchen/MoonCast/releases/tag/browser-v0.6.0-preview.3) · [0.5.1](https://github.com/meta-tabchen/MoonCast/releases/tag/community-v0.5.1) · [0.5.0](https://github.com/meta-tabchen/MoonCast/releases/tag/unified-v0.5.0-preview)

以上历史公开附件与影像保留原版本。数字0.6.0仅停留在草稿，不是此前公开发行版；不提供草稿下载链接。

## 两台设备怎么配合

- 安卓手机 → 浏览器：手机选浏览器、开始共享；在同网 Mac Chrome 打开临时链接并连接。
- 安卓手机 → AirPlay：手机选 AirPlay，在统一「My TV」设备列表选择电视，再开始分享并确认系统录屏授权。接收端可以是 MoonCast 电视模式或兼容的第三方 AirPlay 接收器。
- 安卓手机 → Moonlight：手机选 Moonlight 并启动主机，电视模式或官方 Moonlight 客户端连接手机，按提示完成 GameStream PIN 配对。
- iPhone / iPad → 电视：在苹果系统的屏幕镜像或媒体输出列表选 MoonCast。
- DLNA：兼容控制端把支持的媒体地址推给 MoonCast 电视模式。

My TV 统一显示设备及能力。普通 AirPlay 电视不等于能收 APK。取消配对码指的是 APK 传输；GameStream 自身的 PIN 安全配对仍保留。

## 手机发送 APK 到电视

电视开启「安装手机 APK」，手机选择 `.apk` → My TV 选择支持 APK 的电视 → 上传。固定端口为 TCP 48152，不需要额外传输配对码。电视核对包信息并明确确认后，才打开 Android 系统安装器。

首次允许「安装未知应用」必须由你在系统设置操作。授权回来无需重新上传，在保留的审核页面再次确认即可。不会静默安装，也不能绕过 Android 的签名、降级、设备兼容或分包要求；扩展名为 APK 不代表任意文件都能成功安装。

## 重要边界

- 浏览器页面与信令是未加密 HTTP，媒体为 DTLS-SRTP；持有临时链接者可访问会话，信令无法抵御活跃局域网攻击者。无 STUN/TURN、公网穿透或多观看者，不需要关闭浏览器安全功能。

- APK 使用未加密 HTTP，仅限可信局域网，不能暴露公网。仍保留文件大小、并发、速率、超时、私有路径、哈希、解析及 FileProvider 限制。
- 本项目未实现 Miracast、Google Cast/Chromecast、Lelink、其他厂商系统投屏协议或 AirPlay 2 多房间；GameStream 不进入 Android 系统投屏列表。
- DRM 视频、禁止录屏或禁止音频捕获的应用可能黑屏或无声音。屏幕镜像有损，不宣称原画无损或假 4K。
- Root / Shizuku / 无障碍控制仍为可选功能，不会自动授权，不改路由器或系统安全设置。
- 编译、主机测试和模拟器测试不等于所有手机电视实机兼容。发布时须列出实际通过及未验证项目。

## 社区版升级与旧版迁移

旧 0.5.0 使用 `com.mooncast.unified.preview` 与一次性测试签名。原签名身份已不再可用，不能承诺用后续构建覆盖它。

自 0.5.1 起社区版采用固定包名 `com.mooncast.community` 与仓库公开的固定测试签名，后续递增 versionCode，以支持社区版之间正常覆盖升级。旧 0.5.0 到社区版需要一次迁移，不是每次更新都换包名或长期要求多版本共存。配对、权限与私有设置不能自动跨包搬运。

公开测试密钥不能证明安装包来自维护者：任何人都可用它签名。只从可信发布入口下载并核对发布校验和。未来商店版将使用独立包名与私有生产密钥，不与公开测试身份混用。

社区签名身份与构建失败保护见[签名说明](signing/community/README.md)。

## 语言设置

0.5.1 包含应用内语言入口；旧 0.5.0 不含此入口。历史 0.5.1 界面记录见下方；当前浏览器实测与限制见浏览器验证文档。

手机设置和电视高级设置提供「跟随系统」、English、简体中文。取消或选择当前语言不会停止会话；确认更换语言后才结束旧会话并重新打开当前角色。Android 13 及以上也支持系统应用语言选择。直接从系统设置切换时，活跃通知可能延后刷新，见[语言使用说明](docs/unified-guide.md)。

## 历史界面演示 · 0.5.1

> 此段仅记录 0.5.1，非当前浏览器版验收。角色菜单取消／同角色缺陷已在后续浏览器版修复。

![MoonCast 0.5.1 平板真机界面：切换语言、进入电视模式及打开设置](docs/assets/mooncast-0.5.1-xiaomi-pad8-ui.gif)

*小米平板 8 · Android 17 / API 37 · MoonCast 0.5.1-community。16 秒真实操作，保留原速，仅剪去等待时间。演示中文切英文、手机界面切电视角色，以及电视设置与焦点移动。电视界面运行在这台平板上，没有展示双端投屏或音频效果。*

7 次实测冷启动为 268–509 毫秒，未观察到 ANR。手机／电视角色中的跟随系统、英文、中文选择均在重开后保留；语言菜单取消／同选项、设置返回及方向焦点检查通过。**0.5.1 已知问题：设备角色菜单**取消或选择当前角色仍会重建手机界面或电视接收器，不能与已通过的语言菜单检查混为一谈。本次单设备测试未验证 AirPlay 音频、TCL 电视投屏、设备发现或 APK 推送。

## 构建与参与

需要 JDK 17、Android SDK 35、Build Tools 35.0.0 与 Gradle 8.9 / AGP 8.7.3。当前实现位于 `main`。

```sh
git clone --branch main https://github.com/meta-tabchen/MoonCast.git
cd MoonCast
python3 scripts/restore_release_inputs.py
python3 scripts/prepare_airplay.py --verify-sources
python3 scripts/prepare_airplay.py --verify-aar app/libs/airplaylib.aar
python3 scripts/prepare_unified_native.py
gradle :unified:assembleRelease :unified:lintRelease -PmooncastAbi=arm64-v8a
```

完整 Git checkout 的社区 release 构建使用用户一次初始化的固定公开测试身份。运行 `python3 scripts/verify_community_identity.py unified/build/outputs/apk/release/unified-release.apk` 校验；添加 `--previous /path/to/previous-community.apk` 可检查升级身份与递增 versionCode。缺失或被修改的签名输入会令构建失败，不会临时生成替代身份。独立私有身份实现前，production 构建明确禁用。Release 源码 ZIP 不含 MoonCast 应用的 PKCS12 签名文件；可显式加 `-PmooncastUnsignedVerification=true` 构建独立 `com.mooncast.community.verification` 无签名验证产物，不可覆盖社区版。原生库重建、依赖与源码打包见[构建指南](docs/unified-build.md)。

设备报告请附发送端／接收端型号、系统和应用版本、协议、编码／分辨率／帧率／码率、画面模式及复现步骤。将「能连接」「有画面」「有声音」「裁剪正确」分别描述，删除日志中的隐私及配对信息。[反馈问题](https://github.com/meta-tabchen/MoonCast/issues) · [参与贡献](CONTRIBUTING.md) · [安全说明](SECURITY.md)。

[历史 0.3.1 手机发送端 README](docs/historical-sender-0.3.1.zh-CN.md) 单独保留，不再折叠进当前项目介绍；内容仅适用于历史版本。

## 源码与致谢

手机 GameStream 主机基于 Mirror / Sunshine，AirPlay 发送采用固定版本 Go 发送库；接收端使用带补丁的 AndroidAirPlay / UxPlay、Media3 / DLNA 与 Moonlight core。两套 OpenSSL 通过不同库名与进程隔离，并非任意合并。MoonCast 是独立 GPLv3 项目，不是 Apple、Moonlight 或 Sunshine 的官方发行版。

项目按 GPLv3 提供源码与对应来源说明。浏览器功能使用开源 WebRTC SDK，固定外部源码、依赖定义、原始许可证与构建映射见[源码说明](docs/browser-release-source.md)；不把应用源码包称为完整离线 SDK 源码包。第三方许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，背景来源见 [主题说明](docs/tv-themes.md)。发布同时提供 APK、应用源码与 SDK 来源说明、许可证和 SHA256SUMS。
