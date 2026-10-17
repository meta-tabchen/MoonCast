# code61 与 Mirroring360：逐项审计及 0.7.4 改动

本表先描述已交付 **0.7.3/code61**，不把后续修改写成 code61 的行为。
code61 APK SHA-256：`ff3dc89cc2881309b396745f70399236b5353c798add42d4ebcdac76018eaccb`。
用户已确认该包在目标电视：无声、中央极小横向画面、播放一会退出。此真机失败优先于本机模拟通过。

## 证据边界

* code61 Java 源码已冻结到本 checkout 旁 `evidence-0.7.4-code62/code61-source/`；本表 Java 行号指冻结副本。
* code61 Go 源码已冻结到 `evidence-0.7.4-code62/code61-source/go/`，编译时原路径为 `/tmp/mooncast-airplay-074/source`；本表 `H`、`A`、`S` 分别指该树的 `internal/airplay/legacy_http.go`、`legacy_audio.go`、`airplaylib/airplaylib.go`。
* M360 证据为主任务提供的 SenderBasic/Mirroring360 静态复核位置。此 Mac 没有该 APK/ELF 完整分析产物及样本哈希，**未在本 Mac 独立重跑反编译**。给出的地址照实引用，缺失地址不编造。native 证据来自 x86_64，尚未证明目标手机 ARM ABI 分支相同。
* 所有“影响”均为待验证解释，不能据此宣称找到这台电视退出的唯一根因。没有复制第三方私钥或受保护素材。
* 本机唯一可授权 ADB 设备查到 community code55、非模拟器。未证明它与此次用户确认的 code61 手机为同一设备；未安装、停止或更改其应用，也未把它当作目标日志。

## code61 实际对照

| 项目 | M360 静态证据位置/行为 | code61 实际位置/行为 | 相同/不同及影响假设 | 已有测试与仍缺证据 |
|---|---|---|---|---|
| 发现服务、TXT、端口 | 音频由有效音频端口且 source 非0启用；发现/TXT完整函数位置及选择分支未提供 | `discovery.go:58` `_airplay._tcp`；A:25–64 单独浏览 `_raop._tcp`，只匹配所选IP，取SRV端口与`cn/et`；1.8秒窗口 | 不能声称发现策略一致。短窗、无广告或能力组合可能使音频在发包前就退出 | 只有模拟端点绕过发现的音频测试；缺目标TV `_raop` 发现结果/`cn/et`/实际端口 |
| GET stream.xml | `i.f` 接收原始接收尺寸（具体GET解析地址未提供） | H:93请求；plist整数解析与版本/16..8192边界校验；H:176保留原始`displayW/H` | 接收尺寸和编码约束是两个空间；必须看目标响应数值，不能用电视物理面板尺寸代替 | 伪造caps、拒绝HTML/重定向/认证失败已测；缺真实caps |
| 编码约束 | `i.f` 原始尺寸，采集约束另按用户偏好逐轴min | H:226 `legacySize` 仅按请求及1280x720限幅，正常非零请求时未限制到接收尺寸 | **明确不同**：低于720p的接收能力可能仍收到更大的编码流；不能把这点直接当退出根因 | code61 mock caps=1280x720，没覆盖更低接收上限；0.7.4增加640x360等上限用例 |
| POST stream及HTTP头 | POST后直接持有OutputStream发媒体，不持续等ACK；具体构造函数地址未提供 | H:244 Host、User-Agent AirPlay/150.33、X-Apple-Device-ID、Content-Length；H:372先250ms有界peek，再媒体直写；Watch单独读真实错误/EOF，无流阶段读超时 | 发媒体不依赖ACK基本一致；code61额外peek/读侧监测。读无数据不作断开 | 无HTTP响应升级、401、拒绝、EOF已测；缺真实POST字段/接收端回应 |
| POST bplist | 主任务复核：version字符串150.33，latencyMs整数50；fpsInfo八项；timestampInfo `AfPxt` | H:195 类型/值/数组基本一致；timestampInfo写`AfPxT`，大小写不同；随机会话/设备标识；可选FP param1/2 | **一项精确字符串不同**，属统计字段，尚无其导致退出证据；不能复制真实设备标识/密钥 | bplist类型及字段mock已测；0.7.4对齐`AfPxt`；缺真实接收响应 |
| 128头长度/类型/标记 | DEX `i.k @0x49194c`：每包清零128；type0/1 flags=6，type2 flags=30；payload size LE32、type LE16 | H:441栈上零头128、LE32长度/LE16类型/flags6或30 | **这些字段已相同**，不能再次把“没补flags”当根因 | 三分钟逐包校验；不是TV验收 |
| codec头16/20 | `i.k @0x49194c` type1写编码宽高float32LE | H:454–456 type1写SPS解析后的`videoW/H` | 格式相同；实际SPS值和实际MediaCodec格式尚无真机数据 | 三种H264/SPS尺寸及独立解码已测；缺目标编码器/SPS真实尺寸 |
| type0/1头40..60 | `i.k @0x49194c`，`i.a @0x491820`：40/44编码尺寸；48/52居中；56/60按接收尺寸fit，坐标单位为stream.xml尺寸单位，非0..1 | H:458–464、H:563同样用原始`displayW/H`与SPS尺寸计算并写入两个包种 | **公式和写入位置已相同**。不能把中央小图直接认定为这里公式错误；源内容可能早已在编码画布内缩小 | mock逐包字段与三尺寸解码通过；缺真实header数值和GPU裁剪/视口记录 |
| 心跳频率/内容/原子性 | `AppService$n.g @0x494b10` 初始0ms、每次完成后1000ms；`l @0x4944e8`零payload/零stamp、flush；`i.k` type2不写尺寸；同monitor写完整包 | H:577 NewTicker，第一次约1s；H:441 type2仅长度/类型/flags，其余0；与视频共m.mu，net.Conn直接写没有用户态待flush缓冲 | 字节相同；**首次调度和fixed-rate/fixed-delay不同**。不把M360发送端15秒watchdog等同TV超时 | code61三分钟179心跳；无真实最后写时间/对端收包证据；0.7.4加计数，不盲调间隔 |
| 视频/codec时间戳 | codec=`uptimeMillis`；视频=`MediaCodec.presentationTimeUs/1000`，不减首帧；位置为主任务DEX流头/编码链复核，具体方法地址未提供 | H:453每包`time.Since(m.started)`发送时刻；`AirPlayVideoEncoder:85`丢弃PTS，只传bytes/keyframe | **明确不同**：时间原点、捕获时刻和发送排队时间不同。内部自洽不证明TV接受 | code61只测相对时间单调；0.7.4传真实PTS、统一Android单调参考、记录PTS年龄；缺TV实际PTS/时钟响应 |
| UDP7010 / 48字节NTP | 0x24,1,2,0xe8；AIRP；请求40..47→响应24..31；收发uptime BE32.32；3秒接收超时继续 | H:598相同字段/复制位置、仅所选IP；时间为session-relative；没有ReadDeadline，等待直到socket关闭；WriteToUDP错误未反馈 | 字段相同，**时基不同**。阻塞接收不等于时钟线程已死；此前没收发计数不能判断 | 模拟连续48/32字节应答通过；缺目标TV真实7010请求与发送成功数；0.7.4记录绑定/收发/错误 |
| 实际采集/旋转 | 主任务DEX复核：内容尺寸变化停编码器再按CodecSize创建；格式改变先通知实际尺寸 | 冻结Service:94–98忽略wholeScreen回调；Encoder:55–59用getRealMetrics初始；VideoPipeline:124–136独立缩放texture/VD，固定编码画布；API34 app早到回调可被丢弃 | **明确不同/缺陷**。不同宽高比的捕获与Surface可产生一层黑边，GPU再fit又留一层；与极小中央画面一致，但不是照片即可确诊 | code61无Android实际回调测试；0.7.4保留早到回调、API34整屏和app都跟随实际区域；仍缺该手机回调值 |
| 自动裁剪 | M360具体crop选择分支未提供，不声称完全相同 | VideoPipeline:59仅sourceW>sourceH探测；render:195–196竖屏强制FULL；CropGeometry探测上下边各最多30% | **可复现缺口**：竖屏内横向电影有约40%上下黑边时“自动”不裁，画面可再次缩到约四分之一宽 | 0.7.4补专用于AirPlay自动视频模式的深水平黑边探测，整屏模式仍保留完整屏幕；合成几何可复现，仍缺实际手机图像/几何 |
| SPS/PPS/AVCC/首包 | 版本1、lengthSizeMinusOne3；SPS/PPS BE16，NAL BE32；format/config独立发，关键帧前可合成CSD | H:474 AnnexB解析、buildAVCCConfig；匹配IDR前发送type1；Java AirPlayH264合并SPS/PPS并在关键帧前补入 | 基本wire编码一致；是否与具体硬件输出相同仍未知，不能以20帧覆盖所有旋转 | 20帧及三分钟动态尺寸解码、split CSD/新会话隔离已测；缺目标硬编输出格式 |
| RAOP连接/认证/SDP | native `raopcl_connect VA0x1a04b0`：connect@1a06b6→announce@1a0756→create_udp@1a07c5→timing_thread@1a0811；含AppleLossless及AAC-eld串，选择分支未复原 | A:100–164只接受cn含1、et含0或4；OPTIONS；可选独立FP；SDP AppleLossless 352/16bit/2ch/44100；Digest iTunes、nonce计数独立 | **不能声称音频编码覆盖一致**；RSA-only/AAC-only或未发现会无声。严格不导入第三方私钥 | 614帧PCM→ALAC/RTP及Digest mock通过，不是TV声音成功；0.7.4暴露能力/阶段/HTTP状态或errno及PCM峰值 |
| RAOP SETUP Transport | x86_64/libraopplay.so `rtspcl_setup VA0x19dd90`：`RTP/AVP/UDP;unicast;mode=screen;interleaved=0-1;timing_port=%d;control_port=%d;redundant=2` | A:186：`mode=record;control_port=...;timing_port=...`，缺interleaved/redundant | **精确不同**。可能在SETUP失败或进入不同接收模式，目标TV实际失败阶段未知 | code61 mock只接受自己的record路径；0.7.4按已复核mirroring行为独立实现screen Transport并校验序列化字段 |
| RAOP事件连接 | native connect流程在setup后、record前可选event TCP；端口响应字段/触发条件未提供 | A:187–207只取Transport server_port/control_port及Session；不建立事件TCP | **缺失可选步骤**，不能直接假定目标TV要求它，也不能猜端口扫描连接 | 缺真实SETUP返回和M360事件分支解析证据；0.7.4诊断仍不得声称支持未实现事件流 |
| RECORD/音量/音频包 | native record@1a088f→volume@1a08b8；SETUP之后、事件连接之后；实际codec分支及RTP包抓取未提供 | A:213 RECORD Range/npt和seq1/rtptime2205；216音量0dB；PCM352样本ALAC/RTP96；7次sync、重传沿用audio.go | RECORD及音量**已实现**，不应误报遗漏。具体packet/latency/codec是否一致未证实 | synthetic逐样本通过；缺目标PCM是否非零、RTP是否实际发送、音频是否被播放；0.7.4补计数 |
| socket保活/超时 | 主任务DEX复核：connect/read15000ms、keepalive、TCP_NODELAY；POST后不靠读ACK；写/flush错误触发清理 | H:64 dial600ms，GET2s，POST250ms peek/实际回复2s，媒体write3s；首连接NoDelay/keepalive；音频dial800ms/请求2s/总5s；无流读超时 | **超时数值不同**，不为追求“对齐”直接改成15秒；HTTP初始重连后的socket选项也未统一配置。可能影响边缘网络，缺实际errno | code61阻塞写3s退出、EOF已测；0.7.4记录最后写成功/errno；不伪称恢复所有网络故障 |
| 断开、资源与UI | `l @0x4944e8`写/flush成功刷新发送端15000ms watchdog；异常停心跳、关stream/NTP、通知服务 | S:99去重失败回调并清状态；Service fail置connected=false、500ms核查；MainActivity保留失败；音频失败只通用日志且仅在高级设置可见 | 状态清理已实现；**失败来源/收发状态不充分且无复制入口**，看不到TV为何退；TCP写成功不证明画面被播放 | 模拟关闭/重连及状态单测通过，真实code61仍失败；0.7.4主卡片复制结构化诊断、进程退出后保留 |

## 0.7.4/code62 本次改进（与上表 code61 分开）

1. 保留真实captured-content回调，使用最大窗口初始尺寸，API34整屏与app都同步texture/VirtualDisplay；捕获变更不重用授权创建第二个VirtualDisplay。
2. AirPlay自动视频模式支持竖屏内横向视频的成对深水平黑边；整屏模式不裁。编码画布上限同时受原始接收尺寸约束，显示坐标空间保持原始caps。
3. 传递编码PTS，按Android单调参考统一媒体/NTP；心跳继续零时间戳，不更改心跳或超时参数。修正统计名字AfPxt。
4. 使用已复核的mirroring RAOP screen Transport，其他未复原codec/事件/加密分支不假装完成。
5. 本机应用主卡片“复制投屏诊断”：版本/code、源/texture/canvas/实际编码尺寸、crop/viewport、首个实际发出的配置包头40..60及最新显示矩形、PTS年龄、video/config/heartbeat和bytes、最后写时间、7010绑定/线程存活/错误及48/32字节收发、结构化失败来源/HTTP或errno、RAOP阶段/cn/et/端口、PCM帧/峰值及RTP包数。无IP/PIN/密钥/SDP或媒体内容，保存在app私有目录，用户自行复制，不上传。

Android尺寸修正依据：[MediaProjection.Callback 官方定义](https://developer.android.com/reference/android/media/projection/MediaProjection.Callback#onCapturedContentResize(int,%20int))。

## 尚需目标真机的最少信息

安装0.7.4后复现一次，无需ADB或开发者模式：电视退出后，回到手机AirPlay主卡片点“复制投屏诊断”，粘贴完整文本。保留一次开始至退出的大致秒数；“有没有声音”只记实际听到的结果。暂不要求额外设置切换、清数据或重启电视。
